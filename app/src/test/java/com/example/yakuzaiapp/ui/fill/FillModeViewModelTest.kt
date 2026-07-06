package com.example.yakuzaiapp.ui.fill

import com.example.yakuzaiapp.data.local.dao.FillHistoryDao
import com.example.yakuzaiapp.data.local.dao.StaffMasterDao
import com.example.yakuzaiapp.data.local.entity.DrugMaster
import com.example.yakuzaiapp.data.local.entity.FillHistory
import com.example.yakuzaiapp.data.local.entity.StaffMaster
import com.example.yakuzaiapp.data.repository.StaffSelectionRepository
import com.example.yakuzaiapp.repository.DrugMasterLookup
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FillModeViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun firstScanSelectsDrugAndMovesToTargetStage() = runTest(dispatcher) {
        val drug = drugMaster(
            gtin = "14987376861653",
            yjCode = "2354003F2014",
            drugName = "センノシド錠12mg"
        )
        val viewModel = fillModeViewModel(
            fakeLookup("14987376861653" to drug)
        )

        viewModel.processBarcode("14987376861653")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(FillModeStage.SELECT_TARGET, state.phase)
        assertEquals("センノシド錠12mg", state.selectedDrugName)
        assertFalse(state.isComplete)
    }

    @Test
    fun firstScanExtractsExpirationDateFromGs1Barcode() = runTest(dispatcher) {
        val drug = drugMaster(
            gtin = "04987376861687",
            yjCode = "2354003F2014",
            drugName = "センノシド錠12mg"
        )
        val viewModel = fillModeViewModel(
            fakeLookup("04987376861687" to drug)
        )

        viewModel.processBarcode("(01)04987376861687(17)280615(10)LG221")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(FillModeStage.SELECT_TARGET, state.phase)
        assertEquals("2028-06-15", state.selectedSourceExpirationDate)
        assertEquals("2028-06-15", state.lastScannedExpirationDate)
    }

    @Test
    fun firstScanExtractsMonthOnlyExpirationDateWhenGs1DayIsZero() = runTest(dispatcher) {
        val drug = drugMaster(
            gtin = "04987376861687",
            yjCode = "2354003F2014",
            drugName = "センノシド錠12mg"
        )
        val viewModel = fillModeViewModel(
            fakeLookup("04987376861687" to drug)
        )

        viewModel.processBarcode("01049873768616871728060010LG221")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(FillModeStage.SELECT_TARGET, state.phase)
        assertEquals("2028-06", state.selectedSourceExpirationDate)
    }

    @Test
    fun firstScanShowsWarningWhenSourceExpirationDateIsExpired() = runTest(dispatcher) {
        val drug = drugMaster(
            gtin = "04987376861687",
            yjCode = "2354003F2014",
            drugName = "繧ｻ繝ｳ繝弱す繝蛾権12mg"
        )
        val viewModel = fillModeViewModel(
            drugMasterLookup = fakeLookup("04987376861687" to drug),
            nowMs = { localDateMillis(2026, 7, 3) }
        )

        viewModel.processBarcode("(01)04987376861687(17)260702(10)LG221")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(FillModeStage.SELECT_TARGET, state.phase)
        assertEquals("2026-07-02", state.selectedSourceExpirationDate)
        assertEquals("期限が切れています", state.expirationWarningMessage)
    }

    @Test
    fun firstScanDoesNotShowWarningOnExpirationDate() = runTest(dispatcher) {
        val drug = drugMaster(
            gtin = "04987376861687",
            yjCode = "2354003F2014",
            drugName = "繧ｻ繝ｳ繝弱す繝蛾権12mg"
        )
        val viewModel = fillModeViewModel(
            drugMasterLookup = fakeLookup("04987376861687" to drug),
            nowMs = { localDateMillis(2026, 7, 3) }
        )

        viewModel.processBarcode("(01)04987376861687(17)260703(10)LG221")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("2026-07-03", state.selectedSourceExpirationDate)
        assertEquals(null, state.expirationWarningMessage)
    }

    @Test
    fun monthOnlyExpirationShowsWarningAfterEndOfMonth() = runTest(dispatcher) {
        val drug = drugMaster(
            gtin = "04987376861687",
            yjCode = "2354003F2014",
            drugName = "繧ｻ繝ｳ繝弱す繝蛾権12mg"
        )
        val viewModel = fillModeViewModel(
            drugMasterLookup = fakeLookup("04987376861687" to drug),
            nowMs = { localDateMillis(2026, 8, 1) }
        )

        viewModel.processBarcode("(01)04987376861687(17)260700(10)LG221")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("2026-07", state.selectedSourceExpirationDate)
        assertEquals("期限が切れています", state.expirationWarningMessage)
    }

    @Test
    fun expirationWarningDismissResetsToDrugSelection() = runTest(dispatcher) {
        val drug = drugMaster(
            gtin = "04987376861687",
            yjCode = "2354003F2014",
            drugName = "繧ｻ繝ｳ繝弱す繝蛾権12mg"
        )
        val viewModel = fillModeViewModel(
            drugMasterLookup = fakeLookup("04987376861687" to drug),
            nowMs = { localDateMillis(2026, 7, 3) }
        )

        viewModel.processBarcode("(01)04987376861687(17)260702(10)LG221")
        advanceUntilIdle()
        viewModel.dismissExpirationWarning()

        val state = viewModel.uiState.value
        assertEquals(FillModeStage.SELECT_DRUG, state.phase)
        assertEquals(null, state.expirationWarningMessage)
        assertEquals(null, state.selectedDrugName)
        assertEquals(null, state.selectedSourceExpirationDate)
        assertFalse(state.isComplete)
    }

    @Test
    fun sourceExpirationCanBeUpdatedByFollowUpOcrResultForSameGtin() = runTest(dispatcher) {
        var now = 0L
        val drug = drugMaster(
            gtin = "14987376861653",
            yjCode = "2354003F2014",
            drugName = "センノシド錠12mg"
        )
        val viewModel = fillModeViewModel(
            drugMasterLookup = fakeLookup("14987376861653" to drug),
            nowMs = { now }
        )

        viewModel.processBarcode("0114987376861653")
        advanceUntilIdle()
        now = 200L
        viewModel.processBarcode("(01)14987376861653(17)280600")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(FillModeStage.SELECT_TARGET, state.phase)
        assertEquals("2028-06", state.selectedSourceExpirationDate)
        assertEquals("2028-06", state.lastScannedExpirationDate)
        assertFalse(state.isComplete)
    }

    @Test
    fun followUpOcrExpiredSourceExpirationShowsWarning() = runTest(dispatcher) {
        var now = localDateMillis(2026, 7, 3)
        val drug = drugMaster(
            gtin = "14987376861653",
            yjCode = "2354003F2014",
            drugName = "繧ｻ繝ｳ繝弱す繝蛾権12mg"
        )
        val viewModel = fillModeViewModel(
            drugMasterLookup = fakeLookup("14987376861653" to drug),
            nowMs = { now }
        )

        viewModel.processBarcode("0114987376861653")
        advanceUntilIdle()
        now += 200L
        viewModel.processBarcode("(01)14987376861653(17)260702")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(FillModeStage.SELECT_TARGET, state.phase)
        assertEquals("2026-07-02", state.selectedSourceExpirationDate)
        assertEquals("期限が切れています", state.expirationWarningMessage)
        assertFalse(state.isComplete)
    }

    @Test
    fun gtinOnlyScanDoesNotSetExpirationDate() = runTest(dispatcher) {
        val drug = drugMaster(
            gtin = "14987376861653",
            yjCode = "2354003F2014",
            drugName = "センノシド錠12mg"
        )
        val viewModel = fillModeViewModel(
            fakeLookup("14987376861653" to drug)
        )

        viewModel.processBarcode("14987376861653")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(FillModeStage.SELECT_TARGET, state.phase)
        assertEquals(null, state.selectedSourceExpirationDate)
        assertEquals(null, state.lastScannedExpirationDate)
    }

    @Test
    fun nonGs1SeventeenDigitsDoNotSetExpirationDate() = runTest(dispatcher) {
        val drug = drugMaster(
            gtin = "04987376861687",
            yjCode = "2354003F2014",
            drugName = "センノシド錠12mg"
        )
        val viewModel = fillModeViewModel(
            fakeLookup("04987376861687" to drug)
        )

        viewModel.processBarcode("(01)04987376861687(10)LOT172806")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(FillModeStage.SELECT_TARGET, state.phase)
        assertEquals(null, state.selectedSourceExpirationDate)
    }

    @Test
    fun secondScanWithSameYjCodeCompletesFill() = runTest(dispatcher) {
        var now = 0L
        val selected = drugMaster(
            gtin = "14987376861653",
            yjCode = "2354003F2014",
            drugName = "センノシド錠12mg"
        )
        val target = drugMaster(
            gtin = "24987376861650",
            yjCode = "2354003F2014",
            drugName = "センノシド錠12mg"
        )
        val viewModel = fillModeViewModel(
            drugMasterLookup = fakeLookup(
                "14987376861653" to selected,
                "24987376861650" to target
            ),
            nowMs = { now }
        )

        viewModel.processBarcode("14987376861653")
        advanceUntilIdle()
        now = 3000L
        viewModel.processBarcode("24987376861650")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(FillModeStage.COMPLETED, state.phase)
        assertTrue(state.isComplete)
        assertEquals("センノシド錠12mg", state.selectedDrugName)
    }

    @Test
    fun secondScanWithDirectYjCodeCompletesFill() = runTest(dispatcher) {
        var now = 0L
        val selected = drugMaster(
            gtin = "14987376861653",
            yjCode = "2354003F2014",
            drugName = "センノシド錠12mg"
        )
        val viewModel = fillModeViewModel(
            drugMasterLookup = fakeLookup("14987376861653" to selected),
            nowMs = { now }
        )

        viewModel.processBarcode("14987376861653")
        advanceUntilIdle()
        now = 3000L
        viewModel.processBarcode("2354003F2014")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(FillModeStage.COMPLETED, state.phase)
        assertTrue(state.isComplete)
        assertTrue(state.statusText.contains("充填OK"))
    }

    @Test
    fun directCodeTargetScanDoesNotReuseSourceExpirationDate() = runTest(dispatcher) {
        var now = 0L
        val selected = drugMaster(
            gtin = "04987376861687",
            yjCode = "2354003F2014",
            drugName = "センノシド錠12mg"
        )
        val viewModel = fillModeViewModel(
            drugMasterLookup = fakeLookup("04987376861687" to selected),
            nowMs = { now }
        )

        viewModel.processBarcode("010498737686168717280600")
        advanceUntilIdle()
        now = 3000L
        viewModel.processBarcode("2354003F2014")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(FillModeStage.COMPLETED, state.phase)
        assertTrue(state.isComplete)
        assertEquals("2028-06", state.selectedSourceExpirationDate)
        assertEquals(null, state.lastScannedExpirationDate)
    }

    @Test
    fun sameSourceGtinCompletesTargetScanAfterDelay() = runTest(dispatcher) {
        var now = 0L
        val selected = drugMaster(
            gtin = "14987376861653",
            yjCode = "2354003F2014",
            drugName = "センノシド錠12mg"
        )
        val viewModel = fillModeViewModel(
            drugMasterLookup = fakeLookup("14987376861653" to selected),
            nowMs = { now }
        )

        viewModel.processBarcode("14987376861653")
        advanceUntilIdle()
        now = 3000L
        viewModel.processBarcode("14987376861653")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(FillModeStage.COMPLETED, state.phase)
        assertTrue(state.isComplete)
        assertTrue(state.statusText.contains("充填OK"))
    }

    @Test
    fun gtinOnlyTargetScanDoesNotReuseSourceExpirationDate() = runTest(dispatcher) {
        var now = 0L
        val selected = drugMaster(
            gtin = "04987376861687",
            yjCode = "2354003F2014",
            drugName = "センノシド錠12mg"
        )
        val target = drugMaster(
            gtin = "14987376861653",
            yjCode = "2354003F2014",
            drugName = "センノシド錠12mg"
        )
        val viewModel = fillModeViewModel(
            drugMasterLookup = fakeLookup(
                "04987376861687" to selected,
                "14987376861653" to target
            ),
            nowMs = { now }
        )

        viewModel.processBarcode("010498737686168717280600")
        advanceUntilIdle()
        now = 3000L
        viewModel.processBarcode("14987376861653")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(FillModeStage.COMPLETED, state.phase)
        assertTrue(state.isComplete)
        assertEquals("2028-06", state.selectedSourceExpirationDate)
        assertEquals(null, state.lastScannedExpirationDate)
    }

    @Test
    fun repeatedSameSourceGtinWithoutSeparationDoesNotCompleteTargetScan() = runTest(dispatcher) {
        var now = 0L
        val selected = drugMaster(
            gtin = "14987376861653",
            yjCode = "2354003F2014",
            drugName = "センノシド錠12mg"
        )
        val viewModel = fillModeViewModel(
            drugMasterLookup = fakeLookup("14987376861653" to selected),
            nowMs = { now }
        )

        viewModel.processBarcode("14987376861653")
        advanceUntilIdle()
        listOf(1000L, 2000L, 3000L).forEach { tick ->
            now = tick
            viewModel.processBarcode("14987376861653")
            advanceUntilIdle()
        }

        val state = viewModel.uiState.value
        assertEquals(FillModeStage.SELECT_TARGET, state.phase)
        assertFalse(state.isComplete)
        assertEquals("薬品をカメラから外して、充填先のカセットまたは瓶のコードを準備してください", state.statusText)
    }

    @Test
    fun targetScanExtractsGtinFromGs1TextWhenAi01IsNotFirst() = runTest(dispatcher) {
        var now = 0L
        val selected = drugMaster(
            gtin = "04987376861687",
            yjCode = "2354003F2014",
            drugName = "センノシド錠12mg"
        )
        val target = drugMaster(
            gtin = "14987376861653",
            yjCode = "2354003F2014",
            drugName = "センノシド錠12mg"
        )
        val viewModel = fillModeViewModel(
            drugMasterLookup = fakeLookup(
                "04987376861687" to selected,
                "14987376861653" to target
            ),
            nowMs = { now }
        )

        viewModel.processBarcode("0104987376861687")
        advanceUntilIdle()
        now = 3000L
        viewModel.processBarcode("(17)280600(10)LG221(01)14987376861653")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(FillModeStage.COMPLETED, state.phase)
        assertTrue(state.isComplete)
    }

    @Test
    fun targetScanExtractsExpirationDateFromGs1Barcode() = runTest(dispatcher) {
        var now = 0L
        val selected = drugMaster(
            gtin = "04987376861687",
            yjCode = "2354003F2014",
            drugName = "センノシド錠12mg"
        )
        val target = drugMaster(
            gtin = "14987376861653",
            yjCode = "2354003F2014",
            drugName = "センノシド錠12mg"
        )
        val viewModel = fillModeViewModel(
            drugMasterLookup = fakeLookup(
                "04987376861687" to selected,
                "14987376861653" to target
            ),
            nowMs = { now }
        )

        viewModel.processBarcode("010498737686168717280600")
        advanceUntilIdle()
        now = 3000L
        viewModel.processBarcode("(17)290131(01)14987376861653(10)LG221")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(FillModeStage.COMPLETED, state.phase)
        assertTrue(state.isComplete)
        assertEquals("2028-06", state.selectedSourceExpirationDate)
        assertEquals("2029-01-31", state.lastScannedExpirationDate)
    }

    @Test
    fun mismatchKeepsWaitingForTargetScan() = runTest(dispatcher) {
        var now = 0L
        val selected = drugMaster(
            gtin = "14987376861653",
            yjCode = "2354003F2014",
            drugName = "センノシド錠12mg"
        )
        val mismatch = drugMaster(
            gtin = "14987155157635",
            yjCode = "1149019F1013",
            drugName = "デエビゴ錠5mg"
        )
        val viewModel = fillModeViewModel(
            drugMasterLookup = fakeLookup(
                "14987376861653" to selected,
                "14987155157635" to mismatch
            ),
            nowMs = { now }
        )

        viewModel.processBarcode("14987376861653")
        advanceUntilIdle()
        now = 3000L
        viewModel.processBarcode("14987155157635")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(FillModeStage.SELECT_TARGET, state.phase)
        assertFalse(state.isComplete)
        assertTrue(state.statusText.isNotBlank())
    }

    @Test
    fun unknownTargetScanKeepsTargetInstruction() = runTest(dispatcher) {
        var now = 0L
        val selected = drugMaster(
            gtin = "14987376861653",
            yjCode = "2354003F2014",
            drugName = "センノシド錠12mg"
        )
        val viewModel = fillModeViewModel(
            drugMasterLookup = fakeLookup("14987376861653" to selected),
            nowMs = { now }
        )

        viewModel.processBarcode("14987376861653")
        advanceUntilIdle()
        now = 3000L
        viewModel.processBarcode("91222631378972")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(FillModeStage.SELECT_TARGET, state.phase)
        assertFalse(state.isComplete)
        assertEquals("充填先のカセットまたは瓶のコードをスキャンしてください", state.statusText)
    }

    @Test
    fun targetScanIsIgnoredDuringInitialDelay() = runTest(dispatcher) {
        var now = 0L
        val selected = drugMaster(
            gtin = "14987376861653",
            yjCode = "2354003F2014",
            drugName = "センノシド錠12mg"
        )
        val viewModel = fillModeViewModel(
            drugMasterLookup = fakeLookup("14987376861653" to selected),
            nowMs = { now }
        )

        viewModel.processBarcode("14987376861653")
        advanceUntilIdle()
        now = 1000L
        viewModel.processBarcode("14987376861653")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(FillModeStage.SELECT_TARGET, state.phase)
        assertFalse(state.isComplete)
        assertTrue(state.statusText.contains("充填先"))
    }

    @Test
    fun completedStateIgnoresFurtherUnknownScans() = runTest(dispatcher) {
        var now = 0L
        val selected = drugMaster(
            gtin = "14987376861653",
            yjCode = "2354003F2014",
            drugName = "センノシド錠12mg"
        )
        val target = drugMaster(
            gtin = "24987376861650",
            yjCode = "2354003F2014",
            drugName = "センノシド錠12mg"
        )
        val viewModel = fillModeViewModel(
            drugMasterLookup = fakeLookup(
                "14987376861653" to selected,
                "24987376861650" to target
            ),
            nowMs = { now }
        )

        viewModel.processBarcode("14987376861653")
        advanceUntilIdle()
        now = 3000L
        viewModel.processBarcode("24987376861650")
        advanceUntilIdle()
        viewModel.processBarcode("88657402338651")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(FillModeStage.COMPLETED, state.phase)
        assertTrue(state.isComplete)
        assertTrue(state.statusText.contains("充填OK"))
    }

    private fun fakeLookup(vararg entries: Pair<String, DrugMaster>): DrugMasterLookup {
        val map = entries.toMap()
        return object : DrugMasterLookup {
            override suspend fun findByAnyGtin(code: String): DrugMaster? {
                val candidates = linkedSetOf(
                    code,
                    code.filter(Char::isDigit),
                    code.trim()
                )
                if (code.length == 13) {
                    candidates += "0$code"
                }
                if (code.length == 14 && code.startsWith("0")) {
                    candidates += code.drop(1)
                }
                return candidates.asSequence().mapNotNull(map::get).firstOrNull()
            }
        }
    }

    private fun fillModeViewModel(
        drugMasterLookup: DrugMasterLookup,
        nowMs: () -> Long = System::currentTimeMillis,
    ): FillModeViewModel {
        val staff = StaffMaster(
            staffId = TEST_STAFF_ID,
            staffName = "Test Staff",
            staffKana = "TEST"
        )
        return FillModeViewModel(
            drugMasterLookup = drugMasterLookup,
            staffMasterDao = FakeStaffMasterDao(listOf(staff)),
            fillHistoryDao = FakeFillHistoryDao(),
            staffSelectionRepository = StaffSelectionRepository(TEST_STAFF_ID),
            nowMs = nowMs,
        ).also { it.selectStaff(staff) }
    }

    private fun localDateMillis(year: Int, month: Int, day: Int): Long {
        return LocalDate.of(year, month, day)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }

    private fun drugMaster(
        gtin: String,
        yjCode: String,
        drugName: String,
    ): DrugMaster {
        return DrugMaster(
            drugCode = yjCode,
            drugName = drugName,
            hot13 = gtin,
            gtin = gtin,
            yjCode = yjCode,
            packageName = drugName,
            packageSpec = "1錠",
            maker = "NIG"
        )
    }

    private class FakeStaffMasterDao(
        initialStaff: List<StaffMaster>,
    ) : StaffMasterDao {
        private val staff = MutableStateFlow(initialStaff)

        override fun observeAll(): Flow<List<StaffMaster>> = staff

        override suspend fun upsert(staff: StaffMaster) {
            this.staff.value = this.staff.value
                .filterNot { it.staffId == staff.staffId } + staff
        }

        override suspend fun delete(staff: StaffMaster) {
            this.staff.value = this.staff.value.filterNot { it.staffId == staff.staffId }
        }
    }

    private class FakeFillHistoryDao : FillHistoryDao {
        private val histories = MutableStateFlow(emptyList<FillHistory>())
        private var nextId = 1L

        override suspend fun insert(history: FillHistory): Long {
            val id = nextId++
            histories.value = listOf(history.copy(id = id)) + histories.value
            return id
        }

        override fun observeAll(): Flow<List<FillHistory>> = histories
    }

    private companion object {
        const val TEST_STAFF_ID = "test-staff"
    }
}
