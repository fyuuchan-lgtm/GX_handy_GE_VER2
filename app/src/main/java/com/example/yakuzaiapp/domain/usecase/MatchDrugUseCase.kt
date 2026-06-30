package com.example.yakuzaiapp.domain.usecase

import com.example.yakuzaiapp.data.local.entity.DrugMaster

data class MatchResult(
    val matched: Boolean,
    val reason: String
)

class MatchDrugUseCase {
    fun execute(expected: DrugMaster?, scannedGtin: String?): MatchResult {
        if (expected == null) return MatchResult(false, "master_not_found")
        if (scannedGtin.isNullOrBlank()) return MatchResult(false, "gtin_missing")
        val expectedGtin = expected.gtin ?: expected.hot13
        val matched = normalize(expectedGtin) == normalize(scannedGtin)
        return if (matched) MatchResult(true, "gtin_match") else MatchResult(false, "gtin_mismatch")
    }

    private fun normalize(value: String): String = value.filter(Char::isDigit).padStart(14, '0').takeLast(14)
}
