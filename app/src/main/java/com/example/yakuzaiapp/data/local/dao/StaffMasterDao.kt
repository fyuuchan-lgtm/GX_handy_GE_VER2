package com.example.yakuzaiapp.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.yakuzaiapp.data.local.entity.StaffMaster
import kotlinx.coroutines.flow.Flow

@Dao
interface StaffMasterDao {
    @Query("SELECT * FROM staff_master ORDER BY staffKana IS NULL, staffKana, staffLastName, staffFirstName, staffName, staffId")
    fun observeAll(): Flow<List<StaffMaster>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(staff: StaffMaster)

    @Delete
    suspend fun delete(staff: StaffMaster)
}
