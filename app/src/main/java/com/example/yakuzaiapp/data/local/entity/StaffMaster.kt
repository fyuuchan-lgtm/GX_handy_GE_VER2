package com.example.yakuzaiapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "staff_master")
data class StaffMaster(
    @PrimaryKey val staffId: String,
    val staffName: String? = null,
    val staffLastName: String? = null,
    val staffFirstName: String? = null,
    val staffKana: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun displayName(): String {
        return listOfNotNull(
            staffLastName?.takeIf { it.isNotBlank() },
            staffFirstName?.takeIf { it.isNotBlank() }
        ).joinToString(" ").ifBlank {
            staffName?.takeIf { it.isNotBlank() } ?: "名称未設定"
        }
    }

    fun greetingName(): String? {
        return staffLastName?.takeIf { it.isNotBlank() }
            ?: staffName?.takeIf { it.isNotBlank() }?.substringBefore(" ")
    }
}
