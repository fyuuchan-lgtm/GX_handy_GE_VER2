package com.example.yakuzaiapp.ui.audit

import com.example.yakuzaiapp.data.local.dao.DrugMasterDao
import com.example.yakuzaiapp.data.local.dao.SalesPackageDao
import com.example.yakuzaiapp.data.local.entity.DrugMaster
import com.example.yakuzaiapp.data.local.entity.SalesPackage
import com.example.yakuzaiapp.domain.audit.DrugIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuditPtpScanViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun extractGtin_handlesGs1AndEan13Forms() {
        val viewModel = AuditPtpScanViewModel(fakeDrugDao(), fakeSalesPackageDao())

        assertEquals("04987732010087", viewModel.extractGtin("0104987732010087"))
        assertEquals("04987732010087", viewModel.extractGtin("4987732010087"))
        assertEquals("04987732010087", viewModel.extractGtin("04987732010087"))
        assertEquals("04987732010087", viewModel.extractGtin(" 0 4 9 8 7 7 3 2 0 1 0 0 8 7 "))
        assertNull(viewModel.extractGtin("not-a-barcode"))
    }

    @Test
    fun extractGtin_rejectsInvalidCheckDigits() {
        val viewModel = AuditPtpScanViewModel(fakeDrugDao(), fakeSalesPackageDao())

        assertNull(viewModel.extractGtin("14987732010086"))
        assertNull(viewModel.extractGtin("(01)14987732010086"))
    }

    @Test
    fun initializeFromAudit_leavesQuantityBlankWhenOcrDidNotReadIt() {
        val viewModel = AuditPtpScanViewModel(fakeDrugDao(), fakeSalesPackageDao())

        viewModel.initializeFromAudit(
            listOf(DrugIdentity("YJ123", "デエビゴ錠5mg", "5mg1錠", "錠"))
        )

        assertNull(viewModel.uiState.value.rows.single().quantityDisplay)
    }

    @Test
    fun onBarcodeScanned_marksMatchingAuditRowOnce_and_ignoresDuplicates() = runTest {
        val master = drugMaster(
            hot13 = "hot-1",
            gtin = "04987732010087",
            gtinSales = "14987732010087",
            gtinCase = "24987732010087",
            yjCode = "YJ123",
            drugName = "デエビゴ錠5mg",
            packageSpec = "5mg1錠",
            dosageForm = "錠"
        )
        val salesPackage = salesPackage(
            gtin = "04987732010087",
            yjCode = "YJ123",
            gtinSales = "14987732010087",
            gtinCase = "24987732010087",
            packageName = "デエビゴ錠5mg"
        )
        val viewModel = AuditPtpScanViewModel(fakeDrugDao(master), fakeSalesPackageDao(salesPackage))

        viewModel.initializeFromAudit(listOf(DrugIdentity("YJ123", "デエビゴ錠5mg", "5mg1錠", "錠")))

        viewModel.onBarcodeScanned("0104987732010087")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.rows.single().scanned)
        assertEquals("04987732010087", viewModel.uiState.value.rows.single().scannedGtin)
        assertTrue(viewModel.uiState.value.isComplete)
        assertEquals("OK", viewModel.uiState.value.lastMessage)

        viewModel.onBarcodeScanned("0104987732010087")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.rows.single().scanned)
        assertTrue(viewModel.uiState.value.isComplete)
        assertEquals("スキャン済みです", viewModel.uiState.value.lastMessage)
    }

    @Test
    fun onBarcodeScanned_resolvesViaSalesPackageGtinSales() = runTest {
        val master = drugMaster(
            hot13 = "hot-1",
            gtin = "04987376861687",
            yjCode = "2354003F2014",
            drugName = "センノシド錠12mg"
        )
        val salesPackage = salesPackage(
            gtin = "04987376861687",
            yjCode = "2354003F2014",
            gtinSales = "14987376861653",
            gtinCase = "24987376861650",
            packageName = "センノシド錠12mg"
        )
        val viewModel = AuditPtpScanViewModel(fakeDrugDao(master), fakeSalesPackageDao(salesPackage))

        viewModel.initializeFromAudit(listOf(DrugIdentity("2354003F2014", "センノシド錠12mg", null, "錠")))

        viewModel.onBarcodeScanned("14987376861653")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.rows.single().scanned)
        assertEquals("14987376861653", viewModel.uiState.value.rows.single().scannedGtin)
        assertTrue(viewModel.uiState.value.isComplete)
        assertEquals("OK", viewModel.uiState.value.lastMessage)
    }

    @Test
    fun onBarcodeScanned_bridgesSalesPackagePriceCodeToAuditIndividualYjByDrugCode() = runTest {
        val individualMaster = drugMaster(
            hot13 = "hot-individual",
            yjCode = "2354003F2464",
            drugCode = "2354003F2014",
            drugName = "センノシド錠12mg「NIG」",
            packageName = "センノシド錠12mg「NIG」"
        )
        val salesPackage = salesPackage(
            gtin = "04987376861687",
            yjCode = "2354003F2014",
            packageName = "センノシド錠12mg「NIG」"
        )
        val viewModel = AuditPtpScanViewModel(fakeDrugDao(individualMaster), fakeSalesPackageDao(salesPackage))

        viewModel.initializeFromAudit(listOf(DrugIdentity("2354003F2464", "センノシド錠12mg「NIG」", null, "錠")))

        viewModel.onBarcodeScanned("04987376861687")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.rows.single().scanned)
        assertEquals("04987376861687", viewModel.uiState.value.rows.single().scannedGtin)
        assertEquals("OK", viewModel.uiState.value.lastMessage)
    }

    @Test
    fun onBarcodeScanned_prefersExactIndividualYjOverSharedDrugCodeAuditRow() = runTest {
        val master = drugMaster(
            hot13 = "hot-individual",
            yjCode = "2354003F2464",
            drugCode = "2354003F2014",
            drugName = "センノシド錠12mg「NIG」",
            packageName = "センノシド錠12mg「NIG」"
        )
        val salesPackage = salesPackage(
            gtin = "04987376861687",
            yjCode = "2354003F2014",
            packageName = "センノシド錠12mg「NIG」"
        )
        val viewModel = AuditPtpScanViewModel(fakeDrugDao(master), fakeSalesPackageDao(salesPackage))

        viewModel.initializeFromAudit(
            listOf(
                DrugIdentity("2354003F2014", "センノシド錠12mg", null, "錠"),
                DrugIdentity("2354003F2464", "センノシド錠12mg「NIG」", null, "錠")
            )
        )

        viewModel.onBarcodeScanned("04987376861687")
        advanceUntilIdle()

        val rows = viewModel.uiState.value.rows
        assertFalse(rows.first { it.yjCode == "2354003F2014" }.scanned)
        assertTrue(rows.first { it.yjCode == "2354003F2464" }.scanned)
        assertEquals("OK", viewModel.uiState.value.lastMessage)
    }

    @Test
    fun onBarcodeScanned_resolvesViaSalesPackageGtinCaseWhenPrimaryAndSalesMissing() = runTest {
        val master = drugMaster(
            hot13 = "hot-1",
            gtin = "04987376861687",
            yjCode = "2354003F2014",
            drugName = "センノシド錠12mg"
        )
        val salesPackage = salesPackage(
            gtin = "04987376861687",
            yjCode = "2354003F2014",
            gtinCase = "24987376861650",
            packageName = "センノシド錠12mg"
        )
        val viewModel = AuditPtpScanViewModel(fakeDrugDao(master), fakeSalesPackageDao(salesPackage))

        viewModel.initializeFromAudit(listOf(DrugIdentity("2354003F2014", "センノシド錠12mg", null, "錠")))

        viewModel.onBarcodeScanned("24987376861650")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.rows.single().scanned)
        assertEquals("24987376861650", viewModel.uiState.value.rows.single().scannedGtin)
        assertTrue(viewModel.uiState.value.isComplete)
        assertEquals("OK", viewModel.uiState.value.lastMessage)
    }

    @Test
    fun onBarcodeScanned_showsMismatchMessageWhenYjCodeNotInAuditList() = runTest {
        val master = drugMaster(
            hot13 = "hot-2",
            gtin = "04987224716428",
            yjCode = "YJ999",
            drugName = "別の薬"
        )
        val salesPackage = salesPackage(
            gtin = "04987224716428",
            yjCode = "YJ999",
            packageName = "別の薬"
        )
        val viewModel = AuditPtpScanViewModel(fakeDrugDao(master), fakeSalesPackageDao(salesPackage))

        viewModel.initializeFromAudit(listOf(DrugIdentity("YJ123", "デエビゴ錠5mg", "5mg1錠", "錠")))

        viewModel.onBarcodeScanned("0104987224716428")
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.rows.single().scanned)
        assertEquals("帳票にない薬品です", viewModel.uiState.value.lastMessage)
        assertFalse(viewModel.uiState.value.isComplete)
    }

    @Test
    fun onBarcodeScanned_showsMessageWhenMasterIsMissing() = runTest {
        val viewModel = AuditPtpScanViewModel(fakeDrugDao(), fakeSalesPackageDao())

        viewModel.initializeFromAudit(listOf(DrugIdentity("YJ123", "デエビゴ錠5mg", "5mg1錠", "錠")))

        viewModel.onBarcodeScanned("0100000000000000")
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.rows.single().scanned)
        assertEquals("マスターに該当なし: 00000000000000", viewModel.uiState.value.lastMessage)
        assertFalse(viewModel.uiState.value.isComplete)
    }

    @Test
    fun onBarcodeScanned_normalizes13DigitBarcodeTo14Digits() = runTest {
        val master = drugMaster(
            hot13 = "hot-3",
            gtin = "04987376861687",
            yjCode = "2354003F2014",
            drugName = "センノシド錠12mg"
        )
        val salesPackage = salesPackage(
            gtin = "04987376861687",
            yjCode = "2354003F2014",
            packageName = "センノシド錠12mg"
        )
        val viewModel = AuditPtpScanViewModel(fakeDrugDao(master), fakeSalesPackageDao(salesPackage))

        viewModel.initializeFromAudit(listOf(DrugIdentity("2354003F2014", "センノシド錠12mg", null, "錠")))

        viewModel.onBarcodeScanned("4987376861687")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.rows.single().scanned)
        assertEquals("04987376861687", viewModel.uiState.value.rows.single().scannedGtin)
        assertEquals("OK", viewModel.uiState.value.lastMessage)
    }

    private fun fakeDrugDao(vararg masters: DrugMaster): DrugMasterDao {
        val byGtin = masters.associateBy { it.gtin ?: "" }
        val bySales = masters.associateBy { it.gtinSales ?: "" }
        val byCase = masters.associateBy { it.gtinCase ?: "" }

        return object : DrugMasterDao {
            override suspend fun insertAll(items: List<DrugMaster>) = Unit
            override suspend fun upsertAll(items: List<DrugMaster>) = Unit
            override suspend fun deleteAll() = Unit
            override suspend fun deleteImported() = Unit
            override suspend fun deleteUserRegistered(hot13: String) = Unit
            override fun observeUserRegistered(): Flow<List<DrugMaster>> = flowOf(emptyList())
            override fun observeAll(): Flow<List<DrugMaster>> = flowOf(masters.toList())
            override fun searchByKeyword(keyword: String): Flow<List<DrugMaster>> = flowOf(masters.toList())
            override suspend fun findByExactName(name: String): List<DrugMaster> = emptyList()
            override suspend fun findByExactDrugOrPackageName(name: String): List<DrugMaster> =
                masters.filter { it.drugName == name || it.packageName == name }
            override suspend fun findByCore(core: String): List<DrugMaster> = emptyList()
            override suspend fun findByCoreNormalized(
                core: String,
                coreFullWidth: String,
                coreSmallKanaNormalized: String
            ): List<DrugMaster> = emptyList()
            override suspend fun findAllForLevenshtein(limit: Int): List<DrugMaster> = emptyList()
            override suspend fun findByGtin(gtin: String): DrugMaster? = byGtin[gtin]
            override suspend fun findByAnyGtin(code: String): DrugMaster? =
                byGtin[code] ?: bySales[code] ?: byCase[code]
            override suspend fun findByYjCode(yjCode: String): DrugMaster? =
                masters.firstOrNull { it.yjCode == yjCode || it.drugCode == yjCode }
            override suspend fun findBySalesPackageGtin(gtin: String): DrugMaster? = bySales[gtin]
            override suspend fun findByCaseGtin(gtin: String): DrugMaster? = byCase[gtin]
            override suspend fun count(): Int = masters.size
        }
    }

    private fun fakeSalesPackageDao(vararg packages: SalesPackage): SalesPackageDao {
        val byGtin = packages.associateBy { it.gtin }
        val bySales = packages.filter { !it.gtinSales.isNullOrBlank() }.associateBy { it.gtinSales!! }
        val byCase = packages.filter { !it.gtinCase.isNullOrBlank() }.associateBy { it.gtinCase!! }

        return object : SalesPackageDao {
            override suspend fun upsertAll(items: List<SalesPackage>) = Unit
            override suspend fun findByGtin(gtin: String): SalesPackage? = byGtin[gtin]
            override suspend fun findByAnyGtin(code: String): SalesPackage? =
                byGtin[code] ?: bySales[code] ?: byCase[code]
            override suspend fun findBySalesPackageGtin(gtin: String): SalesPackage? = bySales[gtin]
            override suspend fun findByCaseGtin(gtin: String): SalesPackage? = byCase[gtin]
            override suspend fun findAllByYjCode(yjCode: String): List<SalesPackage> =
                packages.filter { it.yjCode == yjCode }
            override suspend fun findByJanCode(janCode: String): SalesPackage? =
                packages.firstOrNull { it.janCode == janCode }
            override suspend fun count(): Int = packages.size
            override suspend fun deleteAll() = Unit
        }
    }

    private fun drugMaster(
        hot13: String,
        yjCode: String,
        drugName: String,
        packageName: String? = null,
        gtin: String? = null,
        gtinSales: String? = null,
        gtinCase: String? = null,
        packageSpec: String? = null,
        dosageForm: String? = null,
        drugCode: String = yjCode
    ): DrugMaster {
        return DrugMaster(
            hot13 = hot13,
            drugCode = drugCode,
            drugName = drugName,
            packageName = packageName,
            gtin = gtin,
            packageSpec = packageSpec,
            yjCode = yjCode,
            gtinSales = gtinSales,
            gtinCase = gtinCase,
            dosageForm = dosageForm
        )
    }

    private fun salesPackage(
        gtin: String,
        yjCode: String,
        gtinSales: String? = null,
        gtinCase: String? = null,
        janCode: String? = null,
        packageName: String? = null
    ): SalesPackage {
        return SalesPackage(
            gtin = gtin,
            yjCode = yjCode,
            gtinSales = gtinSales,
            gtinCase = gtinCase,
            janCode = janCode,
            packageName = packageName
        )
    }
}
