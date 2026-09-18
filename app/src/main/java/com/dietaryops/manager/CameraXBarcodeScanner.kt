package com.dietaryops.manager

import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

@Composable
fun CameraXBarcodeScanner(
    modifier: Modifier = Modifier,
    isScanningEnabled: Boolean = true,
    isCameraActive: Boolean = true,
    onResumeCamera: (() -> Unit)? = null,
    onBarcodeFound: (rawUpc: String, normalizedUpc: String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var camera by remember { mutableStateOf<Camera?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var isFlashOn by remember { mutableStateOf(false) }

    val barcodeAnalyzer = remember {
        BarcodeAnalyzer { raw, normalized ->
            onBarcodeFound(raw, normalized)
        }
    }

    LaunchedEffect(isScanningEnabled) {
        barcodeAnalyzer.isScanningEnabled = isScanningEnabled
    }

    // Dynamic Camera Lifecycle binding / unbinding based on isCameraActive
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    LaunchedEffect(isCameraActive) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            val provider = providerFuture.get()
            cameraProvider = provider
            if (isCameraActive) {
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(Executors.newSingleThreadExecutor(), barcodeAnalyzer)
                    }
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                try {
                    provider.unbindAll()
                    camera = provider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else {
                try {
                    provider.unbindAll()
                    camera = null
                    isFlashOn = false
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }, ContextCompat.getMainExecutor(context))
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                cameraProvider?.unbindAll()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Animated scanning laser line
    val infiniteTransition = rememberInfiniteTransition(label = "scanning_line")
    val lineOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "line_offset"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black)
    ) {
        if (isCameraActive) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )

            // Viewfinder framing cutout
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.85f)
                    .fillMaxHeight(0.6f)
                    .border(
                        width = 2.dp,
                        color = if (isScanningEnabled) Color(0xFF00E676) else Color.Gray,
                        shape = RoundedCornerShape(12.dp)
                    )
            )

            // Scanning laser animation line
            if (isScanningEnabled) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val boxWidth = size.width * 0.85f
                    val boxHeight = size.height * 0.6f
                    val left = (size.width - boxWidth) / 2
                    val top = (size.height - boxHeight) / 2

                    val currentLineY = top + (boxHeight * lineOffset)
                    drawLine(
                        color = Color(0xFF00E676),
                        start = Offset(left + 12f, currentLineY),
                        end = Offset(left + boxWidth - 12f, currentLineY),
                        strokeWidth = 4f
                    )
                }
            }

            // Torch toggle button
            IconButton(
                onClick = {
                    camera?.let { cam ->
                        if (cam.cameraInfo.hasFlashUnit()) {
                            isFlashOn = !isFlashOn
                            cam.cameraControl.enableTorch(isFlashOn)
                        }
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .background(Color.Black.copy(alpha = 0.5f), shape = CircleShape)
            ) {
                Icon(
                    imageVector = if (isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                    contentDescription = "Toggle Flash",
                    tint = Color.White
                )
            }
        } else {
            // Paused Camera overlay (Battery Saver Mode)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1E1E1E)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.VideocamOff,
                        contentDescription = "Camera Off",
                        tint = Color.Gray,
                        modifier = Modifier.size(40.dp)
                    )
                    Text(
                        text = "Camera Paused (Battery Saver)",
                        color = Color.LightGray,
                        style = MaterialTheme.typography.bodySmall
                    )
                    onResumeCamera?.let { onResume ->
                        Button(
                            onClick = onResume,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Resume Scanner")
                        }
                    }
                }
            }
        }
    }
}
