package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ServerStats
import com.example.network.NetworkUtils
import com.example.ui.theme.*

@Composable
fun LiveStreamMonitorCard(
    isHosting: Boolean,
    stats: ServerStats,
    previewBitmap: Bitmap?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.horizontalGradient(listOf(DarkBorder, DarkBorder)),
            width = 1.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Header with Live telemetry title & Active clients count
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Sensors,
                        contentDescription = null,
                        tint = CyberCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Live Stream Telemetry",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = TextPrimary
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (stats.connectedClientsCount > 0) EmeraldMint.copy(alpha = 0.15f) else DarkBackground,
                    border = BorderStroke(
                        1.dp,
                        if (stats.connectedClientsCount > 0) EmeraldMint else DarkBorder
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Devices,
                            contentDescription = null,
                            tint = if (stats.connectedClientsCount > 0) EmeraldMint else TextMuted,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "${stats.connectedClientsCount} Connected",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (stats.connectedClientsCount > 0) EmeraldMint else TextMuted
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Screen Preview Frame
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(DarkBackground)
                    .border(1.dp, if (isHosting) CyberCyan.copy(alpha = 0.4f) else DarkBorder, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (isHosting && previewBitmap != null) {
                    Image(
                        bitmap = previewBitmap.asImageBitmap(),
                        contentDescription = "Live Screen Thumbnail",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tv,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = if (isHosting) "Waiting for frame capture..." else "Mirroring is currently inactive",
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                    }
                }

                // Overlay Mini HUD
                if (isHosting) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.75f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "LIVE",
                            color = NeonGreen,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 4-Column Metric Grid: FPS, Latency, Bandwidth, Uptime
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TelemetryStatItem(
                    label = "FRAME RATE",
                    value = if (isHosting) "${stats.currentFps.toInt()} FPS" else "--",
                    color = CyberCyan,
                    modifier = Modifier.weight(1f)
                )

                TelemetryStatItem(
                    label = "LATENCY",
                    value = if (isHosting) "${stats.latencyMs} ms" else "--",
                    color = NeonGreen,
                    modifier = Modifier.weight(1f)
                )

                TelemetryStatItem(
                    label = "DATA SENT",
                    value = NetworkUtils.formatBytes(stats.totalBytesTransferred),
                    color = SkyGlow,
                    modifier = Modifier.weight(1f)
                )

                TelemetryStatItem(
                    label = "UPTIME",
                    value = formatUptime(stats.uptimeSeconds),
                    color = AmberGlow,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun TelemetryStatItem(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = DarkBackground,
        border = BorderStroke(1.dp, DarkBorder)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Bold,
                color = TextMuted,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = color,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

private fun formatUptime(seconds: Long): String {
    val m = (seconds / 60) % 60
    val s = seconds % 60
    return String.format("%02d:%02d", m, s)
}
