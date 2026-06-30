package com.example.yakuzaiapp.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sales_package",
    indices = [
        Index(value = ["gtin"]),
        Index(value = ["yjCode"]),
        Index(value = ["gtinSales"]),
        Index(value = ["gtinCase"]),
        Index(value = ["janCode"]),
    ]
)
data class SalesPackage(
    val gtin: String,
    val yjCode: String,
    val gtinSales: String? = null,
    val gtinCase: String? = null,
    val janCode: String? = null,
    val packageName: String? = null,
    val drugNameKana1: String? = null,
    val drugNameKana2: String? = null,
    val drugNameKana3: String? = null,
    val maker: String? = null,
    val packageSpec: String? = null,
    val drugCategory: String? = null,
    val dosageForm: String? = null,
    val packageForm: String? = null,
    val packageUnitCount: Int? = null,
    val packageUnitName: String? = null,
    val quantity: Int? = null,
    val unit: String? = null,
    val containerCount: Int? = null,
    val solventName: String? = null,
    val solventVolume: String? = null,
    val solventUnit: String? = null,
    val biologicalFlag: String? = null,
    val narcoticFlag: String? = null,
    val noticeDate: String? = null,
    val transitionDate: String? = null,
    val discontinuedDate: String? = null,
    val lastLotExpiry: String? = null,
    val medisUpdateDate: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    @PrimaryKey val packageKey: String = buildSalesPackageKey(gtin, gtinSales, gtinCase),
)

fun buildSalesPackageKey(
    gtin: String,
    gtinSales: String?,
    gtinCase: String?,
): String {
    return listOf(gtin, gtinSales.orEmpty(), gtinCase.orEmpty()).joinToString("|")
}
