package com.example.yakuzaiapp.repository

import com.example.yakuzaiapp.data.local.dao.SalesPackageDao
import com.example.yakuzaiapp.data.local.dao.DrugMasterDao
import com.example.yakuzaiapp.data.local.entity.DrugMaster
import kotlinx.coroutines.flow.Flow

interface DrugMasterLookup {
    suspend fun findByAnyGtin(code: String): DrugMaster?

    suspend fun findByGtin(gtin: String): DrugMaster? = findByAnyGtin(gtin)
}

class DrugMasterRepository(
    private val drugMasterDao: DrugMasterDao,
    private val salesPackageDao: SalesPackageDao,
) : DrugMasterLookup {
    fun observeAll(): Flow<List<DrugMaster>> = drugMasterDao.observeAll()
    fun searchByKeyword(keyword: String): Flow<List<DrugMaster>> = drugMasterDao.searchByKeyword(keyword)
    override suspend fun findByAnyGtin(code: String): DrugMaster? {
        val salesPackage = salesPackageDao.findByGtin(code)
            ?: salesPackageDao.findBySalesPackageGtin(code)
            ?: salesPackageDao.findByCaseGtin(code)
        if (salesPackage != null) {
            val yjCode = salesPackage.yjCode.trim()
            salesPackage.packageName
                ?.takeIf { it.isNotBlank() }
                ?.let { packageName ->
                    drugMasterDao.findByExactDrugOrPackageName(packageName)
                        .firstOrNull { it.matchesCode(yjCode) }
                        ?.let { return it }
                }
            if (yjCode.isNotBlank()) {
                drugMasterDao.findByYjCode(yjCode)?.let { return it }
            }
        }

        val fallback = drugMasterDao.findByAnyGtin(code)
        if (fallback != null) {
            return fallback
        }

        return null
    }
    override suspend fun findByGtin(gtin: String): DrugMaster? = findByAnyGtin(gtin)
    suspend fun findBySalesPackageGtin(gtin: String): DrugMaster? = drugMasterDao.findBySalesPackageGtin(gtin)
    suspend fun findByCaseGtin(gtin: String): DrugMaster? = drugMasterDao.findByCaseGtin(gtin)
    suspend fun count(): Int = drugMasterDao.count()
    suspend fun insertAll(items: List<DrugMaster>) = drugMasterDao.insertAll(items)

    private fun DrugMaster.matchesCode(code: String): Boolean {
        if (code.isBlank()) return false
        return yjCode == code || drugCode == code
    }
}
