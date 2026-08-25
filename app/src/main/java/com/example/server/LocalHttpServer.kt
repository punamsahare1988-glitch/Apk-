package com.example.server

import android.content.Context
import android.util.Base64
import com.example.model.ConnectedClient
import com.example.model.HostSettings
import com.example.model.RemoteInputEvent
import com.example.service.ScreenHostAccessibilityService
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.*
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

class LocalHttpServer(
    private val context: Context,
    private val hostSettingsProvider: () -> HostSettings,
    private val onClientCountChanged: (Int) -> Unit,
    private val onByteTransferred: (Long) -> Unit
) {
    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private val serverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val authenticatedTokens = ConcurrentHashMap<String, Long>()
    private val activeClients = ConcurrentHashMap<String, ConnectedClient>()
    private val activeWsConnections = CopyOnWriteArrayList<WebSocketConnection>()
    private val activeMjpegStreams = CopyOnWriteArrayList<MjpegStreamHandler>()

    val totalTransferredBytes = AtomicLong(0L)

    fun start(port: Int) {
        if (isRunning) return
        isRunning = true

        serverScope.launch {
            try {
                serverSocket = ServerSocket(port)
                while (isRunning && !serverSocket!!.isClosed) {
                    val socket = try {
                        serverSocket!!.accept()
                    } catch (e: Exception) {
                        break
                    }
                    launch {
                        handleClientSocket(socket)
                    }
                }
            } catch (e: Exception) {
                // Server socket error or stop
            } finally {
                stop()
            }
        }
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {}
        serverSocket = null

        activeWsConnections.forEach { it.close() }
        activeWsConnections.clear()

        activeMjpegStreams.forEach { it.close() }
        activeMjpegStreams.clear()

        activeClients.clear()
        onClientCountChanged(0)
    }

    fun broadcastFrame(jpegBytes: ByteArray) {
        if (!isRunning || jpegBytes.isEmpty()) return

        // 1. Broadcast to WebSocket clients
        val wsList = activeWsConnections.toList()
        for (ws in wsList) {
            if (ws.isAuthenticated) {
                ws.sendBinary(jpegBytes)
            }
        }

        // 2. Broadcast to MJPEG streams
        val mjpegList = activeMjpegStreams.toList()
        for (mjpeg in mjpegList) {
            mjpeg.sendFrame(jpegBytes)
        }

        val totalBytes = (jpegBytes.size * (wsList.size + mjpegList.size)).toLong()
        totalTransferredBytes.addAndGet(totalBytes)
        onByteTransferred(totalBytes)
    }

    private suspend fun handleClientSocket(socket: Socket) {
        try {
            socket.tcpNoDelay = true
            val inputStream = BufferedInputStream(socket.getInputStream())
            val outputStream = BufferedOutputStream(socket.getOutputStream())

            val reader = BufferedReader(InputStreamReader(inputStream))
            val firstLine = reader.readLine() ?: return
            val parts = firstLine.split(" ")
            if (parts.size < 2) return

            val method = parts[0]
            val fullPath = parts[1]
            val path = fullPath.substringBefore("?")
            val queryParams = parseQueryParams(fullPath)

            val headers = mutableMapOf<String, String>()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                if (line.isNullOrEmpty()) break
                val colonIdx = line!!.indexOf(":")
                if (colonIdx > 0) {
                    val key = line!!.substring(0, colonIdx).trim().lowercase()
                    val value = line!!.substring(colonIdx + 1).trim()
                    headers[key] = value
                }
            }

            val clientIp = socket.inetAddress.hostAddress ?: "Unknown"
            val userAgent = headers["user-agent"] ?: "Browser"

            // Check if this is a WebSocket Upgrade Request
            if (headers["upgrade"]?.equals("websocket", ignoreCase = true) == true) {
                handleWebSocketUpgrade(socket, inputStream, outputStream, headers, queryParams, clientIp, userAgent)
                return
            }

            // Standard HTTP routing
            when {
                path == "/" || path == "/index.html" -> {
                    val html = WebClientAssets.getIndexHtml("ScreenHost")
                    sendHttpResponse(outputStream, 200, "text/html; charset=UTF-8", html.toByteArray())
                }
                path == "/download" || path == "/app" -> {
                    val settings = hostSettingsProvider()
                    val hostUrl = "http://$clientIp:${settings.port}/"
                    val html = WebClientAssets.getDownloadPageHtml("ScreenHost", hostUrl, settings.pinCode)
                    sendHttpResponse(outputStream, 200, "text/html; charset=UTF-8", html.toByteArray())
                }
                path == "/ScreenHost.apk" || path == "/download/ScreenHost.apk" || path == "/apk" || path == "/app.apk" || path == "/download-apk" -> {
                    val apkPath = context.applicationInfo.sourceDir
                    val apkFile = java.io.File(apkPath)
                    if (apkFile.exists() && apkFile.canRead()) {
                        val apkBytes = apkFile.readBytes()
                        val headerStr = "HTTP/1.1 200 OK\r\n" +
                                "Content-Type: application/vnd.android.package-archive\r\n" +
                                "Content-Length: ${apkBytes.size}\r\n" +
                                "Content-Disposition: attachment; filename=\"ScreenHost.apk\"\r\n" +
                                "Access-Control-Allow-Origin: *\r\n" +
                                "Connection: close\r\n\r\n"
                        outputStream.write(headerStr.toByteArray(Charsets.UTF_8))
                        outputStream.write(apkBytes)
                        outputStream.flush()
                    } else {
                        sendHttpResponse(outputStream, 404, "text/plain", "APK file not accessible on host".toByteArray())
                    }
                }
                path == "/stream" -> {
                    handleMjpegStream(socket, outputStream, clientIp, userAgent)
                    return
                }
                path == "/api/status" -> {
                    val settings = hostSettingsProvider()
                    val json = JSONObject().apply {
                        put("status", "running")
                        put("port", settings.port)
                        put("pinRequired", settings.pinAuthEnabled)
                        put("resolution", settings.resolution.title)
                        put("targetFps", settings.targetFps.fps)
                        put("clientsCount", activeClients.size)
                    }
                    sendHttpResponse(outputStream, 200, "application/json", json.toString().toByteArray())
                }
                path == "/api/auth" && method == "POST" -> {
                    val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
                    val body = readBody(reader, contentLength)
                    val json = try { JSONObject(body) } catch (e: Exception) { JSONObject() }
                    val pin = json.optString("pin", "")
                    val settings = hostSettingsProvider()

                    if (!settings.pinAuthEnabled || pin == settings.pinCode) {
                        val token = UUID.randomUUID().toString()
                        authenticatedTokens[token] = System.currentTimeMillis()
                        val resp = JSONObject().apply {
                            put("success", true)
                            put("token", token)
                        }
                        sendHttpResponse(outputStream, 200, "application/json", resp.toString().toByteArray())
                    } else {
                        val resp = JSONObject().apply {
                            put("success", false)
                            put("error", "Invalid PIN")
                        }
                        sendHttpResponse(outputStream, 401, "application/json", resp.toString().toByteArray())
                    }
                }
                path == "/api/input" && method == "POST" -> {
                    val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
                    val body = readBody(reader, contentLength)
                    val event = parseRemoteEvent(body)
                    if (event != null) {
                        ScreenHostAccessibilityService.handleRemoteInput(context, event)
                    }
                    sendHttpResponse(outputStream, 200, "application/json", "{\"status\":\"ok\"}".toByteArray())
                }
                else -> {
                    sendHttpResponse(outputStream, 404, "text/plain", "404 Not Found".toByteArray())
                }
            }

            try {
                socket.close()
            } catch (e: Exception) {}
        } catch (e: Exception) {
            try { socket.close() } catch (ex: Exception) {}
        }
    }

    private fun handleWebSocketUpgrade(
        socket: Socket,
        inputStream: InputStream,
        outputStream: OutputStream,
        headers: Map<String, String>,
        queryParams: Map<String, String>,
        clientIp: String,
        userAgent: String
    ) {
        val secKey = headers["sec-websocket-key"] ?: return
        val acceptKey = generateWebSocketAccept(secKey)

        val response = "HTTP/1.1 101 Switching Protocols\r\n" +
                "Upgrade: websocket\r\n" +
                "Connection: Upgrade\r\n" +
                "Sec-WebSocket-Accept: $acceptKey\r\n\r\n"

        outputStream.write(response.toByteArray(Charsets.UTF_8))
        outputStream.flush()

        val settings = hostSettingsProvider()
        val token = queryParams["token"]
        var isAuth = !settings.pinAuthEnabled || (token != null && authenticatedTokens.containsKey(token))

        val clientId = UUID.randomUUID().toString()
        val client = ConnectedClient(
            id = clientId,
            ipAddress = clientIp,
            userAgent = userAgent,
            isAuthenticated = isAuth
        )
        activeClients[clientId] = client
        onClientCountChanged(activeClients.size)

        val wsConn = WebSocketConnection(
            clientId = clientId,
            socket = socket,
            inputStream = inputStream,
            outputStream = outputStream,
            isAuthenticated = isAuth,
            onMessage = { msg ->
                handleWsTextMessage(clientId, msg)
            },
            onClose = {
                activeClients.remove(clientId)
                onClientCountChanged(activeClients.size)
            }
        )

        activeWsConnections.add(wsConn)
        if (!isAuth && settings.pinAuthEnabled) {
            wsConn.sendText("{\"type\":\"auth_required\"}")
        }

        wsConn.startListening()
    }

    private fun handleWsTextMessage(clientId: String, messageText: String) {
        try {
            val json = JSONObject(messageText)
            val type = json.optString("type", "")
            val ws = activeWsConnections.firstOrNull { it.clientId == clientId } ?: return
            val settings = hostSettingsProvider()

            if (type == "auth") {
                val pin = json.optString("pin", "")
                if (!settings.pinAuthEnabled || pin == settings.pinCode) {
                    val token = UUID.randomUUID().toString()
                    authenticatedTokens[token] = System.currentTimeMillis()
                    ws.isAuthenticated = true
                    val client = activeClients[clientId]
                    client?.isAuthenticated = true
                    ws.sendText("{\"type\":\"auth_success\",\"token\":\"$token\"}")
                } else {
                    ws.sendText("{\"type\":\"auth_failed\"}")
                }
                return
            }

            if (type == "ping") {
                val timestamp = json.optDouble("timestamp", 0.0)
                ws.sendText("{\"type\":\"pong\",\"timestamp\":$timestamp}")
                return
            }

            // Remote control events (only if authenticated and control enabled)
            if (ws.isAuthenticated && settings.remoteControlEnabled) {
                val event = parseRemoteEvent(messageText)
                if (event != null) {
                    ScreenHostAccessibilityService.handleRemoteInput(context, event)
                }
            }
        } catch (e: Exception) {}
    }

    private fun handleMjpegStream(socket: Socket, outputStream: OutputStream, clientIp: String, userAgent: String) {
        val boundary = "frameboundary"
        val header = "HTTP/1.1 200 OK\r\n" +
                "Connection: close\r\n" +
                "Max-Age: 0\r\n" +
                "Expires: 0\r\n" +
                "Cache-Control: no-cache, private\r\n" +
                "Pragma: no-cache\r\n" +
                "Content-Type: multipart/x-mixed-replace; boundary=--$boundary\r\n\r\n"

        outputStream.write(header.toByteArray(Charsets.UTF_8))
        outputStream.flush()

        val clientId = UUID.randomUUID().toString()
        val client = ConnectedClient(id = clientId, ipAddress = clientIp, userAgent = userAgent)
        activeClients[clientId] = client
        onClientCountChanged(activeClients.size)

        val mjpegHandler = MjpegStreamHandler(socket, outputStream, boundary) {
            activeClients.remove(clientId)
            onClientCountChanged(activeClients.size)
        }
        activeMjpegStreams.add(mjpegHandler)
    }

    private fun parseRemoteEvent(jsonStr: String): RemoteInputEvent? {
        return try {
            val json = JSONObject(jsonStr)
            RemoteInputEvent(
                type = json.optString("type", ""),
                x = json.optDouble("x", 0.0).toFloat(),
                y = json.optDouble("y", 0.0).toFloat(),
                startX = json.optDouble("startX", 0.0).toFloat(),
                startY = json.optDouble("startY", 0.0).toFloat(),
                endX = json.optDouble("endX", 0.0).toFloat(),
                endY = json.optDouble("endY", 0.0).toFloat(),
                durationMs = json.optLong("durationMs", 200L),
                action = json.optString("action", ""),
                text = json.optString("text", ""),
                keyCode = json.optString("keyCode", "")
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun sendHttpResponse(out: OutputStream, code: Int, contentType: String, body: ByteArray) {
        val statusText = if (code == 200) "OK" else if (code == 401) "Unauthorized" else "Not Found"
        val response = "HTTP/1.1 $code $statusText\r\n" +
                "Content-Type: $contentType\r\n" +
                "Content-Length: ${body.size}\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Connection: close\r\n\r\n"
        out.write(response.toByteArray(Charsets.UTF_8))
        out.write(body)
        out.flush()
    }

    private fun readBody(reader: BufferedReader, length: Int): String {
        if (length <= 0) return ""
        val charArray = CharArray(length)
        var totalRead = 0
        while (totalRead < length) {
            val read = reader.read(charArray, totalRead, length - totalRead)
            if (read == -1) break
            totalRead += read
        }
        return String(charArray, 0, totalRead)
    }

    private fun parseQueryParams(url: String): Map<String, String> {
        val query = url.substringAfter("?", "")
        if (query.isEmpty()) return emptyMap()
        val result = mutableMapOf<String, String>()
        for (param in query.split("&")) {
            val pair = param.split("=")
            if (pair.size == 2) {
                result[pair[0]] = java.net.URLDecoder.decode(pair[1], "UTF-8")
            }
        }
        return result
    }

    private fun generateWebSocketAccept(key: String): String {
        val magic = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
        val sha1 = MessageDigest.getInstance("SHA-1").digest((key + magic).toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(sha1, Base64.NO_WRAP)
    }

    inner class WebSocketConnection(
        val clientId: String,
        val socket: Socket,
        val inputStream: InputStream,
        val outputStream: OutputStream,
        var isAuthenticated: Boolean,
        val onMessage: (String) -> Unit,
        val onClose: () -> Unit
    ) {
        private var isOpen = true
        private val writeLock = Any()

        fun startListening() {
            serverScope.launch(Dispatchers.IO) {
                try {
                    while (isOpen && !socket.isClosed) {
                        val firstByte = inputStream.read()
                        if (firstByte == -1) break
                        val opcode = firstByte and 0x0F
                        if (opcode == 8) break // Close frame

                        val secondByte = inputStream.read()
                        if (secondByte == -1) break
                        val isMasked = (secondByte and 0x80) != 0
                        var payloadLen = (secondByte and 0x7F).toLong()

                        if (payloadLen == 126L) {
                            val b1 = inputStream.read()
                            val b2 = inputStream.read()
                            payloadLen = ((b1 and 0xFF) shl 8 or (b2 and 0xFF)).toLong()
                        } else if (payloadLen == 127L) {
                            var len = 0L
                            for (i in 0..7) {
                                len = (len shl 8) or (inputStream.read().toLong() and 0xFF)
                            }
                            payloadLen = len
                        }

                        val maskingKey = ByteArray(4)
                        if (isMasked) {
                            var readM = 0
                            while (readM < 4) {
                                val r = inputStream.read(maskingKey, readM, 4 - readM)
                                if (r == -1) break
                                readM += r
                            }
                        }

                        val payload = ByteArray(payloadLen.toInt())
                        var readP = 0
                        while (readP < payload.size) {
                            val r = inputStream.read(payload, readP, payload.size - readP)
                            if (r == -1) break
                            readP += r
                        }

                        if (isMasked) {
                            for (i in payload.indices) {
                                payload[i] = (payload[i].toInt() xor maskingKey[i % 4].toInt()).toByte()
                            }
                        }

                        if (opcode == 1) { // Text message
                            val text = String(payload, Charsets.UTF_8)
                            onMessage(text)
                        }
                    }
                } catch (e: Exception) {}
                finally {
                    close()
                }
            }
        }

        fun sendText(text: String) {
            if (!isOpen) return
            val bytes = text.toByteArray(Charsets.UTF_8)
            sendFrame(0x01, bytes)
        }

        fun sendBinary(data: ByteArray) {
            if (!isOpen) return
            sendFrame(0x02, data)
        }

        private fun sendFrame(opcode: Int, data: ByteArray) {
            synchronized(writeLock) {
                try {
                    val out = outputStream
                    out.write(0x80 or opcode)
                    val len = data.size
                    if (len <= 125) {
                        out.write(len)
                    } else if (len <= 65535) {
                        out.write(126)
                        out.write((len shr 8) and 0xFF)
                        out.write(len and 0xFF)
                    } else {
                        out.write(127)
                        for (i in 7 downTo 0) {
                            out.write((len.toLong() shr (i * 8) and 0xFF).toInt())
                        }
                    }
                    out.write(data)
                    out.flush()
                } catch (e: Exception) {
                    close()
                }
            }
        }

        fun close() {
            if (!isOpen) return
            isOpen = false
            try { socket.close() } catch (e: Exception) {}
            activeWsConnections.remove(this)
            onClose()
        }
    }

    inner class MjpegStreamHandler(
        val socket: Socket,
        val outputStream: OutputStream,
        val boundary: String,
        val onClose: () -> Unit
    ) {
        private var isOpen = true
        private val writeLock = Any()

        fun sendFrame(jpegBytes: ByteArray) {
            if (!isOpen) return
            synchronized(writeLock) {
                try {
                    val frameHeader = "--$boundary\r\n" +
                            "Content-Type: image/jpeg\r\n" +
                            "Content-Length: ${jpegBytes.size}\r\n\r\n"
                    outputStream.write(frameHeader.toByteArray(Charsets.UTF_8))
                    outputStream.write(jpegBytes)
                    outputStream.write("\r\n".toByteArray(Charsets.UTF_8))
                    outputStream.flush()
                } catch (e: Exception) {
                    close()
                }
            }
        }

        fun close() {
            if (!isOpen) return
            isOpen = false
            try { socket.close() } catch (e: Exception) {}
            activeMjpegStreams.remove(this)
            onClose()
        }
    }
}
