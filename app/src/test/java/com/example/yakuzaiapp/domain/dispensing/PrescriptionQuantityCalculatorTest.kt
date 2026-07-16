package com.example.yakuzaiapp.domain.dispensing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PrescriptionQuantityCalculatorTest {
    @Test
    fun oralMultipliesDailyAmountByDays() {
        assertEquals(
            "21錠",
            PrescriptionQuantityCalculator.calculate("3", "錠", "7", "1")
        )
    }

    @Test
    fun asNeededMultipliesSingleAmountByNumberOfDoses() {
        assertEquals(
            "10錠",
            PrescriptionQuantityCalculator.calculate("2", "錠", "5", "3")
        )
    }

    @Test
    fun externalUsesRecordedTotalWithoutMultiplication() {
        assertEquals(
            "5g",
            PrescriptionQuantityCalculator.calculate("5", "g", "1", "5")
        )
        assertEquals(
            "21枚",
            PrescriptionQuantityCalculator.calculate("21", "枚", "1", "5")
        )
    }

    @Test
    fun unknownOrInvalidInputFallsBackToOriginalDisplay() {
        assertNull(PrescriptionQuantityCalculator.calculate("1", "錠", "7", null))
        assertNull(PrescriptionQuantityCalculator.calculate("適量", "g", "1", "5"))
        assertNull(PrescriptionQuantityCalculator.calculate("1", "錠", null, "1"))
    }
}
