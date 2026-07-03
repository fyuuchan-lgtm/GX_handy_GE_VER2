package com.example.yakuzaiapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.yakuzaiapp.data.local.entity.FillHistory
import kotlinx.coroutines.flow.Flow

@Dao
interface FillHistoryDao {
    @Insert
    suspend fun insert(history: FillHistory): Long

    @Query("SELECT * FROM fill_history ORDER BY completedAt DESC")
    fun observeAll(): Flow<List<FillHistory>>
}
