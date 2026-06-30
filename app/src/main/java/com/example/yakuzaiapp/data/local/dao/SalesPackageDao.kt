package com.example.yakuzaiapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.yakuzaiapp.data.local.entity.SalesPackage

@Dao
interface SalesPackageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<SalesPackage>)

    @Query("SELECT * FROM sales_package WHERE gtin = :gtin")
    suspend fun findByGtin(gtin: String): SalesPackage?

    @Query("SELECT * FROM sales_package WHERE gtin = :code OR gtinSales = :code OR gtinCase = :code LIMIT 1")
    suspend fun findByAnyGtin(code: String): SalesPackage?

    @Query("SELECT * FROM sales_package WHERE gtinSales = :gtin LIMIT 1")
    suspend fun findBySalesPackageGtin(gtin: String): SalesPackage?

    @Query("SELECT * FROM sales_package WHERE gtinCase = :gtin LIMIT 1")
    suspend fun findByCaseGtin(gtin: String): SalesPackage?

    suspend fun findByGtinSales(gtin: String): SalesPackage? = findBySalesPackageGtin(gtin)

    suspend fun findByGtinCase(gtin: String): SalesPackage? = findByCaseGtin(gtin)

    @Query("SELECT * FROM sales_package WHERE yjCode = :yjCode")
    suspend fun findAllByYjCode(yjCode: String): List<SalesPackage>

    @Query("SELECT * FROM sales_package WHERE janCode = :janCode LIMIT 1")
    suspend fun findByJanCode(janCode: String): SalesPackage?

    @Query("SELECT COUNT(*) FROM sales_package")
    suspend fun count(): Int

    @Query("DELETE FROM sales_package")
    suspend fun deleteAll()
}
