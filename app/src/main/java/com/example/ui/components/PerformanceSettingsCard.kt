package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.FpsOption
import com.example.model.HostSettings
import com.example.model.ResolutionOption
import com.example.ui.theme.*

@Composable
fun PerformanceSettingsCard(
    settings: HostSettings,
    onResolutionChange: (ResolutionOption) -> Unit,
    onFpsChange: (FpsOption) -> Unit,
    onQualityChange: (Int) -> Unit,
    onAudioToggle: (Boolean) -> Unit,
    onControlToggle: (Boolean) -> Unit,
    onPortChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
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
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = CyberCyan,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Streaming Quality & Latency",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = TextPrimary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 1. Resolution Selection
            Text(
                text = "STREAM RESOLUTION",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextMuted,
                letterSpacing = 0.8.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ResolutionOption.values().forEach { option ->
                    val isSelected = settings.resolution == option
                    Surface(
                        onClick = { onResolutionChange(option) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("res_${option.name}"),
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) CyberCyan else DarkBackground,
                        border = BorderStroke(1.dp, if (isSelected) CyberCyan else DarkBorder)
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = option.title,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color(0xFF041E2D) else TextPrimary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. FPS Target
            Text(
                text = "FRAME RATE TARGET",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextMuted,
                letterSpacing = 0.8.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FpsOption.values().forEach { option ->
                    val isSelected = settings.targetFps == option
                    Surface(
                        onClick = { onFpsChange(option) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("fps_${option.fps}"),
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) SkyGlow else DarkBackground,
                        border = BorderStroke(1.dp, if (isSelected) SkyGlow else DarkBorder)
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "${option.fps} FPS",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color(0xFF041E2D) else TextPrimary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3. Compression Quality Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "JPEG COMPRESSION QUALITY",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    letterSpacing = 0.8.sp
                )
                Text(
                    text = "${settings.jpegQuality}%",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberCyan
                )
            }
            Slider(
                value = settings.jpegQuality.toFloat(),
                onValueChange = { onQualityChange(it.toInt()) },
                valueRange = 30f..95f,
                steps = 12,
                modifier = Modifier.testTag("jpeg_quality_slider"),
                colors = SliderDefaults.colors(
                    thumbColor = CyberCyan,
                    activeTrackColor = CyberCyan,
                    inactiveTrackColor = DarkBorder
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 4. Audio Streaming Toggle
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = DarkBackground,
                border = BorderStroke(1.dp, DarkBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = null,
                            tint = SkyGlow,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = "Zero-Config Audio Streaming",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Broadcast internal audio to PC browser",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                    }

                    Switch(
                        checked = settings.audioStreamingEnabled,
                        onCheckedChange = onAudioToggle,
                        modifier = Modifier.testTag("audio_stream_switch"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF041E2D),
                            checkedTrackColor = SkyGlow,
                            uncheckedTrackColor = DarkSurfaceElevated,
                            uncheckedThumbColor = TextMuted
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 5. Remote Mouse & Touch Control Toggle
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = DarkBackground,
                border = BorderStroke(1.dp, DarkBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mouse,
                            contentDescription = null,
                            tint = EmeraldMint,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = "Remote Touch & Mouse Control",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Allow clicks, drags, and keystrokes from PC",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                    }

                    Switch(
                        checked = settings.remoteControlEnabled,
                        onCheckedChange = onControlToggle,
                        modifier = Modifier.testTag("remote_control_switch"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF041E2D),
                            checkedTrackColor = EmeraldMint,
                            uncheckedTrackColor = DarkSurfaceElevated,
                            uncheckedThumbColor = TextMuted
                        )
                    )
                }
            }
        }
    }
}
