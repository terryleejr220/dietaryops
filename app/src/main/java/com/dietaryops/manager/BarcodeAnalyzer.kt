package com.dietaryops.manager

import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.dietaryops.manager.util.Gs1BarcodeParser
import com.dietaryops.manager.util.SyscoUpcNormalizer
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.atomic.AtomicLong

class BarcodeAnalyzer(
    private val onBarcodeFound: (rawUpc: String, normalizedUpc: String) -> Unit
) : ImageAnalysis.Analyzer {

    private val options = BarcodeScannerOptions.Builder()
        .setBarcodeFormats(
            Barcode.FORMAT_UPC_A,
            Barcode.FORMAT_UPC_E,
            Barcode.FORMAT_EAN_13,
            Barcode.FORMAT_EAN_8,
            Barcode.FORMAT_CODE_128,
            Barcode.FORMAT_CODE_39,
            Barcode.FORMAT_ITF,
            Barcode.FORMAT_ALL_FORMATS
        )
        .build()

    private val scanner = BarcodeScanning.getClient(options)
    var isScanningEnabled: Boolean = true

    // 1.5-second atomic debounce timestamp tracking
    private val lastScanTimestamp = AtomicLong(0L)
    private val debounceMillis = 1500L

    @ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        val now = System.currentTimeMillis()

        if (mediaImage != null && isScanningEnabled && (now - lastScanTimestamp.get() >= debounceMillis)) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    val currentTime = System.currentTimeMillis()
                    for (barcode in barcodes) {
                        val rawValue = barcode.rawValue
                        if (!rawValue.isNullOrBlank()) {
                            val lastTime = lastScanTimestamp.get()
                            if (currentTime - lastTime >= debounceMillis) {
                                if (lastScanTimestamp.compareAndSet(lastTime, currentTime)) {
                                    val parsed = Gs1BarcodeParser.parseBarcode(rawValue)
                                    val normalized = parsed.extractedUpc.ifBlank { SyscoUpcNormalizer.normalize(rawValue) }
                                    isScanningEnabled = false
                                    onBarcodeFound(rawValue, normalized)
                                    break
                                }
                            }
                        }
                    }
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }
}
