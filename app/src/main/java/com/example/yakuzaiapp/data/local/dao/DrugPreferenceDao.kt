package com.example.yakuzaiapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.yakuzaiapp.data.local.entity.DrugPreference

@Dao
interface DrugPreferenceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(pref: DrugPreference)

    @Query("SELECT * FROM drug_preference WHERE yjCode = :yjCode")
    suspend fun findByYjCode(yjCode: String): DrugPreference?

    @Query("SELECT * FROM drug_preference WHERE defaultPackingMachine = 1")
    suspend fun getAllPackingMachineDrugs(): List<DrugPreference>

    @Query("DELETE FROM drug_preference WHERE yjCode = :yjCode")
    suspend fun deleteByYjCode(yjCode: String)

    @Query("SELECT COUNT(*) FROM drug_preference")
    suspend fun count(): Int
}
