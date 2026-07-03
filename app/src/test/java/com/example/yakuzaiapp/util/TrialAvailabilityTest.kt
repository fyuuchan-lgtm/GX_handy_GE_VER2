package com.example.yakuzaiapp.util

import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrialAvailabilityTest {
    @Test
    fun availableBeforeExpiry() {
        assertTrue(TrialAvailability.isAvailable(LocalDate.of(2026, 7, 30)))
    }

    @Test
    fun availableOnExpiryDate() {
        assertTrue(TrialAvailability.isAvailable(LocalDate.of(2026, 7, 31)))
    }

    @Test
    fun unavailableAfterExpiryDate() {
        assertFalse(TrialAvailability.isAvailable(LocalDate.of(2026, 8, 1)))
    }

    @Test
    fun activeCheckIntervalIsShortEnoughForForegroundExpiry() {
        assertTrue(TrialAvailability.activeCheckIntervalMillis <= 60_000L)
    }
}
