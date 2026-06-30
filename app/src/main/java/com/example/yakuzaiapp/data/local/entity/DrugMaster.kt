package com.example.yakuzaiapp.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "drug_master",
    indices = [
        Index(value = ["yjCode"]),
        Index(value = ["gtin"]),
        Index(value = ["gtinSales"]),
        Index(value = ["gtinCase"]),
        Index(value = ["drugCode"]),
        Index(value = ["drugName"]),
        Index(value = ["packageName"])
    ]
)
data class DrugMaster(
    val drugCode: String,
    val drugName: String,
    @PrimaryKey val hot13: String,
    val gtin: String? = null,
    val maker: String? = null,
    val packageName: String? = null,
    val drugNameKana1: String? = null,
    val drugNameKana2: String? = null,
    val drugNameKana3: String? = null,
    val alias: String? = null,
    val packageSpec: String? = null,
    val quantity: Int? = null,
    val unit: String? = null,
    val price: Int? = null,
    val yjCode: String? = null,
    val janCode: String? = null,
    val gtinSales: String? = null,
    val gtinCase: String? = null,
    val drugCategory: String? = null,
    val dosageForm: String? = null,
    val packageForm: String? = null,
    val packageUnitCount: Int? = null,
    val packageUnitName: String? = null,
    val containerCount: Int? = null,
    val narcoticFlag: String? = null,
    val biologicalFlag: String? = null,
    val solventName: String? = null,
    val solventVolume: String? = null,
    val solventUnit: String? = null,
    val noticeDate: String? = null,
    val transitionDate: String? = null,
    val discontinuedDate: String? = null,
    val lastLotExpiry: String? = null,
    val medisUpdateDate: String? = null,
    val year: Int? = null,
    val updatedAt: Long = System.currentTimeMillis()
) {
    val needsWarning: Boolean
        get() = !narcoticFlag.isNullOrBlank() || !biologicalFlag.isNullOrBlank()

    val isDiscontinued: Boolean
        get() = !discontinuedDate.isNullOrBlank()

    val isInTransition: Boolean
        get() = !transitionDate.isNullOrBlank()

    val displayLabel: String
        get() = buildString {
            append(drugName)
            if (!packageSpec.isNullOrBlank()) {
                append(" / ")
                append(packageSpec)
            }
            if (!maker.isNullOrBlank()) {
                append(" / ")
                append(maker)
            }
        }
}
