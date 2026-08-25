package com.example.ui.viewmodel

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.*
import com.example.network.NetworkUtils
import com.example.qr.QrCodeGenerator
import com.example.service.ScreenHostAccessibilityService
import com.example.service.ScreenHostService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

class ScreenHostViewModel(application: Application) : AndroidViewModel(application) {

    val isHosting: StateFlow<Boolean> = ScreenHostService.isHostingActive
    val serverStats: StateFlow<ServerStats> = ScreenHostService.serverStats
    val previewBitmap: StateFlow<Bitmap?> = ScreenHostService.latestPreviewBitmap
    val isAccessibilityActive: StateFlow<Boolean> = ScreenHostAccessibilityService.isServiceActive

    private val _hostSettings = MutableStateFlow(
        HostSettings(
            port = 8080,
            pinAuthEnabled = true,
            pinCode = NetworkUtils.generateRandomPin(4),
            resolution = ResolutionOption.HD_720P,
            targetFps = FpsOption.FPS_30,
            jpegQuality = 75,
            audioStreamingEnabled = false,
            remoteControlEnabled = true,
            wakeLockEnabled = true
        )
    )
    val hostSettings: StateFlow<HostSettings> = _hostSettings.asStateFlow()

    private val _keybinds = MutableStateFlow(defaultKeybinds())
    val keybinds: StateFlow<List<KeybindMapping>> = _keybinds.asStateFlow()

    private val _downloadCode = MutableStateFlow("SH-" + NetworkUtils.generateRandomPin(4))
    val downloadCode: StateFlow<String> = _downloadCode.asStateFlow()

    private val _qrBitmap = MutableStateFlow<Bitmap?>(null)
    val qrBitmap: StateFlow<Bitmap?> = _qrBitmap.asStateFlow()

    private val _showQrDialog = MutableStateFlow(false)
    val showQrDialog: StateFlow<Boolean> = _showQrDialog.asStateFlow()

    private val _showDownloadDialog = MutableStateFlow(false)
    val showDownloadDialog: StateFlow<Boolean> = _showDownloadDialog.asStateFlow()

    private val _showKeybindDialog = MutableStateFlow(false)
    val showKeybindDialog: StateFlow<Boolean> = _showKeybindDialog.asStateFlow()

    init {
        // Sync settings to service
        viewModelScope.launch {
            _hostSettings.collect { settings ->
                ScreenHostService.currentSettings.value = settings
                updateQrCode()
            }
        }
    }

    fun getHostUrl(): String {
        val ip = NetworkUtils.getLocalIpAddress(getApplication())
        val port = _hostSettings.value.port
        return "http://$ip:$port"
    }

    fun getDownloadUrl(): String {
        val ip = NetworkUtils.getLocalIpAddress(getApplication())
        val port = _hostSettings.value.port
        return "http://$ip:$port/download"
    }

    fun getApkDownloadUrl(): String {
        val ip = NetworkUtils.getLocalIpAddress(getApplication())
        val port = _hostSettings.value.port
        return "http://$ip:$port/ScreenHost.apk"
    }

