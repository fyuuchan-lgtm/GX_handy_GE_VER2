package com.example.yakuzaiapp.repository

import com.example.yakuzaiapp.data.local.dao.DrugMasterDao
import com.example.yakuzaiapp.data.local.dao.SalesPackageDao
import com.example.yakuzaiapp.data.local.entity.DrugMaster
import com.example.yakuzaiapp.data.local.entity.SalesPackage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DrugMasterRepositoryTest {
    @Test
    fun findsMasterViaSalesPackageGtin() = runTest {
        val master = drugMaster(yjCode = "1290401G1021", hot13 = "hot-1", drugName = "Amvuttra")
        val repository = createRepository(
            salesPackage = salesPackage(
                gtin = "04987934648347",
                yjCode = "1290401G1021",
            ),
            masterByYjCode = master
        )

        val result = repository.findByAnyGtin("04987934648347")

        assertEquals(master, result)
    }

    @Test
    fun findsMasterViaSalesPackageGtinSales() = runTest {
        val master = drugMaster(yjCode = "1290401G1021", hot13 = "hot-1", drugName = "Amvuttra")
        val repository = createRepository(
            salesPackageBySales = salesPackage(
                gtin = "00000000000000",
                gtinSales = "14987934409693",
                yjCode = "1290401G1021",
            ),
            masterByYjCode = master
        )

        val result = repository.findByAnyGtin("14987934409693")

        assertEquals(master, result)
    }

    @Test
    fun findsMasterViaSalesPackageGtinCase() = runTest {
        val master = drugMaster(yjCode = "1290401G1021", hot13 = "hot-1", drugName = "Amvuttra")
        val repository = createRepository(
            salesPackageByCase = salesPackage(
                gtin = "00000000000000",
                gtinCase = "24987934409690",
                yjCode = "1290401G1021",
            ),
            masterByYjCode = master
        )

        val result = repository.findByAnyGtin("24987934409690")

        assertEquals(master, result)
    }

    @Test
    fun bridgesSalesPackagePriceCodeToIndividualYjByPackageName() = runTest {
        val master = drugMaster(
            yjCode = "2354003F2464",
            hot13 = "hot-sennoside",
            drugCode = "2354003F2014",
            drugName = "センノシド錠１２ｍｇ「ＮＩＧ」",
            packageName = "センノシド錠１２ｍｇ「ＮＩＧ」"
        )
        val repository = createRepository(
            salesPackage = salesPackage(
                gtin = "04987376861687",
                yjCode = "2354003F2014",
                packageName = "センノシド錠１２ｍｇ「ＮＩＧ」"
            ),
            masterByExactDrugOrPackageName = master
        )

        val result = repository.findByAnyGtin("04987376861687")

        assertEquals(master, result)
    }

    @Test
    fun prefersPackageNameMatchedIndividualWhenPriceCodeHasMultipleMasters() = runTest {
        val wrongMaster = drugMaster(
            yjCode = "2354003F2015",
            hot13 = "hot-wrong",
            drugCode = "2354003F2014",
            drugName = "センノシド錠１２ｍｇ「別メーカー」",
            packageName = "センノシド錠１２ｍｇ「別メーカー」"
        )
        val targetMaster = drugMaster(
            yjCode = "2354003F2464",
            hot13 = "hot-target",
            drugCode = "2354003F2014",
            drugName = "センノシド錠１２ｍｇ「ＮＩＧ」",
            packageName = "センノシド錠１２ｍｇ「ＮＩＧ」"
        )
        val repository = createRepository(
            salesPackage = salesPackage(
                gtin = "04987376861687",
                yjCode = "2354003F2014",
                packageName = "センノシド錠１２ｍｇ「ＮＩＧ」"
            ),
            exactDrugOrPackageNameMatches = listOf(wrongMaster, targetMaster),
            masterByYjCode = wrongMaster
        )

        val result = repository.findByAnyGtin("04987376861687")

        assertEquals(targetMaster, result)
    }

    @Test
    fun fallsBackToHot13WhenNoSalesPackageMatch() = runTest {
        val master = drugMaster(yjCode = "1290401G1021", hot13 = "04987934648347", drugName = "Amvuttra")
        val repository = createRepository(
            masterByHot13 = master
        )

        val result = repository.findByAnyGtin("04987934648347")

        assertEquals(master, result)
    }

    @Test
    fun returnsNullWhenNoMatch() = runTest {
        val repository = createRepository()

        val result = repository.findByAnyGtin("00000000000000")

        assertNull(result)
    }

    private fun createRepository(
        salesPackage: SalesPackage? = null,
        salesPackageBySales: SalesPackage? = null,
        salesPackageByCase: SalesPackage? = null,
        masterByYjCode: DrugMaster? = null,
        masterByHot13: DrugMaster? = null,
        masterByExactDrugOrPackageName: DrugMaster? = null,
        exactDrugOrPackageNameMatches: List<DrugMaster> =
            listOfNotNull(masterByExactDrugOrPackageName),
    ): DrugMasterRepository {
        val salesDao = object : SalesPackageDao {
            override suspend fun upsertAll(items: List<SalesPackage>) = Unit
            override suspend fun findByGtin(gtin: String): SalesPackage? =
                salesPackage?.takeIf { it.gtin == gtin }
                    ?: salesPackageBySales?.takeIf { it.gtin == gtin }
                    ?: salesPackageByCase?.takeIf { it.gtin == gtin }

            override suspend fun findByAnyGtin(code: String): SalesPackage? = when {
                salesPackage != null && salesPackage.gtin == code -> salesPackage
                salesPackageBySales != null && salesPackageBySales.gtin == code -> salesPackageBySales
                salesPackageByCase != null && salesPackageByCase.gtin == code -> salesPackageByCase
                salesPackageBySales != null && salesPackageBySales.gtinSales == code -> salesPackageBySales
                salesPackageByCase != null && salesPackageByCase.gtinCase == code -> salesPackageByCase
                else -> null
            }

            override suspend fun findAllByYjCode(yjCode: String): List<SalesPackage> = emptyList()
            override suspend fun findByJanCode(janCode: String): SalesPackage? = null
            override suspend fun count(): Int = 0
            override suspend fun deleteAll() = Unit

            override suspend fun findBySalesPackageGtin(gtin: String): SalesPackage? =
                salesPackageBySales?.takeIf { it.gtinSales == gtin }

            override suspend fun findByCaseGtin(gtin: String): SalesPackage? =
                salesPackageByCase?.takeIf { it.gtinCase == gtin }
        }

        val drugDao = object : DrugMasterDao {
            override suspend fun insertAll(items: List<DrugMaster>) = Unit
            override suspend fun upsertAll(items: List<DrugMaster>) = Unit
            override suspend fun deleteAll() = Unit
            override fun observeAll() = kotlinx.coroutines.flow.flowOf(emptyList<DrugMaster>())
            override fun searchByKeyword(keyword: String) = kotlinx.coroutines.flow.flowOf(emptyList<DrugMaster>())
            override suspend fun findByExactName(name: String): List<DrugMaster> = emptyList()
            override suspend fun findByExactDrugOrPackageName(name: String): List<DrugMaster> =
                exactDrugOrPackageNameMatches.filter { it.drugName == name || it.packageName == name }
            override suspend fun findByCore(core: String): List<DrugMaster> = emptyList()
            override suspend fun findByCoreNormalized(
                core: String,
                coreFullWidth: String,
                coreSmallKanaNormalized: String
            ): List<DrugMaster> =
                emptyList()
            override suspend fun findAllForLevenshtein(limit: Int): List<DrugMaster> = emptyList()
            override suspend fun findByGtin(gtin: String): DrugMaster? =
                when {
                    masterByHot13?.hot13 == gtin || masterByHot13?.gtin == gtin -> masterByHot13
                    masterByYjCode?.hot13 == gtin || masterByYjCode?.gtin == gtin -> masterByYjCode
                    else -> null
                }
            override suspend fun findByAnyGtin(code: String): DrugMaster? = findByGtin(code)
            override suspend fun findByYjCode(yjCode: String): DrugMaster? =
                when {
                    masterByYjCode?.yjCode == yjCode || masterByYjCode?.drugCode == yjCode -> masterByYjCode
                    masterByHot13?.yjCode == yjCode || masterByHot13?.drugCode == yjCode -> masterByHot13
                    else -> null
                }
            override suspend fun findBySalesPackageGtin(gtin: String): DrugMaster? = null
            override suspend fun findByCaseGtin(gtin: String): DrugMaster? = null
            override suspend fun count(): Int = 0
        }

        return DrugMasterRepository(drugDao, salesDao)
    }

    private fun salesPackage(
        gtin: String,
        yjCode: String,
        gtinSales: String? = null,
        gtinCase: String? = null,
        packageName: String = "Package",
    ): SalesPackage {
        return SalesPackage(
            gtin = gtin,
            yjCode = yjCode,
            gtinSales = gtinSales,
            gtinCase = gtinCase,
            janCode = null,
            packageName = packageName,
        )
    }

    private fun drugMaster(
        yjCode: String,
        hot13: String,
        drugCode: String = yjCode,
        drugName: String,
        packageName: String? = null,
    ): DrugMaster {
        return DrugMaster(
            hot13 = hot13,
            drugCode = drugCode,
            drugName = drugName,
            packageName = packageName,
            yjCode = yjCode,
            gtin = hot13,
        )
    }
}
