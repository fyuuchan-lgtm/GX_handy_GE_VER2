package com.example.yakuzaiapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "drug_preference")
data class DrugPreference(
    @PrimaryKey val yjCode: String,
    val defaultPackingMachine: Boolean,
    val updatedAt: Long
)
