package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@Composable
fun AccessibilityHelperBanner(
    isAccessibilityActive: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (isAccessibilityActive) DarkSurfaceElevated else AmberGlow.copy(alpha = 0.12f),
        border = BorderStroke(
            1.dp,
            if (isAccessibilityActive) NeonGreen.copy(alpha = 0.4f) else AmberGlow.copy(alpha = 0.6f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = if (isAccessibilityActive) Icons.Default.CheckCircle else Icons.Default.WarningAmber,
                    contentDescription = null,
                    tint = if (isAccessibilityActive) NeonGreen else AmberGlow,
                    modifier = Modifier.size(24.dp)
                )
                Column {
                    Text(
                        text = if (isAccessibilityActive) "Remote Touch Control Active" else "Enable Remote Touch Control",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = TextPrimary
                    )
                    Text(
                        text = if (isAccessibilityActive) "Browser mouse clicks & gestures are dispatched instantly." else "Accessibility permission is required to simulate touches on phone.",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }

            if (!isAccessibilityActive) {
                Button(
                    onClick = {
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                    },
                    modifier = Modifier.testTag("enable_accessibility_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AmberGlow,
                        contentColor = Color(0xFF041E2D)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("Enable", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
