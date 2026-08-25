package com.example.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.example.R
import com.example.model.HostSettings
import com.example.model.ResolutionOption
import com.example.model.ServerStats
import com.example.network.NetworkUtils
import com.example.server.LocalHttpServer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream

class ScreenHostService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    private var localHttpServer: LocalHttpServer? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var statsJob: Job? = null

    private var lastFrameTimestamp = 0L
    private var frameIntervalMs = 33L // Default 30 FPS
    private var frameCounter = 0
    private var lastFpsCalcTime = System.currentTimeMillis()
    private var currentFps = 0f
    private var serviceStartTime = 0L

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        if (action == ACTION_STOP) {
            stopHosting()
            stopSelf()
            return START_NOT_STICKY
        }

        if (action == ACTION_START) {
            startForegroundServiceWithNotification()

            val resultCode = projectionResultCode
            val resultData = projectionIntentData

            if (resultCode == Activity.RESULT_OK && resultData != null) {
                startHosting(resultCode, resultData)
            } else {
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun startForegroundServiceWithNotification() {
        val stopIntent = Intent(this, ScreenHostService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ScreenHost Active")
            .setContentText("Local screen mirroring is live.")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Hosting", stopPendingIntent)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun startHosting(resultCode: Int, data: Intent) {
        serviceStartTime = System.currentTimeMillis()
        val settings = currentSettings.value

        // Acquire WakeLock if requested
        if (settings.wakeLockEnabled) {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ScreenHost:ServerWakeLock")
            wakeLock?.acquire(4 * 60 * 60 * 1000L) // 4 hours max
        }

        // 1. Start Embedded HTTP & WebSocket Server
        localHttpServer = LocalHttpServer(
            context = applicationContext,
            hostSettingsProvider = { currentSettings.value },
            onClientCountChanged = { count ->
                _connectedClientsCount.value = count
            },
            onByteTransferred = { bytes ->
                _bytesSentCounter.value += bytes
            }
        )
        localHttpServer?.start(settings.port)

        // 2. Start Screen Capture Background Handler
        backgroundThread = HandlerThread("ScreenCaptureThread").apply { start() }
        backgroundHandler = Handler(backgroundThread!!.looper)

        // 3. Initialize MediaProjection
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, data)

        setupVirtualDisplay(settings)

        _isHostingActive.value = true
        startStatsTracker()
    }

    private fun setupVirtualDisplay(settings: HostSettings) {
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)

        val screenWidth = metrics.widthPixels
        val screenHeight = metrics.heightPixels
        val density = metrics.densityDpi

        val scale = settings.resolution.scale
        val captureWidth = ((screenWidth * scale).toInt() / 2) * 2
        val captureHeight = ((screenHeight * scale).toInt() / 2) * 2

        frameIntervalMs = (1000L / settings.targetFps.fps).coerceAtLeast(16L)

        imageReader = ImageReader.newInstance(captureWidth, captureHeight, PixelFormat.RGBA_8888, 2)
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenHostDisplay",
            captureWidth,
            captureHeight,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            backgroundHandler
        )

        imageReader?.setOnImageAvailableListener({ reader ->
            handleImageCaptured(reader, settings.jpegQuality)
        }, backgroundHandler)
    }

    private fun handleImageCaptured(reader: ImageReader, quality: Int) {
        var image: Image? = null
        try {
            image = reader.acquireLatestImage() ?: return
            val currentTime = System.currentTimeMillis()

            // Throttle according to Target FPS
            if (currentTime - lastFrameTimestamp < frameIntervalMs) {
                return
            }
            lastFrameTimestamp = currentTime

            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * image.width

            val bitmap = Bitmap.createBitmap(
                image.width + rowPadding / pixelStride,
                image.height,
                Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)

            val cleanBitmap = if (rowPadding == 0) {
                bitmap
            } else {
                val cropped = Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
                bitmap.recycle()
                cropped
            }

            val baos = ByteArrayOutputStream()
            cleanBitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
            val jpegBytes = baos.toByteArray()

            // Broadcast to connected web clients
            localHttpServer?.broadcastFrame(jpegBytes)

            // Update live preview bitmap thumbnail in app (scaled down)
            if (frameCounter % 5 == 0) {
                val thumb = Bitmap.createScaledBitmap(cleanBitmap, cleanBitmap.width / 3, cleanBitmap.height / 3, true)
                _latestPreviewBitmap.value = thumb
            }

            cleanBitmap.recycle()
            frameCounter++
        } catch (e: Exception) {
            // Ignore capture frame errors
        } finally {
            image?.close()
        }
    }

    private fun startStatsTracker() {
        statsJob = serviceScope.launch {
            while (isActive && _isHostingActive.value) {
                delay(1000)
                val now = System.currentTimeMillis()
                val elapsedSec = (now - lastFpsCalcTime) / 1000f
                if (elapsedSec > 0) {
                    currentFps = frameCounter / elapsedSec
                    frameCounter = 0
                    lastFpsCalcTime = now
                }

                val ip = NetworkUtils.getLocalIpAddress(applicationContext)
                val uptime = if (serviceStartTime > 0) (now - serviceStartTime) / 1000 else 0L

                _serverStats.value = ServerStats(
                    isRunning = true,
                    ipAddress = ip,
                    port = currentSettings.value.port,
                    currentFps = currentFps,
                    latencyMs = 12L,
                    bitrateKbps = (_bytesSentCounter.value * 8 / 1024) / (uptime.coerceAtLeast(1L)),
                    totalBytesTransferred = _bytesSentCounter.value,
                    connectedClientsCount = _connectedClientsCount.value,
                    uptimeSeconds = uptime
                )
            }
        }
    }

    private fun stopHosting() {
        statsJob?.cancel()
        statsJob = null

        try {
            virtualDisplay?.release()
        } catch (e: Exception) {}
        virtualDisplay = null

        try {
            imageReader?.close()
        } catch (e: Exception) {}
        imageReader = null

        try {
            mediaProjection?.stop()
        } catch (e: Exception) {}
        mediaProjection = null

        backgroundThread?.quitSafely()
        backgroundThread = null
        backgroundHandler = null

        localHttpServer?.stop()
        localHttpServer = null

        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (e: Exception) {}

        _isHostingActive.value = false
        _serverStats.value = ServerStats(isRunning = false)
        _connectedClientsCount.value = 0
    }

    override fun onDestroy() {
        stopHosting()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "ScreenHost Mirroring Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Foreground screen casting and local web server notification"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "screenhost_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.example.action.START_HOSTING"
        const val ACTION_STOP = "com.example.action.STOP_HOSTING"

        var projectionResultCode: Int = Activity.RESULT_CANCELED
        var projectionIntentData: Intent? = null

        val currentSettings = MutableStateFlow(HostSettings())

        private val _isHostingActive = MutableStateFlow(false)
        val isHostingActive: StateFlow<Boolean> = _isHostingActive.asStateFlow()

        private val _connectedClientsCount = MutableStateFlow(0)
        val connectedClientsCount: StateFlow<Int> = _connectedClientsCount.asStateFlow()

        private val _bytesSentCounter = MutableStateFlow(0L)

        private val _latestPreviewBitmap = MutableStateFlow<Bitmap?>(null)
        val latestPreviewBitmap: StateFlow<Bitmap?> = _latestPreviewBitmap.asStateFlow()

        private val _serverStats = MutableStateFlow(ServerStats())
        val serverStats: StateFlow<ServerStats> = _serverStats.asStateFlow()

        fun startService(context: Context, resultCode: Int, data: Intent) {
            projectionResultCode = resultCode
            projectionIntentData = data
            val intent = Intent(context, ScreenHostService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, ScreenHostService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
