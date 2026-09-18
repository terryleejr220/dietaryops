package com.dietaryops.manager.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dietaryops.manager.ui.theme.ScannerLaserBrush

/**
 * Modern Scanner HUD Overlay providing high-tech corner brackets,
 * animated scanning laser, and frosted guidance status pills.
 */
@Composable
fun ScannerOverlay(
    isScanningActive: Boolean,
    isCameraActive: Boolean,
    onResumeCamera: () -> Unit,
    modifier: Modifier = Modifier
) {
    val laserColor = MaterialTheme.colorScheme.primary

    // Smooth continuous laser scanline animation
    val infiniteTransition = rememberInfiniteTransition(label = "scanner_laser")
    val laserProgress by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser_y"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(18.dp))
    ) {
        if (isCameraActive) {
            // Draw Reticle Corner Brackets and Laser Line
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height

                // Active scanning reticle dimensions (centered)
                val boxWidth = width * 0.82f
                val boxHeight = height * 0.72f
                val left = (width - boxWidth) / 2f
                val top = (height - boxHeight) / 2f
                val right = left + boxWidth
                val bottom = top + boxHeight

                val bracketLength = 28.dp.toPx()
                val strokeWidth = 3.5.dp.toPx()
                val cornerRadius = 8.dp.toPx()

                // Corner 1: Top-Left
                drawPath(
                    path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(left, top + bracketLength)
                        lineTo(left, top + cornerRadius)
                        quadraticBezierTo(left, top, left + cornerRadius, top)
                        lineTo(left + bracketLength, top)
                    },
                    color = laserColor,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                // Corner 2: Top-Right
                drawPath(
                    path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(right - bracketLength, top)
                        lineTo(right - cornerRadius, top)
                        quadraticBezierTo(right, top, right, top + cornerRadius)
                        lineTo(right, top + bracketLength)
                    },
                    color = laserColor,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                // Corner 3: Bottom-Left
                drawPath(
                    path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(left, bottom - bracketLength)
                        lineTo(left, bottom - cornerRadius)
                        quadraticBezierTo(left, bottom, left + cornerRadius, bottom)
                        lineTo(left + bracketLength, bottom)
                    },
                    color = laserColor,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                // Corner 4: Bottom-Right
                drawPath(
                    path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(right - bracketLength, bottom)
                        lineTo(right - cornerRadius, bottom)
                        quadraticBezierTo(right, bottom, right, bottom - cornerRadius)
                        lineTo(right, bottom + bracketLength)
                    },
                    color = laserColor,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                // Laser scanline if actively scanning
                if (isScanningActive) {
                    val laserY = top + (boxHeight * laserProgress)
                    // Glow beam behind line
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                laserColor.copy(alpha = 0.0f),
                                laserColor.copy(alpha = 0.25f),
                                laserColor.copy(alpha = 0.0f)
                            ),
                            startY = laserY - 14.dp.toPx(),
                            endY = laserY + 14.dp.toPx()
                        ),
                        topLeft = Offset(left + 4.dp.toPx(), laserY - 14.dp.toPx()),
                        size = Size(boxWidth - 8.dp.toPx(), 28.dp.toPx())
                    )

                    // Sharp center laser line
                    drawLine(
                        color = laserColor,
                        start = Offset(left + 6.dp.toPx(), laserY),
                        end = Offset(right - 6.dp.toPx(), laserY),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }

            // Top Status Pill
            Surface(
                color = Color(0x99000000),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF)),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (isScanningActive) Color(0xFF10B981) else Color(0xFFF59E0B))
                    )
                    Text(
                        text = if (isScanningActive) "SCANNER ACTIVE" else "BARCODE DETECTED",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp
                    )
                }
            }

            // Bottom Guidance Pill
            Surface(
                color = Color(0xAA0B132B),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, laserColor.copy(alpha = 0.4f)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = null,
                        tint = laserColor,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "ALIGN BARCODE WITHIN RETICLE",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        } else {
            // Camera Paused / Inactive State Overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xEE0B132B)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        shape = CircleShape,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.QrCodeScanner,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Text(
                        text = "Camera Paused for Battery Savings",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Button(
                        onClick = onResumeCamera,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Resume Scanner", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
