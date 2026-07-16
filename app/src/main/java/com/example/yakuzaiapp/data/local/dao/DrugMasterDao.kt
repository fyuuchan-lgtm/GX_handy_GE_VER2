package com.example.yakuzaiapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.yakuzaiapp.data.local.entity.DrugMaster
import kotlinx.coroutines.flow.Flow

@Dao
interface DrugMasterDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<DrugMaster>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<DrugMaster>)

    @Query("DELETE FROM drug_master")
    suspend fun deleteAll()

    @Query("DELETE FROM drug_master WHERE isUserRegistered = 0")
    suspend fun deleteImported()

    @Query("DELETE FROM drug_master WHERE hot13 = :hot13 AND isUserRegistered = 1")
    suspend fun deleteUserRegistered(hot13: String)

    @Query("SELECT * FROM drug_master WHERE isUserRegistered = 1 ORDER BY drugName ASC")
    fun observeUserRegistered(): Flow<List<DrugMaster>>

    @Query("SELECT * FROM drug_master ORDER BY drugName ASC")
    fun observeAll(): Flow<List<DrugMaster>>

    @Query(
        """
        SELECT * FROM drug_master
        WHERE drugName LIKE '%' || :keyword || '%'
           OR IFNULL(packageName, '') LIKE '%' || :keyword || '%'
           OR IFNULL(drugNameKana1, '') LIKE '%' || :keyword || '%'
           OR IFNULL(drugNameKana2, '') LIKE '%' || :keyword || '%'
           OR IFNULL(drugNameKana3, '') LIKE '%' || :keyword || '%'
        ORDER BY drugName ASC,
                 CASE WHEN IFNULL(packageName, '') LIKE '%ＰＴＰ%' OR IFNULL(packageName, '') LIKE '%PTP%' THEN 0 ELSE 1 END ASC,
                 packageName ASC
        LIMIT 500
        """
    )
    fun searchByKeyword(keyword: String): Flow<List<DrugMaster>>

    @Query("SELECT * FROM drug_master WHERE drugName = :name LIMIT 5")
    suspend fun findByExactName(name: String): List<DrugMaster>

    @Query(
        """
        SELECT * FROM drug_master
        WHERE drugName = :name
           OR IFNULL(packageName, '') = :name
        LIMIT 20
        """
    )
    suspend fun findByExactDrugOrPackageName(name: String): List<DrugMaster>

    @Query(
        """
        SELECT * FROM drug_master
        WHERE drugName LIKE '%' || :core || '%'
           OR IFNULL(packageName, '') LIKE '%' || :core || '%'
           OR IFNULL(drugNameKana1, '') LIKE '%' || :core || '%'
           OR IFNULL(drugNameKana2, '') LIKE '%' || :core || '%'
           OR IFNULL(drugNameKana3, '') LIKE '%' || :core || '%'
           OR IFNULL(alias, '') LIKE '%' || :core || '%'
        ORDER BY drugName ASC
        LIMIT 20
        """
    )
    suspend fun findByCore(core: String): List<DrugMaster>

    @Query(
        """
        SELECT * FROM drug_master
        WHERE drugName LIKE '%' || :core || '%'
           OR drugName LIKE '%' || :coreFullWidth || '%'
           OR drugName LIKE '%' || :coreSmallKanaNormalized || '%'
           OR IFNULL(packageName, '') LIKE '%' || :core || '%'
           OR IFNULL(packageName, '') LIKE '%' || :coreFullWidth || '%'
           OR IFNULL(packageName, '') LIKE '%' || :coreSmallKanaNormalized || '%'
           OR IFNULL(drugNameKana1, '') LIKE '%' || :core || '%'
           OR IFNULL(drugNameKana1, '') LIKE '%' || :coreSmallKanaNormalized || '%'
           OR IFNULL(drugNameKana2, '') LIKE '%' || :core || '%'
           OR IFNULL(drugNameKana2, '') LIKE '%' || :coreSmallKanaNormalized || '%'
           OR IFNULL(drugNameKana3, '') LIKE '%' || :core || '%'
           OR IFNULL(drugNameKana3, '') LIKE '%' || :coreSmallKanaNormalized || '%'
           OR IFNULL(alias, '') LIKE '%' || :core || '%'
           OR IFNULL(alias, '') LIKE '%' || :coreSmallKanaNormalized || '%'
        ORDER BY drugName ASC
        LIMIT 500
        """
    )
    suspend fun findByCoreNormalized(
        core: String,
        coreFullWidth: String,
        coreSmallKanaNormalized: String
    ): List<DrugMaster>

    @Query("SELECT * FROM drug_master ORDER BY drugName ASC LIMIT :limit")
    suspend fun findAllForLevenshtein(limit: Int = 5000): List<DrugMaster>

    @Query(
        """
        SELECT * FROM drug_master
        WHERE hot13 = :gtin
           OR gtin = :gtin
        LIMIT 1
        """
    )
    suspend fun findByGtin(gtin: String): DrugMaster?

    @Query(
        """
        SELECT * FROM drug_master
        WHERE hot13 = :code
           OR gtin = :code
           OR gtinSales = :code
           OR gtinCase = :code
        LIMIT 1
        """
    )
    suspend fun findByAnyGtin(code: String): DrugMaster?

    @Query(
        """
        SELECT * FROM drug_master
        WHERE REPLACE(REPLACE(UPPER(TRIM(IFNULL(yjCode, ''))), '-', ''), ' ', '')
              = REPLACE(REPLACE(UPPER(TRIM(:yjCode)), '-', ''), ' ', '')
           OR REPLACE(REPLACE(UPPER(TRIM(IFNULL(drugCode, ''))), '-', ''), ' ', '')
              = REPLACE(REPLACE(UPPER(TRIM(:yjCode)), '-', ''), ' ', '')
        LIMIT 1
        """
    )
    suspend fun findByYjCode(yjCode: String): DrugMaster?

    @Query("SELECT * FROM drug_master WHERE gtinSales = :gtin LIMIT 1")
    suspend fun findBySalesPackageGtin(gtin: String): DrugMaster?

    @Query("SELECT * FROM drug_master WHERE gtinCase = :gtin LIMIT 1")
    suspend fun findByCaseGtin(gtin: String): DrugMaster?

    suspend fun findByGtinSales(gtin: String): DrugMaster? = findBySalesPackageGtin(gtin)

    suspend fun findByGtinCase(gtin: String): DrugMaster? = findByCaseGtin(gtin)

    @Query("SELECT COUNT(*) FROM drug_master")
    suspend fun count(): Int
}
