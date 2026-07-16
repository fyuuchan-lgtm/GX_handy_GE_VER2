package com.example.yakuzaiapp.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GtinNormalizerTest {
    @Test
    fun normalizeMasterBarcode_keepsFacilitySpecificCode() {
        assertEquals("INHOUSE-001", normalizeMasterBarcode(" INHOUSE-001 "))
    }

    @Test
    fun normalizeMasterBarcode_normalizesCommercialGtin() {
        assertEquals("04987376861687", normalizeMasterBarcode("4987376861687"))
    }

    @Test
    fun normalizeMasterBarcode_rejectsBlankAndControlCharacters() {
        assertNull(normalizeMasterBarcode("  "))
        assertNull(normalizeMasterBarcode("ABC\n123"))
    }

    @Test
    fun ai01Prefixed16DigitsNormalizesTo14Digits() {
        assertEquals("04987732010087", normalizeGtin("0104987732010087"))
    }

    @Test
    fun ai01PrefixedWithTrailingAisNormalizesTo14Digits() {
        assertEquals("04987732010087", normalizeGtin("01049877320100871725123110ABC123"))
    }

    @Test
    fun ai01WithParenthesesNormalizesTo14Digits() {
        assertEquals("04987732010087", normalizeGtin("(01)04987732010087(17)251231"))
    }

    @Test
    fun fullWidthDigitsNormalizeTo14Digits() {
        assertEquals("04987732010087", normalizeGtin("０４９８７７３２０１００８７"))
    }

    @Test
    fun gtin14ReturnsAsIs() {
        assertEquals("04987224716428", normalizeGtin("04987224716428"))
    }

    @Test
    fun jan13PrefixesZero() {
        assertEquals("04987732010087", normalizeGtin("4987732010087"))
    }

    @Test
    fun unknownFormatReturnsNull() {
        assertNull(normalizeGtin("12345"))
        assertNull(normalizeGtin(""))
    }

    @Test
    fun invalidCheckDigitReturnsNull() {
        assertNull(normalizeGtin("14987732010086"))
    }

    @Test
    fun isPackageBarcodeDetectsOnlyPackageGtins() {
        assertFalse(isPackageBarcode("04987732010087"))
        assertTrue(isPackageBarcode("14987376861653"))
        assertFalse(isPackageBarcode("04987376861687"))
        assertTrue(isPackageBarcode("24987376861653"))
        assertFalse(isPackageBarcode("94987376861653"))
        assertFalse(isPackageBarcode("4987376861687"))
    }
}
