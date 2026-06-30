package com.example.yakuzaiapp.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.charset.Charset

class BarcodeAnalyzerTest {
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
}
