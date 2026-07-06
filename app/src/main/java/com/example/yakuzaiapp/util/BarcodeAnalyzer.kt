package com.example.yakuzaiapp.util

import android.content.Context
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.core.content.ContextCompat
import com.example.yakuzaiapp.domain.jahis.DetectedQr
import com.example.yakuzaiapp.domain.scan.ScanMode
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import java.nio.charset.Charset
import java.util.concurrent.atomic.AtomicBoolean
import zxingcpp.BarcodeReader

private const val TAG = "BarcodeAnalyzer"
private const val TEXT_EXPIRATION_FALLBACK_COOLDOWN_MS = 1500L
private val WINDOWS_31J: Charset = Charset.forName("Windows-31J")
private val ISO_8859_1: Charset = Charsets.ISO_8859_1

private fun escapeForLog(s: String): String {
    return s
        .replace("\\", "\\\\")
        .replace("\r", "\\r")
        .replace("\n", "\\n")
}

internal fun decodeJahisMlKitText(rawValue: String?, rawBytes: ByteArray?): String? {
    rawBytes?.takeIf { it.isNotEmpty() }?.let { bytes ->
        val decoded = runCatching { String(bytes, WINDOWS_31J) }.getOrNull()
        if (!decoded.isNullOrBlank()) {
            return decoded
        }
    }

    rawValue?.takeIf { it.isNotBlank() }?.let { value ->
        val restored = runCatching {
            String(value.toByteArray(ISO_8859_1), WINDOWS_31J)
        }.getOrNull()
        if (!restored.isNullOrBlank()) {
            return restored
        }
        return value
    }

    return null
}

