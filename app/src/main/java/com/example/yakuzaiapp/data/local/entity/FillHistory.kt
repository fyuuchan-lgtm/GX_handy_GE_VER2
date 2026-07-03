package com.example.yakuzaiapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "fill_history")
data class FillHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val completedAt: Long,
    val staffId: String,
    val staffName: String?,
    val staffKana: String?,
    val drugName: String,
    val yjCode: String?,
    val sourceGtin: String?,
    val targetCode: String,
    val expirationDate: String?,
    val status: String = "OK"
)
