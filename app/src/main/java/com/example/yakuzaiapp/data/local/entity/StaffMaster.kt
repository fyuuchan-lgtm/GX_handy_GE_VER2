package com.example.yakuzaiapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "staff_master")
data class StaffMaster(
    @PrimaryKey val staffId: String,
    val staffName: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

