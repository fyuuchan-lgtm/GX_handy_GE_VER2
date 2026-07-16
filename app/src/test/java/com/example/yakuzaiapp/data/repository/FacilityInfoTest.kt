package com.example.yakuzaiapp.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FacilityInfoTest {
    @Test
    fun isRegistered_requiresMedicalFacilityCoreFields() {
        assertFalse(FacilityInfo().isRegistered)
        assertFalse(
            FacilityInfo(
                name = "テスト薬局",
                postalCode = "0000000",
                prefecture = "東京都"
            ).isRegistered
        )
        assertTrue(
            FacilityInfo(
                name = "テスト薬局",
                postalCode = "0000000",
                prefecture = "東京都",
                city = "千代田区"
            ).isRegistered
        )
    }
}
