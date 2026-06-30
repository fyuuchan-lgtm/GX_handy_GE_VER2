package com.example.yakuzaiapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.yakuzaiapp.data.local.entity.AuditDrugPreference

@Dao
interface AuditDrugPreferenceDao {
    @Query("SELECT * FROM audit_drug_preference WHERE matchKey = :matchKey LIMIT 1")
    suspend fun findByMatchKey(matchKey: String): AuditDrugPreference?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(pref: AuditDrugPreference)

    @Query("DELETE FROM audit_drug_preference WHERE matchKey = :matchKey")
    suspend fun deleteByMatchKey(matchKey: String)

    @Query("SELECT * FROM audit_drug_preference ORDER BY selectedAt DESC LIMIT 100")
    suspend fun listRecent(): List<AuditDrugPreference>
}
