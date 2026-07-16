package com.example.yakuzaiapp.domain.dispensing

import com.example.yakuzaiapp.data.jahis.DrugCodeType

data class DispensingSession(
    val id: String,
    val createdAt: Long,
    val patientName: String?,
    val patientGender: String?,
    val patientBirthDate: String?,
    val dispensingDate: String?,
    val pharmacyName: String?,
    val prescribingHospital: String?,
    val doctorName: String?,
    val department: String?,
    val items: List<ExpectedDrugItem>
)

data class ExpectedDrugItem(
    val id: String,
    val rpNumber: Int,
    val drugCodeType: DrugCodeType,
    val drugCode: String?,
    val drugName: String,
    val quantity: String,
    val unit: String,
    val totalQuantityDisplay: String? = null,
    val matchedYjCode: String?,
    val matchedGtin: String?,
    val matchedDrugName: String?,
    val status: ItemStatus,
    val checkedAt: Long?,
    val isGeneric: Boolean
)

enum class ItemStatus {
    UNCHECKED,
    CONFIRMED,
    PACKING_MACHINE
}
