package com.example.yakuzaiapp.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "matching_detail",
    foreignKeys = [ForeignKey(
        entity = MatchingHistory::class,
        parentColumns = ["id"],
        childColumns = ["historyId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class MatchingDetail(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(index = true) val historyId: Long,
    val drugCode: String? = null,
    val drugName: String,
    val expectedGtin: String,
    val scannedGtin: String? = null,
    val result: String,
    val scannedAt: Long? = null
)

