package com.example.yakuzaiapp.util

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrialAvailabilityTest {
    @Test
    fun availableBeforeExpiry() {
        assertTrue(TrialAvailability.isAvailable(LocalDate.of(2026, 10, 30)))
    }

    @Test
    fun availableOnExpiryDate() {
        assertTrue(TrialAvailability.isAvailable(LocalDate.of(2026, 10, 31)))
    }

    @Test
    fun unavailableAfterExpiryDate() {
        assertFalse(TrialAvailability.isAvailable(LocalDate.of(2026, 11, 1)))
    }

    @Test
    fun expirationMessageUsesConfiguredExpiryDate() {
        assertEquals(
            "このテスト版の利用可能期間は2026年10月31日までです。",
            TrialAvailability.expirationMessage(),
        )
    }

    @Test
    fun activeCheckIntervalIsShortEnoughForForegroundExpiry() {
        assertTrue(TrialAvailability.activeCheckIntervalMillis <= 60_000L)
    }
}
