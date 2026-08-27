package com.example.yakuzaiapp.util

import java.time.LocalDate
import java.time.ZoneId

object TrialAvailability {
    val expiresOn: LocalDate = LocalDate.of(2026, 10, 31)
    const val activeCheckIntervalMillis: Long = 60_000L
    private val zoneId: ZoneId = ZoneId.of("Asia/Tokyo")

    fun isAvailable(today: LocalDate = LocalDate.now(zoneId)): Boolean {
        return !today.isAfter(expiresOn)
    }

    fun expirationMessage(): String {
        return "このテスト版の利用可能期間は${expiresOn.year}年${expiresOn.monthValue}月${expiresOn.dayOfMonth}日までです。"
    }
}
