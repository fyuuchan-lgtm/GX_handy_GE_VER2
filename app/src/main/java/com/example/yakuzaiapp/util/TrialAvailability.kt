package com.example.yakuzaiapp.util

import java.time.LocalDate
import java.time.ZoneId

object TrialAvailability {
    val expiresOn: LocalDate = LocalDate.of(2026, 7, 31)
    const val activeCheckIntervalMillis: Long = 60_000L
    private val zoneId: ZoneId = ZoneId.of("Asia/Tokyo")

    fun isAvailable(today: LocalDate = LocalDate.now(zoneId)): Boolean {
        return !today.isAfter(expiresOn)
    }
}
