package com.example.yakuzaiapp.util

import android.content.Context
import android.graphics.Point
import android.util.Log
import androidx.camera.core.ExperimentalGetImage
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
private const val PTP_READ_AREA_WIDTH_RATIO = 0.78f
private const val PTP_READ_AREA_HEIGHT_RATIO = 0.84f
private const val PTP_MIN_BARCODE_AREA_RATIO = 0.0025f
private val WINDOWS_31J: Charset = Charset.forName("Windows-31J")
private val ISO_8859_1: Charset = Charsets.ISO_8859_1

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

@androidx.annotation.OptIn(ExperimentalGetImage::class)
class BarcodeAnalyzer(
    context: Context,
    private val mode: ScanMode = ScanMode.PTP_GTIN,
    private val cooldownMs: Long = 2000L,
    private val useMlKitFallback: Boolean = true,
    private val useTextExpirationFallback: Boolean = false,
    private val restrictPtpToCenter: Boolean = false,
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
    private val ptpStabilityGate = PtpStabilityGate()
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
                val centerX = result.position.centerX()
                val centerY = result.position.centerY()
                val area = result.position.boundsArea()
                if (restrictPtpToCenter &&
                    !isInsidePtpReadArea(centerX, centerY, image.width, image.height)
                ) {
                    Log.d(
                        TAG,
                        "zxing-ptp ignored outside read area center=($centerX,$centerY)"
                    )
                    return@mapNotNull null
                }
                if (restrictPtpToCenter &&
                    !isLargeEnoughPtpBarcode(area, image.width, image.height)
                ) {
                    Log.d(
                        TAG,
                        "zxing-ptp ignored too small area=$area image=${image.width}x${image.height}"
                    )
                    return@mapNotNull null
                }
                val left = result.position.points().minOfOrNull { it.x } ?: 0
                Log.d(
                    TAG,
                    "zxing-ptp format=${result.format} text-len=${text.length} left=$left center=($centerX,$centerY) area=$area"
                )
                DetectedQr(
                    text = text,
                    left = left,
                    centerX = centerX,
                    centerY = centerY,
                    area = area
                )
            }
            if (detections.isNotEmpty()) {
                val emitted = emitIfFresh(detections)
                if (emitted &&
                    useTextExpirationFallback &&
                    shouldRunTextExpirationFallback(detections)
                ) {
                    analyzePtpTextExpirationFallback(image, detections)
                } else {
                    image.close()
                }
            } else if (!useMlKitFallback) {
                resetPtpStabilityIfRestricted()
                image.close()
            } else {
                resetPtpStabilityIfRestricted()
                analyzePtpWithMlKitFallback(image)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "ZXing analyze failed for mode=$mode", e)
            if (useMlKitFallback) {
                resetPtpStabilityIfRestricted()
                analyzePtpWithMlKitFallback(image)
            } else {
                resetPtpStabilityIfRestricted()
                image.close()
            }
        }
    }

    private fun analyzePtpWithMlKitFallback(image: ImageProxy, emitResults: Boolean = true) {
        val mediaImage = image.image
        if (mediaImage == null) {
            resetPtpStabilityIfRestricted()
            image.close()
            return
        }
        if (!ptpMlKitProcessing.compareAndSet(false, true)) {
            resetPtpStabilityIfRestricted()
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
                        val centerX = barcode.cornerPoints?.centerX()
                        val centerY = barcode.cornerPoints?.centerY()
                        val area = barcode.cornerPoints?.boundsArea()
                        if (restrictPtpToCenter &&
                            !isInsidePtpReadArea(centerX, centerY, image.width, image.height)
                        ) {
                            Log.d(
                                TAG,
                                "mlkit-ptp ignored outside read area center=($centerX,$centerY)"
                            )
                            return@mapNotNull null
                        }
                        if (restrictPtpToCenter &&
                            !isLargeEnoughPtpBarcode(area, image.width, image.height)
                        ) {
                            Log.d(
                                TAG,
                                "mlkit-ptp ignored too small area=$area image=${image.width}x${image.height}"
                            )
                            return@mapNotNull null
                        }
                        Log.d(
                            TAG,
                            "mlkit-ptp format=${barcode.format} text-len=${text.length} left=$left center=($centerX,$centerY) area=$area"
                        )
                        DetectedQr(
                            text = text,
                            left = left,
                            centerX = centerX,
                            centerY = centerY,
                            area = area
                        )
                    }
                    if (emitResults) {
                        emitIfFresh(detections)
                    }
                }
                .addOnFailureListener(mainExecutor) { e ->
                    resetPtpStabilityIfRestricted()
                    Log.w(TAG, "PTP ML Kit fallback analyze failed", e)
                }
                .addOnCompleteListener(mainExecutor) {
                    ptpMlKitProcessing.set(false)
                    image.close()
                }
        } catch (e: Throwable) {
            ptpMlKitProcessing.set(false)
            resetPtpStabilityIfRestricted()
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
                            left = detection.left,
                            centerX = detection.centerX,
                            centerY = detection.centerY,
                            area = detection.area
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
                            "mlkit-jahis qr text-len=${text.length} left=$left"
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

    private fun emitIfFresh(detections: List<DetectedQr>): Boolean {
        val unique = detections
            .asSequence()
            .filter { it.text.isNotBlank() }
            .distinctBy { "${it.left}:${it.text}" }
            .toList()

        val ordered = orderDetectionsForEmit(mode, unique)

        if (ordered.isEmpty()) {
            resetPtpStabilityIfRestricted()
            return false
        }

        val shouldEmit = synchronized(emitLock) {
            val fingerprint = ordered.joinToString("|") {
                if (mode == ScanMode.PTP_GTIN) {
                    ptpCooldownFingerprint(it.text)
                } else {
                    "${it.left}:${it.text}"
                }
            }
            val now = System.currentTimeMillis()
            if (mode == ScanMode.PTP_GTIN &&
                restrictPtpToCenter &&
                !ptpStabilityGate.markCandidate(ptpStabilityKey(ordered.first().text))
            ) {
                false
            } else if (fingerprint == lastFingerprint && now - lastDetectedAt <= cooldownMs) {
                false
            } else {
                lastFingerprint = fingerprint
                lastDetectedAt = now
                true
            }
        }
        if (!shouldEmit) return false
        mainExecutor.execute {
            onResult(ordered)
        }
        return true
    }

    private fun resetPtpStabilityIfRestricted() {
        if (mode == ScanMode.PTP_GTIN && restrictPtpToCenter) {
            synchronized(emitLock) {
                ptpStabilityGate.reset()
            }
        }
    }
}

