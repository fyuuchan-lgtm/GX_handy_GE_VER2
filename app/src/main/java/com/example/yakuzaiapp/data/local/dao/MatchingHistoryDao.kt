package com.example.yakuzaiapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.yakuzaiapp.data.local.entity.MatchingHistory

@Dao
interface MatchingHistoryDao {
    @Insert
    suspend fun insert(history: MatchingHistory): Long

    @Query("UPDATE matching_history SET completedAt = :completedAt, matchedItems = :matchedItems, status = :status WHERE id = :id")
    suspend fun finish(id: Long, completedAt: Long, matchedItems: Int, status: String)
}