class BarcodeAnalyzer(
    context: Context,
    private val mode: ScanMode = ScanMode.PTP_GTIN,
    private val cooldownMs: Long = 2000L,
    private val useMlKitFallback: Boolean = true,
    private val useTextExpirationFallback: Boolean = false,
    private val onResult: (List<DetectedQr>) -> Unit
) : ImageAnalysis.Analyzer {
    private val zxingReader = BarcodeReader().apply {
        options = BarcodeReader.Options(
            formats = setOf(
                BarcodeReader.Format.DATA_BAR,
                BarcodeReader.Format.DATA_BAR_LIMITED,
                BarcodeReader.Format.DATA_BAR_EXPANDED,
                BarcodeReader.Format.CODE_128,
                BarcodeReader.Format.DATA_MATRIX,
                BarcodeReader.Format.QR_CODE,
                BarcodeReader.Format.EAN_13,
                BarcodeReader.Format.EAN_8,
                BarcodeReader.Format.ITF,
                BarcodeReader.Format.UPC_A,
                BarcodeReader.Format.UPC_E,
                BarcodeReader.Format.CODE_39
            ),
            tryHarder = true,
            tryRotate = true,
            tryInvert = true,
            tryDownscale = true
        )
    }
    private val jahisScannerOptions = BarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
        .build()
    private val ptpScannerOptions = BarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
        .build()
    private val jahisBarcodeScanner: BarcodeScanner = BarcodeScanning.getClient(jahisScannerOptions)
    private val ptpBarcodeScanner: BarcodeScanner = BarcodeScanning.getClient(ptpScannerOptions)
    private val textRecognizer: TextRecognizer? = if (useTextExpirationFallback) {
        TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
    } else {
        null
    }
    private val mainExecutor = ContextCompat.getMainExecutor(context)
    private val jahisProcessing = AtomicBoolean(false)
    private val ptpMlKitProcessing = AtomicBoolean(false)
    private val ptpTextProcessing = AtomicBoolean(false)
    private var lastFingerprint: String? = null
    private var lastDetectedAt: Long = 0L
    private var lastTextFallbackFingerprint: String? = null
    private var lastTextFallbackAt: Long = 0L
    private val emitLock = Any()

    override fun analyze(image: ImageProxy) {
        when (mode) {
            ScanMode.PTP_GTIN -> analyzeWithZxing(image)
            ScanMode.JAHIS_QR -> analyzeWithMlKitForJahis(image)
        }
    }

    fun close() {
        runCatching { ptpBarcodeScanner.close() }
        runCatching { jahisBarcodeScanner.close() }
        runCatching { textRecognizer?.close() }
    }

    private fun analyzeWithZxing(image: ImageProxy) {
        try {
            val results = zxingReader.read(image)
            val detections = results.mapNotNull { result ->
                val text = result.text?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                Log.d(
                    TAG,
                    "zxing-ptp format=${result.format} text-len=${text.length} text-head='${escapeForLog(text.take(40))}'"
                )
                DetectedQr(text = text, left = 0)
            }
            if (detections.isNotEmpty()) {
                emitIfFresh(detections)
                if (useTextExpirationFallback && shouldRunTextExpirationFallback(detections)) {
                    analyzePtpTextExpirationFallback(image, detections)
                } else {
                    image.close()
                }
            } else if (!useMlKitFallback) {
                image.close()
            } else {
                analyzePtpWithMlKitFallback(image)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "ZXing analyze failed for mode=$mode", e)
            if (useMlKitFallback) {
                analyzePtpWithMlKitFallback(image)
            } else {
                image.close()
            }
        }
    }

    private fun analyzePtpWithMlKitFallback(image: ImageProxy, emitResults: Boolean = true) {
        val mediaImage = image.image
        if (mediaImage == null) {
            image.close()
            return
        }
        if (!ptpMlKitProcessing.compareAndSet(false, true)) {
            image.close()
            return
        }

        try {
            val inputImage = InputImage.fromMediaImage(mediaImage, image.imageInfo.rotationDegrees)
            ptpBarcodeScanner.process(inputImage)
                .addOnSuccessListener(mainExecutor) { barcodes ->
                    val detections = barcodes.mapNotNull { barcode ->
                        val text = barcode.rawValue
                            ?.trim()
                            ?.takeIf { it.isNotBlank() }
                            ?: return@mapNotNull null
                        val left = barcode.cornerPoints?.minOfOrNull { it.x } ?: 0
                        Log.d(
                            TAG,
                            "mlkit-ptp format=${barcode.format} text-len=${text.length} left=$left text-head='${escapeForLog(text.take(40))}'"
                        )
                        DetectedQr(text = text, left = left)
                    }
                    if (emitResults) {
                        emitIfFresh(detections)
                    }
                }
                .addOnFailureListener(mainExecutor) { e ->
                    Log.w(TAG, "PTP ML Kit fallback analyze failed", e)
                }
                .addOnCompleteListener(mainExecutor) {
                    ptpMlKitProcessing.set(false)
                    image.close()
                }
        } catch (e: Throwable) {
            ptpMlKitProcessing.set(false)
            image.close()
            Log.w(TAG, "PTP ML Kit fallback failed before task submission", e)
        }
    }

    private fun shouldRunTextExpirationFallback(detections: List<DetectedQr>): Boolean {
        val fingerprint = detections.joinToString("|") { it.text }
        val now = System.currentTimeMillis()
        if (fingerprint == lastTextFallbackFingerprint &&
            now - lastTextFallbackAt < TEXT_EXPIRATION_FALLBACK_COOLDOWN_MS
        ) {
            return false
        }
        lastTextFallbackFingerprint = fingerprint
        lastTextFallbackAt = now
        return true
    }

    private fun analyzePtpTextExpirationFallback(
        image: ImageProxy,
        barcodeDetections: List<DetectedQr>
    ) {
        val recognizer = textRecognizer
        val mediaImage = image.image
        if (recognizer == null || mediaImage == null) {
            image.close()
            return
        }
        if (!ptpTextProcessing.compareAndSet(false, true)) {
            image.close()
            return
        }

        try {
            val inputImage = InputImage.fromMediaImage(mediaImage, image.imageInfo.rotationDegrees)
            recognizer.process(inputImage)
                .addOnSuccessListener(mainExecutor) { text ->
                    val expirationDate = extractOcrGs1ExpirationDate(text.text)
                    Log.d(
                        TAG,
                        "ocr-ptp text-len=${text.text.length} expiration-found=${expirationDate != null}"
                    )
                    if (expirationDate == null) {
                        return@addOnSuccessListener
                    }

                    val enriched = barcodeDetections.mapNotNull { detection ->
                        val gtin = normalizeGtin(detection.text) ?: return@mapNotNull null
                        DetectedQr(
                            text = "(01)$gtin(17)$expirationDate",
                            left = detection.left
                        )
                    }
                    emitIfFresh(enriched)
                }
                .addOnFailureListener(mainExecutor) { e ->
                    Log.w(TAG, "PTP text expiration fallback failed", e)
                }
                .addOnCompleteListener(mainExecutor) {
                    ptpTextProcessing.set(false)
                    image.close()
                }
        } catch (e: Throwable) {
            ptpTextProcessing.set(false)
            image.close()
            Log.w(TAG, "PTP text expiration fallback failed before task submission", e)
        }
    }

    private fun analyzeWithMlKitForJahis(image: ImageProxy) {
        val mediaImage = image.image
        if (mediaImage == null) {
            image.close()
            return
        }
        if (!jahisProcessing.compareAndSet(false, true)) {
            image.close()
            return
        }

        try {
            val inputImage = InputImage.fromMediaImage(mediaImage, image.imageInfo.rotationDegrees)
            jahisBarcodeScanner.process(inputImage)
                .addOnSuccessListener(mainExecutor) { barcodes ->
                    val detections = barcodes.mapNotNull { barcode ->
                        if (barcode.format != Barcode.FORMAT_QR_CODE) {
                            return@mapNotNull null
                        }

                        val text = decodeJahisMlKitText(barcode.rawValue, barcode.rawBytes)
                            ?.trim()
                            ?.takeIf { it.isNotBlank() }
                            ?: return@mapNotNull null

                        val left = barcode.cornerPoints?.minOfOrNull { it.x } ?: 0
                        Log.d(
                            TAG,
                            "mlkit-jahis qr text-len=${text.length} left=$left text-head='${escapeForLog(text.take(40))}'"
                        )
                        DetectedQr(
                            text = text,
                            left = left,
                            saSequence = null,
                            saTotal = null,
                            saParity = null
                        )
                    }
                    emitIfFresh(detections)
                }
                .addOnFailureListener(mainExecutor) { e ->
                    Log.w(TAG, "JAHIS ML Kit analyze failed", e)
                }
                .addOnCompleteListener(mainExecutor) {
                    jahisProcessing.set(false)
                    image.close()
                }
        } catch (e: Throwable) {
            jahisProcessing.set(false)
            image.close()
            Log.w(TAG, "JAHIS ML Kit analyze failed before task submission", e)
        }
    }

    private fun emitIfFresh(detections: List<DetectedQr>) {
        val ordered = detections
            .asSequence()
            .filter { it.text.isNotBlank() }
            .distinctBy { "${it.left}:${it.text}" }
            .sortedBy { it.left }
            .toList()

        if (ordered.isEmpty()) return

        val shouldEmit = synchronized(emitLock) {
            val fingerprint = ordered.joinToString("|") { "${it.left}:${it.text}" }
            val now = System.currentTimeMillis()
            if (fingerprint == lastFingerprint && now - lastDetectedAt <= cooldownMs) {
                false
            } else {
                lastFingerprint = fingerprint
                lastDetectedAt = now
                true
            }
        }
        if (!shouldEmit) return
        mainExecutor.execute {
            onResult(ordered)
        }
    }
}

private fun extractOcrGs1ExpirationDate(text: String): String? {
    val normalized = text.map { ch ->
        when (ch) {
            in '０'..'９' -> '0' + (ch - '０')
            '（' -> '('
            '）' -> ')'
            else -> ch
        }
    }.joinToString("")

    Regex("""\(?\s*17\s*\)?\s*([0-9]{6})""")
        .find(normalized)
        ?.groups
        ?.get(1)
        ?.value
        ?.let { return it }

    return null
}
