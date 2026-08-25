package com.example.model

enum class ResolutionOption(val title: String, val scale: Float, val description: String) {
    FULL_HD("1080p", 1.0f, "Crisp & High Detail"),
    HD_720P("720p", 0.67f, "Balanced Performance"),
    SD_540P("540p", 0.50f, "Low Latency (Fast)"),
    LOW_360P("360p", 0.33f, "Ultra Low Bandwidth")
}

enum class FpsOption(val fps: Int, val label: String) {
    FPS_60(60, "60 FPS (Ultra Smooth)"),
    FPS_30(30, "30 FPS (Standard)"),
    FPS_15(15, "15 FPS (Battery Saver)")
}

enum class KeyActionType(val displayName: String, val description: String) {
    BACK("Back", "Android Back Navigation"),
    HOME("Home", "Go to Home Screen"),
    RECENTS("Recent Apps", "Open App Switcher"),
    VOLUME_UP("Volume +", "Increase Volume"),
    VOLUME_DOWN("Volume -", "Decrease Volume"),
    POWER("Power / Lock", "Lock or Open Power Menu"),
    NOTIFICATIONS("Notifications", "Pull Down Notification Shade"),
    QUICK_SETTINGS("Quick Settings", "Open Quick Settings Panel"),
    SCREENSHOT("Screenshot", "Take Screen Capture")
}

data class KeybindMapping(
    val id: String,
    val keyName: String,
    val keyCode: String,
    val action: KeyActionType
)

data class ConnectedClient(
    val id: String,
    val ipAddress: String,
    val userAgent: String,
    val connectedAt: Long = System.currentTimeMillis(),
    var lastPingMs: Long = 0L,
    var isAuthenticated: Boolean = true
)

data class HostSettings(
    val port: Int = 8080,
    val pinAuthEnabled: Boolean = true,
    val pinCode: String = "8899",
    val resolution: ResolutionOption = ResolutionOption.HD_720P,
    val targetFps: FpsOption = FpsOption.FPS_30,
    val jpegQuality: Int = 75,
    val audioStreamingEnabled: Boolean = false,
    val remoteControlEnabled: Boolean = true,
    val wakeLockEnabled: Boolean = true
)

data class ServerStats(
    val isRunning: Boolean = false,
    val ipAddress: String = "127.0.0.1",
    val port: Int = 8080,
    val currentFps: Float = 0f,
    val latencyMs: Long = 0L,
    val bitrateKbps: Long = 0L,
    val totalBytesTransferred: Long = 0L,
    val connectedClientsCount: Int = 0,
    val uptimeSeconds: Long = 0L
)

data class RemoteInputEvent(
    val type: String, // "down", "up", "move", "click", "swipe", "key", "text", "action"
    val x: Float = 0f,
    val y: Float = 0f,
    val startX: Float = 0f,
    val startY: Float = 0f,
    val endX: Float = 0f,
    val endY: Float = 0f,
    val durationMs: Long = 200L,
    val action: String = "",
    val text: String = "",
    val keyCode: String = ""
)
