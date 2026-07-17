package com.example.yakuzaiapp.util

import com.example.yakuzaiapp.domain.jahis.DetectedQr
import com.example.yakuzaiapp.domain.scan.ScanMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.charset.Charset

class BarcodeAnalyzerTest {
    @Test
    fun structuredAppendMetadata_acceptsCompleteSequenceMetadata() {
        assertEquals(
            StructuredAppendMetadata(sequence = 1, total = 3, groupId = "group-a"),
            structuredAppendMetadata(sequenceSize = 3, sequenceIndex = 1, sequenceId = "group-a")
        )
    }

    @Test
    fun structuredAppendMetadata_rejectsSingleQrAndInvalidSequence() {
        assertNull(structuredAppendMetadata(sequenceSize = 1, sequenceIndex = 0, sequenceId = null))
        assertNull(structuredAppendMetadata(sequenceSize = 3, sequenceIndex = 3, sequenceId = "group-a"))
        assertNull(structuredAppendMetadata(sequenceSize = 3, sequenceIndex = 1, sequenceId = ""))
    }

    @Test
    fun decodeJahisMlKitText_prefersShiftJisRawBytesOverRawValue() {
        val expected = "JAHISTC01\n1,谷川長子,1,19561116\n"
        val decoded = decodeJahisMlKitText(
            rawValue = "????",
            rawBytes = expected.toByteArray(Charset.forName("Windows-31J"))
        )

        assertEquals(expected, decoded)
    }

    @Test
    fun decodeJahisMlKitText_usesRawValueWhenRawBytesMissing() {
        val expected = "JAHISTC07,1"

        val decoded = decodeJahisMlKitText(
            rawValue = expected,
            rawBytes = null
        )

        assertEquals(expected, decoded)
    }

    @Test
    fun decodeJahisMlKitText_keepsAsciiRawValueWhenRawBytesMissing() {
        val expected = "ABC123"

        val decoded = decodeJahisMlKitText(
            rawValue = expected,
            rawBytes = null
        )

        assertEquals(expected, decoded)
    }

    @Test
    fun decodeJahisMlKitText_returnsNullWhenNothingAvailable() {
        assertNull(decodeJahisMlKitText(rawValue = null, rawBytes = null))
    }

    @Test
    fun isInsidePtpReadArea_acceptsCenterPoint() {
        assertEquals(true, isInsidePtpReadArea(640, 360, 1280, 720))
    }

    @Test
    fun isInsidePtpReadArea_rejectsEdgePoint() {
        assertEquals(false, isInsidePtpReadArea(40, 360, 1280, 720))
    }

    @Test
    fun isInsidePtpReadArea_rejectsMissingCoordinates() {
        assertEquals(false, isInsidePtpReadArea(null, 360, 1280, 720))
    }

    @Test
    fun isLargeEnoughPtpBarcode_acceptsNearBarcodeArea() {
        assertEquals(true, isLargeEnoughPtpBarcode(3000, 1280, 720))
    }

    @Test
    fun isLargeEnoughPtpBarcode_rejectsFarBarcodeArea() {
        assertEquals(false, isLargeEnoughPtpBarcode(1800, 1280, 720))
    }

    @Test
    fun isInsidePtpReadArea_acceptsSwappedImageDimensions() {
        assertEquals(true, isInsidePtpReadArea(360, 640, 1280, 720))
    }

    @Test
    fun orderDetectionsForEmit_prefersLargerPtpBarcode() {
        val smaller = DetectedQr(text = "small", left = 10, area = 100)
        val larger = DetectedQr(text = "large", left = 300, area = 1000)

        val ordered = orderDetectionsForEmit(ScanMode.PTP_GTIN, listOf(smaller, larger))

        assertEquals(listOf(larger, smaller), ordered)
    }

    @Test
    fun orderDetectionsForEmit_keepsJahisLeftOrdering() {
        val right = DetectedQr(text = "right", left = 300, area = 1000)
        val left = DetectedQr(text = "left", left = 10, area = 100)

        val ordered = orderDetectionsForEmit(ScanMode.JAHIS_QR, listOf(right, left))

        assertEquals(listOf(left, right), ordered)
    }

    @Test
    fun ptpStabilityGate_requiresConsecutiveCandidate() {
        val gate = PtpStabilityGate()

        assertEquals(false, gate.markCandidate("A"))
        gate.reset()
        assertEquals(false, gate.markCandidate("A"))
        assertEquals(true, gate.markCandidate("A"))
    }

    @Test
    fun ptpStabilityKey_usesGtinForRawAndEnrichedText() {
        val raw = "0104987155186574"
        val enriched = "(01)04987155186574(17)260101"

        assertEquals(ptpStabilityKey(raw), ptpStabilityKey(enriched))
    }

    @Test
    fun ptpCooldownFingerprint_keepsEnrichedExpirationDistinct() {
        val raw = "0104987155186574"
        val enriched = "(01)04987155186574(17)260101"

        assertEquals("04987155186574", ptpCooldownFingerprint(raw))
        assertEquals("04987155186574|17:260101", ptpCooldownFingerprint(enriched))
    }
}