private const val PTP_STABLE_DETECTION_COUNT = 2

internal class PtpStabilityGate(
    private val requiredCount: Int = PTP_STABLE_DETECTION_COUNT
) {
    private var pendingKey: String? = null
    private var pendingCount: Int = 0

    fun reset() {
        pendingKey = null
        pendingCount = 0
    }

    fun markCandidate(key: String): Boolean {
        if (pendingKey == key) {
            pendingCount += 1
        } else {
            pendingKey = key
            pendingCount = 1
        }
        return pendingCount >= requiredCount
    }
}

internal fun ptpStabilityKey(text: String): String {
    return normalizeGtin(text) ?: text
}

internal fun ptpCooldownFingerprint(text: String): String {
    val key = ptpStabilityKey(text)
    val expiration = extractOcrGs1ExpirationDate(text)
    return if (expiration == null) key else "$key|17:$expiration"
}

internal fun orderDetectionsForEmit(
    mode: ScanMode,
    detections: List<DetectedQr>
): List<DetectedQr> {
    return if (mode == ScanMode.PTP_GTIN) {
        detections.sortedWith(
            compareByDescending<DetectedQr> { it.area ?: 0 }
                .thenBy { it.left }
        )
    } else {
        detections.sortedBy { it.left }
    }
}

internal fun isInsidePtpReadArea(
    centerX: Int?,
    centerY: Int?,
    imageWidth: Int,
    imageHeight: Int
): Boolean {
    if (centerX == null || centerY == null || imageWidth <= 0 || imageHeight <= 0) {
        return false
    }

    return isInsidePtpReadAreaForSize(centerX, centerY, imageWidth, imageHeight) ||
        (imageWidth != imageHeight &&
            isInsidePtpReadAreaForSize(centerX, centerY, imageHeight, imageWidth))
}

internal fun isLargeEnoughPtpBarcode(
    area: Int?,
    imageWidth: Int,
    imageHeight: Int
): Boolean {
    if (area == null || area <= 0 || imageWidth <= 0 || imageHeight <= 0) {
        return false
    }
    val minArea = imageWidth * imageHeight * PTP_MIN_BARCODE_AREA_RATIO
    return area >= minArea
}

private fun isInsidePtpReadAreaForSize(
    centerX: Int,
    centerY: Int,
    imageWidth: Int,
    imageHeight: Int
): Boolean {
    val left = imageWidth * (1f - PTP_READ_AREA_WIDTH_RATIO) / 2f
    val right = imageWidth - left
    val top = imageHeight * (1f - PTP_READ_AREA_HEIGHT_RATIO) / 2f
    val bottom = imageHeight - top
    return centerX >= left && centerX <= right && centerY >= top && centerY <= bottom
}

private fun BarcodeReader.Position.points(): List<Point> {
    return listOf(topLeft, topRight, bottomRight, bottomLeft)
}

private fun BarcodeReader.Position.centerX(): Int {
    return points().sumOf { it.x } / 4
}

private fun BarcodeReader.Position.centerY(): Int {
    return points().sumOf { it.y } / 4
}

private fun BarcodeReader.Position.boundsArea(): Int {
    return points().boundsArea()
}

private fun Array<Point>.centerX(): Int {
    return sumOf { it.x } / size
}

private fun Array<Point>.centerY(): Int {
    return sumOf { it.y } / size
}

private fun Array<Point>.boundsArea(): Int {
    return toList().boundsArea()
}

private fun List<Point>.boundsArea(): Int {
    if (isEmpty()) return 0
    val width = maxOf { it.x } - minOf { it.x }
    val height = maxOf { it.y } - minOf { it.y }
    return (width.coerceAtLeast(0) * height.coerceAtLeast(0))
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
