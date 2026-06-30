package com.example.yakuzaiapp.domain.dispensing

import com.example.yakuzaiapp.data.jahis.DrugCodeType
import com.example.yakuzaiapp.data.jahis.JahisDrug
import com.example.yakuzaiapp.data.jahis.JahisPrescription
import com.example.yakuzaiapp.data.jahis.JahisRp
import com.example.yakuzaiapp.data.local.dao.DrugMasterDao
import com.example.yakuzaiapp.data.local.dao.DrugPreferenceDao
import com.example.yakuzaiapp.data.local.entity.DrugMaster
import com.example.yakuzaiapp.data.local.entity.DrugPreference
import com.example.yakuzaiapp.data.repository.DrugPreferenceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExpectedListBuilderTest {
    private companion object {
        const val BIASPIRIN_MASTER_NAME = "\u30D0\u30A4\u30A2\u30B9\u30D4\u30EA\u30F3\u9320\uFF11\uFF10\uFF10\uFF4D\uFF47"
        const val BIASPIRIN_MASTER_PACKAGE = "\u30D0\u30A4\u30A2\u30B9\u30D4\u30EA\u30F3\u9320\uFF11\uFF10\uFF10\uFF4D\uFF47 \uFF30\uFF34\uFF30 10\u9320"
        const val BIASPIRIN_QUERY_NAME = "\u30D0\u30A4\u30A2\u30B9\u30D4\u30EA\u30F3\u9320100mg"
        const val ROSUVASTATIN_MASTER_NAME = "\u30ED\u30B9\u30D0\u30B9\u30BF\u30C1\u30F3\uFF2F\uFF24\u9320\uFF12\uFF0E\uFF15\uFF4D\uFF47\u300C\u660E\u6CBB\u300D"
        const val ROSUVASTATIN_MASTER_PACKAGE = "\u30ED\u30B9\u30D0\u30B9\u30BF\u30C1\u30F3\uFF2F\uFF24\u9320\uFF12\uFF0E\uFF15\uFF4D\uFF47\u300C\u660E\u6CBB\u300D \uFF30\uFF34\uFF30 10\u9320"
        const val ROSUVASTATIN_QUERY_NAME = "\u30ED\u30B9\u30D0\u30B9\u30BF\u30C1\u30F3OD\u93202.5mg"
    }

    @Test
    fun yjCodeMatchSetsMatchedMasterFields() = runTest {
        val builder = ExpectedListBuilder(
            drugMasterDao = fakeDrugMasterDao(
                drugMaster("2171019F1024", "04987123456789", "Amlodipine Tablet 5mg")
            ),
            drugPreferenceRepository = DrugPreferenceRepository(fakeDrugPreferenceDao())
        )

        val session = builder.build(
            prescriptionOf(
                JahisDrug(
                    rpNumber = 1,
                    drugCodeType = DrugCodeType.YJ,
                    drugCode = "2171019F1024",
                    drugName = "Amlodipine QR Name",
                    quantity = "2",
                    unit = "TAB"
                )
            )
        )

        val item = session.items.single()
        assertEquals("2171019F1024", item.matchedYjCode)
        assertEquals("04987123456789", item.matchedGtin)
        assertEquals("Amlodipine Tablet 5mg", item.matchedDrugName)
        assertEquals(ItemStatus.UNCHECKED, item.status)
    }

    @Test
    fun missingYjCodeKeepsMasterFieldsNull() = runTest {
        val builder = ExpectedListBuilder(
            drugMasterDao = fakeDrugMasterDao(
                drugMaster("2171019F1024", "04987123456789", "Amlodipine Tablet 5mg")
            ),
            drugPreferenceRepository = DrugPreferenceRepository(fakeDrugPreferenceDao())
        )

        val session = builder.build(
            prescriptionOf(
                JahisDrug(
                    rpNumber = 1,
                    drugCodeType = DrugCodeType.YJ,
                    drugCode = "9999999Z9999",
                    drugName = "Unknown Drug",
                    quantity = "1",
                    unit = "TAB"
                )
            )
        )

        val item = session.items.single()
        assertNull(item.matchedYjCode)
        assertNull(item.matchedGtin)
        assertNull(item.matchedDrugName)
        assertEquals(ItemStatus.UNCHECKED, item.status)
    }

    @Test
    fun yjLookupFallsBackToNameSearchWhenExactMatchMisses() = runTest {
        val master = drugMaster("2171019F1024", "04987123456789", "Amlodipine Tablet 5mg")
        val builder = ExpectedListBuilder(
            drugMasterDao = object : DrugMasterDao {
                override suspend fun insertAll(items: List<DrugMaster>) = Unit
                override suspend fun upsertAll(items: List<DrugMaster>) = Unit
                override suspend fun deleteAll() = Unit
                override fun observeAll(): Flow<List<DrugMaster>> = flowOf(listOf(master))
                override fun searchByKeyword(keyword: String): Flow<List<DrugMaster>> = flowOf(listOf(master))
                override suspend fun findByExactName(name: String): List<DrugMaster> = emptyList()
                override suspend fun findByExactDrugOrPackageName(name: String): List<DrugMaster> = emptyList()
                override suspend fun findByCore(core: String): List<DrugMaster> = emptyList()
                override suspend fun findByCoreNormalized(
                    core: String,
                    coreFullWidth: String,
                    coreSmallKanaNormalized: String
                ): List<DrugMaster> = emptyList()
                override suspend fun findAllForLevenshtein(limit: Int): List<DrugMaster> = emptyList()
                override suspend fun findByAnyGtin(code: String): DrugMaster? = null
                override suspend fun findByGtin(gtin: String): DrugMaster? = null
                override suspend fun findByYjCode(yjCode: String): DrugMaster? = null
                override suspend fun findBySalesPackageGtin(gtin: String): DrugMaster? = null
                override suspend fun findByCaseGtin(gtin: String): DrugMaster? = null
                override suspend fun count(): Int = 1
            },
            drugPreferenceRepository = DrugPreferenceRepository(fakeDrugPreferenceDao())
        )

        val session = builder.build(
            prescriptionOf(
                JahisDrug(
                    rpNumber = 1,
                    drugCodeType = DrugCodeType.YJ,
                    drugCode = "2171019F1024",
                    drugName = "Amlodipine Tablet 5mg",
                    quantity = "2",
                    unit = "TAB"
                )
            )
        )

        val item = session.items.single()
        assertEquals("2171019F1024", item.matchedYjCode)
        assertEquals("04987123456789", item.matchedGtin)
        assertEquals("Amlodipine Tablet 5mg", item.matchedDrugName)
    }

    @Test
    fun mhlwCodeMatchesDrugMasterByCode() = runTest {
        val builder = expectedListBuilderWithMaster(
            drugMaster(
                yjCode = "3399007H1013",
                gtin = "04987341303075",
                drugName = BIASPIRIN_MASTER_NAME,
                packageName = BIASPIRIN_MASTER_PACKAGE
            )
        )

        val item = buildSingleItem(
            builder = builder,
            drug = JahisDrug(
                rpNumber = 1,
                drugCodeType = DrugCodeType.MHLW,
                drugCode = "3399007H1013",
                drugName = BIASPIRIN_QUERY_NAME,
                quantity = "1",
                unit = "TAB"
            )
        )

        assertEquals("3399007H1013", item.matchedYjCode)
        assertEquals("04987341303075", item.matchedGtin)
        assertEquals(BIASPIRIN_MASTER_NAME, item.matchedDrugName)
    }

    @Test
    fun yjCodeMatchesDrugMasterByCode() = runTest {
        val builder = expectedListBuilderWithMaster(
            drugMaster(
                yjCode = "3399007H1013",
                gtin = "04987341303075",
                drugName = BIASPIRIN_MASTER_NAME,
                packageName = BIASPIRIN_MASTER_PACKAGE
            )
        )

        val item = buildSingleItem(
            builder = builder,
            drug = JahisDrug(
                rpNumber = 1,
                drugCodeType = DrugCodeType.YJ,
                drugCode = "3399007H1013",
                drugName = BIASPIRIN_QUERY_NAME,
                quantity = "1",
                unit = "TAB"
            )
        )

        assertEquals("3399007H1013", item.matchedYjCode)
        assertEquals("04987341303075", item.matchedGtin)
    }

    @Test
    fun unknownCodeTypeStillMatchesDrugMasterByCode() = runTest {
        val builder = expectedListBuilderWithMaster(
            drugMaster(
                yjCode = "3399007H1013",
                gtin = "04987341303075",
                drugName = BIASPIRIN_MASTER_NAME,
                packageName = BIASPIRIN_MASTER_PACKAGE
            )
        )

        val item = buildSingleItem(
            builder = builder,
            drug = JahisDrug(
                rpNumber = 1,
                drugCodeType = DrugCodeType.UNKNOWN,
                drugCode = "3399007H1013",
                drugName = BIASPIRIN_QUERY_NAME,
                quantity = "1",
                unit = "TAB"
            )
        )

        assertEquals("3399007H1013", item.matchedYjCode)
        assertEquals("04987341303075", item.matchedGtin)
    }

    @Test
    fun nameSearchMatchesFullWidthDrugName() = runTest {
        val builder = expectedListBuilderWithMaster(
            drugMaster(
                yjCode = "3399007H1013",
                gtin = "04987341303075",
                drugName = BIASPIRIN_MASTER_NAME,
                packageName = BIASPIRIN_MASTER_PACKAGE
            )
        )

        val item = buildSingleItem(
            builder = builder,
            drug = JahisDrug(
                rpNumber = 1,
                drugCodeType = DrugCodeType.UNKNOWN,
                drugCode = "",
                drugName = BIASPIRIN_QUERY_NAME,
                quantity = "1",
                unit = "TAB"
            )
        )

        assertEquals("3399007H1013", item.matchedYjCode)
        assertEquals("04987341303075", item.matchedGtin)
        assertEquals(BIASPIRIN_MASTER_NAME, item.matchedDrugName)
    }

    @Test
    fun nameSearchStripsBrandSuffixes() = runTest {
        val builder = expectedListBuilderWithMaster(
            drugMaster(
                yjCode = "2189017F3017",
                gtin = "04987916250544",
                drugName = ROSUVASTATIN_MASTER_NAME,
                packageName = ROSUVASTATIN_MASTER_PACKAGE
            )
        )

        val item = buildSingleItem(
            builder = builder,
            drug = JahisDrug(
                rpNumber = 1,
                drugCodeType = DrugCodeType.UNKNOWN,
                drugCode = "",
                drugName = ROSUVASTATIN_QUERY_NAME,
                quantity = "1",
                unit = "TAB"
            )
        )

        assertEquals("2189017F3017", item.matchedYjCode)
        assertEquals("04987916250544", item.matchedGtin)
        assertEquals(ROSUVASTATIN_MASTER_NAME, item.matchedDrugName)
    }

    private fun prescriptionOf(drug: JahisDrug): JahisPrescription {
        return JahisPrescription(
            version = "JAHISTC02",
            patientName = "Patient",
            patientGender = "1",
            patientBirthDate = "19800101",
            dispensingDate = "20260606",
            pharmacyName = "Pharmacy",
            prescribingHospital = "Hospital",
            doctorName = null,
            department = null,
            rps = listOf(JahisRp(rpNumber = drug.rpNumber, drugs = listOf(drug), usage = null))
        )
    }

    private suspend fun buildSingleItem(builder: ExpectedListBuilder, drug: JahisDrug): ExpectedDrugItem {
        val session = builder.build(prescriptionOf(drug))
        return session.items.single()
    }

    private fun expectedListBuilderWithMaster(master: DrugMaster): ExpectedListBuilder {
        return ExpectedListBuilder(
            drugMasterDao = fakeDrugMasterDao(master),
            drugPreferenceRepository = DrugPreferenceRepository(fakeDrugPreferenceDao())
        )
    }

    private fun drugMaster(
        yjCode: String,
        gtin: String,
        drugName: String,
        packageName: String? = null
    ): DrugMaster {
        return DrugMaster(
            hot13 = gtin,
            drugCode = yjCode,
            drugName = drugName,
            yjCode = yjCode,
            gtin = gtin,
            packageName = packageName
        )
    }

    private fun fakeDrugMasterDao(vararg masters: DrugMaster): DrugMasterDao {
        return object : DrugMasterDao {
            override suspend fun insertAll(items: List<DrugMaster>) = Unit
            override suspend fun upsertAll(items: List<DrugMaster>) = Unit
            override suspend fun deleteAll() = Unit
            override fun observeAll(): Flow<List<DrugMaster>> = flowOf(masters.toList())
            override fun searchByKeyword(keyword: String): Flow<List<DrugMaster>> = flowOf(masters.toList())
            override suspend fun findByExactName(name: String): List<DrugMaster> =
                masters.filter { it.drugName == name }

            override suspend fun findByExactDrugOrPackageName(name: String): List<DrugMaster> =
                masters.filter { it.drugName == name || it.packageName == name }

            override suspend fun findByCore(core: String): List<DrugMaster> =
                masters.filter { master ->
                    master.drugName.contains(core) ||
                        master.drugNameKana1?.contains(core) == true ||
                        master.drugNameKana2?.contains(core) == true ||
                        master.drugNameKana3?.contains(core) == true ||
                        master.alias?.contains(core) == true
                }

            override suspend fun findByCoreNormalized(
                core: String,
                coreFullWidth: String,
                coreSmallKanaNormalized: String
            ): List<DrugMaster> =
                (findByCore(core) + findByCore(coreFullWidth) + findByCore(coreSmallKanaNormalized))
                    .distinctBy { it.hot13 }

            override suspend fun findAllForLevenshtein(limit: Int): List<DrugMaster> =
                masters.take(limit)

            override suspend fun findByAnyGtin(code: String): DrugMaster? =
                masters.firstOrNull { it.gtin == code || it.hot13 == code || it.gtinSales == code || it.gtinCase == code }

            override suspend fun findByGtin(gtin: String): DrugMaster? =
                masters.firstOrNull { it.gtin == gtin }

            override suspend fun findByYjCode(yjCode: String): DrugMaster? =
                masters.firstOrNull { it.yjCode == yjCode || it.drugCode == yjCode }

            override suspend fun findBySalesPackageGtin(gtin: String): DrugMaster? = null
            override suspend fun findByCaseGtin(gtin: String): DrugMaster? = null
            override suspend fun count(): Int = masters.size
        }
    }

    private fun fakeDrugPreferenceDao(): DrugPreferenceDao {
        return object : DrugPreferenceDao {
            override suspend fun upsert(pref: DrugPreference) = Unit
            override suspend fun findByYjCode(yjCode: String): DrugPreference? = null
            override suspend fun getAllPackingMachineDrugs(): List<DrugPreference> = emptyList()
            override suspend fun deleteByYjCode(yjCode: String) = Unit
            override suspend fun count(): Int = 0
        }
    }
}