    fun exportOrShareApk(context: Context) {
        try {
            val appInfo = context.applicationInfo
            val sourceApk = java.io.File(appInfo.sourceDir)
            if (!sourceApk.exists()) {
                android.widget.Toast.makeText(context, "APK file not found on device", android.widget.Toast.LENGTH_SHORT).show()
                return
            }
            val cacheApk = java.io.File(context.cacheDir, "ScreenHost.apk")
            sourceApk.copyTo(cacheApk, overwrite = true)
            val apkUri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                cacheApk
            )
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "application/vnd.android.package-archive"
                putExtra(android.content.Intent.EXTRA_STREAM, apkUri)
                putExtra(android.content.Intent.EXTRA_SUBJECT, "ScreenHost Full App APK")
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(android.content.Intent.createChooser(intent, "Save or Send ScreenHost APK"))
        } catch (e: Exception) {
            android.widget.Toast.makeText(context, "Export error: ${e.localizedMessage}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun updateQrCode() {
        viewModelScope.launch(Dispatchers.Default) {
            val url = getHostUrl()
            val bitmap = QrCodeGenerator.createQrBitmap(url, 400)
            _qrBitmap.value = bitmap
        }
    }

    fun setPort(port: Int) {
        _hostSettings.update { it.copy(port = port.coerceIn(1024, 65535)) }
    }

    fun setPinAuthEnabled(enabled: Boolean) {
        _hostSettings.update { it.copy(pinAuthEnabled = enabled) }
    }

    fun setPinCode(pin: String) {
        _hostSettings.update { it.copy(pinCode = pin) }
    }

    fun generateNewPin() {
        val newPin = NetworkUtils.generateRandomPin(4)
        _hostSettings.update { it.copy(pinCode = newPin) }
    }

    fun setResolution(res: ResolutionOption) {
        _hostSettings.update { it.copy(resolution = res) }
    }

    fun setTargetFps(fps: FpsOption) {
        _hostSettings.update { it.copy(targetFps = fps) }
    }

    fun setJpegQuality(quality: Int) {
        _hostSettings.update { it.copy(jpegQuality = quality.coerceIn(30, 95)) }
    }

    fun setAudioStreaming(enabled: Boolean) {
        _hostSettings.update { it.copy(audioStreamingEnabled = enabled) }
    }

    fun setRemoteControl(enabled: Boolean) {
        _hostSettings.update { it.copy(remoteControlEnabled = enabled) }
    }

    fun setWakeLock(enabled: Boolean) {
        _hostSettings.update { it.copy(wakeLockEnabled = enabled) }
    }

    fun setShowQrDialog(show: Boolean) {
        _showQrDialog.value = show
        if (show) updateQrCode()
    }

    fun setShowDownloadDialog(show: Boolean) {
        _showDownloadDialog.value = show
    }

    fun setShowKeybindDialog(show: Boolean) {
        _showKeybindDialog.value = show
    }

    fun addOrUpdateKeybind(keybind: KeybindMapping) {
        _keybinds.update { list ->
            val existing = list.indexOfFirst { it.id == keybind.id }
            if (existing >= 0) {
                list.toMutableList().apply { set(existing, keybind) }
            } else {
                list + keybind
            }
        }
    }

    fun removeKeybind(id: String) {
        _keybinds.update { list -> list.filterNot { it.id == id } }
    }

    fun resetKeybindsToDefault() {
        _keybinds.value = defaultKeybinds()
    }

    fun copyToClipboard(text: String) {
        val clipboard = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText("ScreenHost URL", text)
        clipboard?.setPrimaryClip(clip)
    }

    private fun defaultKeybinds(): List<KeybindMapping> {
        return listOf(
            KeybindMapping(UUID.randomUUID().toString(), "Escape / Backspace", "Escape", KeyActionType.BACK),
            KeybindMapping(UUID.randomUUID().toString(), "Home / Windows", "Home", KeyActionType.HOME),
            KeybindMapping(UUID.randomUUID().toString(), "Tab", "Tab", KeyActionType.RECENTS),
            KeybindMapping(UUID.randomUUID().toString(), "F1", "F1", KeyActionType.VOLUME_DOWN),
            KeybindMapping(UUID.randomUUID().toString(), "F2", "F2", KeyActionType.VOLUME_UP),
            KeybindMapping(UUID.randomUUID().toString(), "F8", "F8", KeyActionType.POWER),
            KeybindMapping(UUID.randomUUID().toString(), "F9", "F9", KeyActionType.NOTIFICATIONS),
            KeybindMapping(UUID.randomUUID().toString(), "F10", "F10", KeyActionType.QUICK_SETTINGS),
            KeybindMapping(UUID.randomUUID().toString(), "F12", "F12", KeyActionType.SCREENSHOT)
        )
    }
}
