package com.example.yakuzaiapp.domain.dispensing

import java.math.BigDecimal

object PrescriptionQuantityCalculator {
    private const val ORAL = "1"
    private const val AS_NEEDED = "3"
    private const val EXTERNAL = "5"

    fun calculate(
        quantity: String,
        unit: String,
        dispensingQuantity: String?,
        dosageFormCode: String?
    ): String? {
        val drugAmount = quantity.toDecimalOrNull() ?: return null
        val total = when (dosageFormCode) {
            ORAL, AS_NEEDED -> {
                val multiplier = dispensingQuantity.toDecimalOrNull() ?: return null
                drugAmount.multiply(multiplier)
            }
            EXTERNAL -> drugAmount
            else -> return null
        }
        return "${total.toPlainDisplay()}$unit"
    }

    private fun String?.toDecimalOrNull(): BigDecimal? =
        this
            ?.trim()
            ?.let { runCatching { BigDecimal(it) }.getOrNull() }
            ?.takeIf { it.signum() >= 0 }

    private fun BigDecimal.toPlainDisplay(): String =
        stripTrailingZeros().toPlainString()
}
