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

/**
 * Scan mode that controls which barcode formats the ML Kit scanner listens for.
 *
 * - [DELIVERY]: Optimized for GS1-128 delivery labels (CODE_128, UPC-A/E, EAN-13/8, ITF, CODE_39).
 * - [BADGE]:    Optimized for employee QR-code badges (QR_CODE only — fast and unambiguous).
 */
enum class ScanMode { DELIVERY, BADGE }

class BarcodeAnalyzer(
    private val onBarcodeFound: (rawUpc: String, normalizedUpc: String) -> Unit,
    scanMode: ScanMode = ScanMode.DELIVERY
) : ImageAnalysis.Analyzer {

    private val scanner: com.google.mlkit.vision.barcode.BarcodeScanner

    init {
        // Compute the format list first so the spread operator can be applied correctly.
        val formats = when (scanMode) {
            ScanMode.DELIVERY -> intArrayOf(
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E,
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_CODE_128,
                Barcode.FORMAT_CODE_39,
                Barcode.FORMAT_ITF
            )
            ScanMode.BADGE -> intArrayOf(Barcode.FORMAT_QR_CODE)
        }
        // setBarcodeFormats(Int, vararg Int) — first element required, rest spread
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(formats[0], *formats.drop(1).toIntArray())
            .build()
        scanner = BarcodeScanning.getClient(options)
    }

    /** Set to false after a successful scan; call [reset] to re-enable for the next item. */
    var isScanningEnabled: Boolean = true
        private set

    /** 1.5-second atomic debounce to prevent duplicate reads of the same label. */
    private val lastScanTimestamp = AtomicLong(0L)
    private val debounceMillis = 1500L

    /** Re-arm the scanner for the next delivery box / next scan session. */
    fun reset() {
        isScanningEnabled = true
    }

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
