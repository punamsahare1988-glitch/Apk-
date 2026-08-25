package com.example

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.HostSettings
import com.example.model.ServerStats
import com.example.service.ScreenHostService
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.ScreenHostViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: ScreenHostViewModel by viewModels()

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            ScreenHostService.startService(this, result.resultCode, result.data!!)
        } else {
            Toast.makeText(this, "Screen capture permission is required to host screen", Toast.LENGTH_SHORT).show()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Continue
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            ScreenHostTheme {
                ScreenHostMainScreen(
                    viewModel = viewModel,
                    onRequestStartHosting = {
                        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                        mediaProjectionLauncher.launch(projectionManager.createScreenCaptureIntent())
                    },
                    onRequestStopHosting = {
                        ScreenHostService.stopService(this)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenHostMainScreen(
    viewModel: ScreenHostViewModel,
    onRequestStartHosting: () -> Unit,
    onRequestStopHosting: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val isHosting by viewModel.isHosting.collectAsStateWithLifecycle()
    val serverStats by viewModel.serverStats.collectAsStateWithLifecycle()
    val hostSettings by viewModel.hostSettings.collectAsStateWithLifecycle()
    val previewBitmap by viewModel.previewBitmap.collectAsStateWithLifecycle()
    val isAccessibilityActive by viewModel.isAccessibilityActive.collectAsStateWithLifecycle()
    val keybinds by viewModel.keybinds.collectAsStateWithLifecycle()
    val downloadCode by viewModel.downloadCode.collectAsStateWithLifecycle()
    val qrBitmap by viewModel.qrBitmap.collectAsStateWithLifecycle()

    val showQrDialog by viewModel.showQrDialog.collectAsStateWithLifecycle()
    val showDownloadDialog by viewModel.showDownloadDialog.collectAsStateWithLifecycle()

    val hostUrl = viewModel.getHostUrl()
    val downloadUrl = viewModel.getDownloadUrl()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = DarkBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    Brush.linearGradient(listOf(ElectricBlue, CyberCyan))
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cast,
                                contentDescription = null,
                                tint = Color(0xFF041E2D),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Column {
                            Text(
                                text = "ScreenHost",
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = TextPrimary
                            )
                            Text(
                                text = "Local Wi-Fi PC Mirror & Control",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.setShowDownloadDialog(true)
                        },
                        modifier = Modifier.testTag("download_apk_top_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Download APK",
                            tint = SkyGlow
                        )
                    }

                    IconButton(
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "ScreenHost Mirror Link")
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    "Watch and control my screen locally at: $hostUrl (PIN: ${hostSettings.pinCode})"
                                )
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Host Link"))
                        },
                        modifier = Modifier.testTag("share_host_link_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            tint = CyberCyan
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkSurface,
                    titleContentColor = TextPrimary
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Accessibility Setup Banner (if disabled)
            AccessibilityHelperBanner(
                isAccessibilityActive = isAccessibilityActive,
                modifier = Modifier.testTag("accessibility_banner")
            )

            // 2. Primary Server Hosting Controller Card
            ServerControlCard(
                isHosting = isHosting,
                hostUrl = hostUrl,
                onToggleHosting = {
                    if (isHosting) {
                        onRequestStopHosting()
                    } else {
                        onRequestStartHosting()
                    }
                },
                onCopyUrl = {
                    viewModel.copyToClipboard(hostUrl)
                    scope.launch {
                        snackbarHostState.showSnackbar("Server URL copied to clipboard!")
                    }
                },
                onShowQr = {
                    viewModel.setShowQrDialog(true)
                },
                onShowDownload = {
                    viewModel.setShowDownloadDialog(true)
                },
                modifier = Modifier.testTag("server_control_card")
            )

            // 3. Live Stream Telemetry & Screen Preview Monitor
            LiveStreamMonitorCard(
                isHosting = isHosting,
                stats = serverStats,
                previewBitmap = previewBitmap,
                modifier = Modifier.testTag("stream_monitor_card")
            )

            // 4. Access Security & PIN Portal
            SecurityCard(
                pinAuthEnabled = hostSettings.pinAuthEnabled,
                pinCode = hostSettings.pinCode,
                downloadCode = downloadCode,
                onTogglePinAuth = { viewModel.setPinAuthEnabled(it) },
                onRegeneratePin = {
                    viewModel.generateNewPin()
                    scope.launch {
                        snackbarHostState.showSnackbar("Generated new secure PIN")
                    }
                },
                onCopyPin = {
                    viewModel.copyToClipboard(hostSettings.pinCode)
                    scope.launch {
                        snackbarHostState.showSnackbar("Host PIN copied to clipboard!")
                    }
                },
                onShowDownloadCode = {
                    viewModel.setShowDownloadDialog(true)
                },
                modifier = Modifier.testTag("security_card")
            )

            // 5. PC Keybinds & Gesture Mapping
            KeybindsManagerCard(
                keybinds = keybinds,
                onResetDefaults = {
                    viewModel.resetKeybindsToDefault()
                    scope.launch {
                        snackbarHostState.showSnackbar("Keybinds reset to standard defaults")
                    }
                },
                onOpenKeybindDialog = {
                    // Open keybinds manager
                },
                modifier = Modifier.testTag("keybinds_card")
            )

            // 6. Quality, Audio & Performance Settings
            PerformanceSettingsCard(
                settings = hostSettings,
                onResolutionChange = { viewModel.setResolution(it) },
                onFpsChange = { viewModel.setTargetFps(it) },
                onQualityChange = { viewModel.setJpegQuality(it) },
                onAudioToggle = { viewModel.setAudioStreaming(it) },
                onControlToggle = { viewModel.setRemoteControl(it) },
                onPortChange = { viewModel.setPort(it) },
                modifier = Modifier.testTag("performance_card")
            )

            // 7. Quick Connect Guide Card
            QuickGuideCard()

            Spacer(modifier = Modifier.height(24.dp))
        }

        // QR Code Dialog
        if (showQrDialog) {
            QrCodeDialog(
                qrBitmap = qrBitmap,
                hostUrl = hostUrl,
                pinCode = hostSettings.pinCode,
                pinEnabled = hostSettings.pinAuthEnabled,
                onDismiss = { viewModel.setShowQrDialog(false) },
                onCopyUrl = {
                    viewModel.copyToClipboard(hostUrl)
                    scope.launch {
                        snackbarHostState.showSnackbar("URL copied to clipboard!")
                    }
                }
            )
        }

        // Download & APK Dialog
        if (showDownloadDialog) {
            OneTimeDownloadDialog(
                downloadCode = downloadCode,
                downloadUrl = downloadUrl,
                apkUrl = viewModel.getApkDownloadUrl(),
                onDismiss = { viewModel.setShowDownloadDialog(false) },
                onCopyDownloadUrl = {
                    viewModel.copyToClipboard(downloadUrl)
                    scope.launch {
                        snackbarHostState.showSnackbar("Download URL copied!")
                    }
                },
                onCopyCode = {
                    viewModel.copyToClipboard(downloadCode)
                    scope.launch {
                        snackbarHostState.showSnackbar("Pairing code copied!")
                    }
                },
                onExportApk = {
                    viewModel.exportOrShareApk(context)
                }
            )
        }
    }
}

@Composable
fun QuickGuideCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("quick_guide_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(DarkBorder),
            width = 1.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.HelpOutline,
                    contentDescription = null,
                    tint = SkyGlow,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Zero-Config Quick Connect Guide",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = TextPrimary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            GuideStepItem(
                step = "1",
                title = "Connect to Same Local Network",
                desc = "Make sure your PC / Laptop and Phone are connected to the same Wi-Fi network or Mobile Hotspot."
            )

            Spacer(modifier = Modifier.height(8.dp))

            GuideStepItem(
                step = "2",
                title = "Open Browser & Enter URL",
                desc = "Open Chrome, Edge, Safari, or Firefox on your PC and enter the local IP address or scan the QR Code."
            )

            Spacer(modifier = Modifier.height(8.dp))

            GuideStepItem(
                step = "3",
                title = "Control with Mouse & Keyboard",
                desc = "Click, drag, and use mapped hotkeys (Esc for Back, Home for Home, Tab for App Switcher) with ultra-low latency."
            )
        }
    }
}

@Composable
private fun GuideStepItem(
    step: String,
    title: String,
    desc: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(CyberCyan.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = step,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = CyberCyan
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Text(
                text = desc,
                fontSize = 11.5.sp,
                color = TextSecondary,
                lineHeight = 16.sp
            )
        }
    }
}
