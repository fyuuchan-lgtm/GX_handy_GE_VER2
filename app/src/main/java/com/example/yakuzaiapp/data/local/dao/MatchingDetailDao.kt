package com.example.yakuzaiapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import com.example.yakuzaiapp.data.local.entity.MatchingDetail

@Dao
interface MatchingDetailDao {
    @Insert
    suspend fun insertAll(items: List<MatchingDetail>)
}

