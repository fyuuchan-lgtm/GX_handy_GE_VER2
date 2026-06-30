package com.example.yakuzaiapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "audit_drug_preference")
data class AuditDrugPreference(
    @PrimaryKey val matchKey: String,
    val yjCode: String,
    val displayName: String,
    val selectCount: Int,
    val selectedAt: Long
)
