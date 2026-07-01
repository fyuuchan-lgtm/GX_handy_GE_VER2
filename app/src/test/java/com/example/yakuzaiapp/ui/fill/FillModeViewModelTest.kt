package com.example.yakuzaiapp.ui.fill

import com.example.yakuzaiapp.data.local.entity.DrugMaster
import com.example.yakuzaiapp.repository.DrugMasterLookup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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
        val viewModel = FillModeViewModel(
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
        val viewModel = FillModeViewModel(
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
        val viewModel = FillModeViewModel(
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
    fun sameSourceGtinCompletesTargetScanAfterDelay() = runTest(dispatcher) {
        var now = 0L
        val selected = drugMaster(
            gtin = "14987376861653",
            yjCode = "2354003F2014",
            drugName = "センノシド錠12mg"
        )
        val viewModel = FillModeViewModel(
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
    fun repeatedSameSourceGtinWithoutSeparationDoesNotCompleteTargetScan() = runTest(dispatcher) {
        var now = 0L
        val selected = drugMaster(
            gtin = "14987376861653",
            yjCode = "2354003F2014",
            drugName = "センノシド錠12mg"
        )
        val viewModel = FillModeViewModel(
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
        val viewModel = FillModeViewModel(
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
        val viewModel = FillModeViewModel(
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
        val viewModel = FillModeViewModel(
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
        val viewModel = FillModeViewModel(
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
        val viewModel = FillModeViewModel(
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
}
