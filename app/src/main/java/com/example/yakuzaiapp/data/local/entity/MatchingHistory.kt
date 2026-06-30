package com.example.yakuzaiapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "matching_history")
data class MatchingHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val completedAt: Long? = null,
    val staffId: String? = null,
    val imageFilePath: String? = null,
    val totalItems: Int,
    val matchedItems: Int,
    val status: String
)

