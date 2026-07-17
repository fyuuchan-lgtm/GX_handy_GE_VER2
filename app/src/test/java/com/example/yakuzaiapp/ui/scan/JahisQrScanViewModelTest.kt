package com.example.yakuzaiapp.ui.scan

import com.example.yakuzaiapp.domain.jahis.DetectedQr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class JahisQrScanViewModelTest {
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
    fun isStructuredAppendComplete_returnsTrueWhenAllSequencesCollected() {
        val fragments = listOf(
            detected(saSequence = 0, saTotal = 3, saParity = 7),
            detected(saSequence = 1, saTotal = 3, saParity = 7),
            detected(saSequence = 2, saTotal = 3, saParity = 7)
        )

        assertTrue(isStructuredAppendComplete(fragments))
    }

    @Test
    fun isStructuredAppendComplete_returnsFalseWhenSequencesIncomplete() {
        val fragments = listOf(
            detected(saSequence = 0, saTotal = 3, saParity = 7),
            detected(saSequence = 2, saTotal = 3, saParity = 7)
        )

        assertFalse(isStructuredAppendComplete(fragments))
    }

    @Test
    fun isStructuredAppendComplete_returnsFalseWhenParityMismatch() {
        val fragments = listOf(
            detected(saSequence = 0, saTotal = 2, saParity = 7),
            detected(saSequence = 1, saTotal = 2, saParity = 8)
        )

        assertFalse(isStructuredAppendComplete(fragments))
    }

    @Test
    fun isStructuredAppendComplete_usesExactGroupId() {
        val fragments = listOf(
            detected(saSequence = 0, saTotal = 2, saGroupId = "group-a"),
            detected(saSequence = 1, saTotal = 2, saGroupId = "group-a")
        )
        assertTrue(isStructuredAppendComplete(fragments))

        val mixedGroups = listOf(
            detected(saSequence = 0, saTotal = 2, saGroupId = "group-a"),
            detected(saSequence = 1, saTotal = 2, saGroupId = "group-b")
        )
        assertFalse(isStructuredAppendComplete(mixedGroups))
    }

    @Test
    fun isStructuredAppendComplete_returnsFalseWhenNoSaMetadata() {
        val fragments = listOf(
            detected(),
            detected()
        )

        assertFalse(isStructuredAppendComplete(fragments))
    }

    @Test
    fun isStructuredAppendComplete_returnsFalseWhenTotalExceeds16() {
        val fragments = (0 until 17).map { index ->
            detected(saSequence = index, saTotal = 17, saParity = 9)
        }

        assertFalse(isStructuredAppendComplete(fragments))
    }

    @Test
    fun recordDetections_returnsCompleteFlagWithSaMetadata() {
        val viewModel = JahisQrScanViewModel()

        val first = viewModel.recordDetections(
            listOf(
                detected(text = "frag-0", left = 0, saSequence = 0, saTotal = 2, saParity = 5)
            )
        )
        assertEquals(1, first.count)
        assertFalse(first.isComplete)

        val second = viewModel.recordDetections(
            listOf(
                detected(text = "frag-1", left = 100, saSequence = 1, saTotal = 2, saParity = 5)
            )
        )
        assertEquals(2, second.count)
        assertTrue(second.isComplete)
    }

    @Test
    fun recordDetections_ignoresUnnumberedQrAfterStructuredAppendGroupStarts() {
        val viewModel = JahisQrScanViewModel()
        viewModel.recordDetections(
            listOf(
                detected(text = "part-0", saSequence = 0, saTotal = 2, saGroupId = "group-a"),
                detected(text = "part-1", saSequence = 1, saTotal = 2, saGroupId = "group-a")
            )
        )

        val result = viewModel.recordDetections(listOf(detected(text = "unrelated-qr")))

        assertEquals(2, result.count)
        assertTrue(result.isComplete)
        assertEquals(listOf("part-0", "part-1"), viewModel.fragments.value.map { it.text })
    }

    @Test
    fun recordDetections_discardsEarlierUnnumberedQrWhenStructuredGroupAppears() {
        val viewModel = JahisQrScanViewModel()
        viewModel.recordDetections(listOf(detected(text = "unrelated-qr")))

        val result = viewModel.recordDetections(
            listOf(detected(text = "part-0", saSequence = 0, saTotal = 2, saGroupId = "group-a"))
        )

        assertEquals(1, result.count)
        assertEquals(listOf("part-0"), viewModel.fragments.value.map { it.text })
    }

    @Test
    fun recordDetections_acceptsLongerFragmentAndReplacesShorter() {
        val viewModel = JahisQrScanViewModel()

        val short = detected(text = "JAHISTC01\r\n201,1,☆ラメルテオン錠8m", left = 10)
        val long = detected(text = "JAHISTC01\r\n201,1,☆ラメルテオン錠8mg（ロゼレム）,1,錠,4,1190016F1075\r\n", left = 5)

        val first = viewModel.recordDetections(listOf(short))
        assertEquals(1, first.count)
        assertFalse(first.isComplete)

        val second = viewModel.recordDetections(listOf(long))
        assertEquals(1, second.count)
        assertFalse(second.isComplete)
        assertEquals(long.text, viewModel.fragments.value.first().text)
    }

    @Test
    fun recordDetections_rejectsShorterDuplicateOfExistingFragment() {
        val viewModel = JahisQrScanViewModel()

        val long = detected(text = "JAHISTC01\r\n201,1,☆ラメルテオン錠8mg（ロゼレム）,1,錠,4,1190016F1075\r\n", left = 10)
        val short = detected(text = "JAHISTC01\r\n201,1,☆ラメルテオン錠8m", left = 5)

        viewModel.recordDetections(listOf(long))
        val second = viewModel.recordDetections(listOf(short))

        assertEquals(1, second.count)
        assertEquals(long.text, viewModel.fragments.value.first().text)
    }

    @Test
    fun recordDetections_acceptsMultipleDistinctFragments() {
        val viewModel = JahisQrScanViewModel()

        val first = detected(text = "JAHISTC01\r\n201,1,☆デエビゴ錠5mg,1,錠,4,1190027F2029\r\n", left = 10)
        val second = detected(text = "201,2,☆ラメルテオン錠8mg（ロゼレム）,1,錠,4,1190016F1075\r\n", left = 20)

        val result = viewModel.recordDetections(listOf(first, second))

        assertEquals(2, result.count)
        assertEquals(2, viewModel.fragments.value.size)
        assertEquals(first.text, viewModel.fragments.value[0].text)
        assertEquals(second.text, viewModel.fragments.value[1].text)
    }

    @Test
    fun autoDebounce_doesNotFireForMalformedSingleFragmentWithoutSaMetadata() {
        val viewModel = JahisQrScanViewModel()

        viewModel.recordDetections(
            listOf(
                detected(
                    text = """
                        JAHISTC01
                        1,Test User,1,20000101
                        5,20260608
                        201,1,Drug A,1,錠,4,1111111111111
                        301,1,1日1回朝食後,7,日分,1,1,
                        201,2,Broken Drug
                    """.trimIndent() + "\r\n",
                    left = 10
                )
            )
        )

        dispatcher.scheduler.advanceTimeBy(2100)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(null, viewModel.autoAssembleEvent.value)
    }

    @Test
    fun autoDebounce_doesNotNavigateForCompleteSingleFragmentWithoutSaMetadata() {
        val viewModel = JahisQrScanViewModel()

        viewModel.recordDetections(
            listOf(
                detected(
                    text = """
                        JAHISTC01
                        1,Test User,1,20000101
                        5,20260608
                        201,1,Drug A,1,錠,4,1111111111111
                        301,1,1日1回朝食後,7,日分,1,1,
                        201,2,Drug B,1,錠,4,2222222222222
                        301,2,1日2回朝夕,7,日分,1,1,
                        201,3,Drug C,1,錠,4,3333333333333
                        301,3,1日3回朝昼夕食後,7,日分,1,1,
                    """.trimIndent() + "\r\n",
                    left = 10
                )
            )
        )

        dispatcher.scheduler.advanceTimeBy(2100)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(null, viewModel.autoAssembleEvent.value)
    }

    @Test
    fun autoDebounce_doesNotNavigateAfterIdleTimeoutWithMultipleFragmentsWithoutSaMetadata() {
        val viewModel = JahisQrScanViewModel()

        viewModel.recordDetections(
            listOf(
                detected(
                    text = """
                        JAHISTC01
                        1,Test User,1,20000101
                        5,20260608
                        201,1,Drug A,1,骭,4,1111111111111
                        301,1,1譌･1蝗樊悃鬟溷ｾ・7,譌･蛻・1,1,
                        201,2,Drug B,1,骭,4,2222222222222
                    """.trimIndent() + "\r\n",
                    left = 10
                )
            )
        )

        viewModel.recordDetections(
            listOf(
                detected(
                    text = """
                        301,2,1譌･2蝗樊悃螟・7,譌･蛻・1,1,
                        201,3,Drug C,1,骭,4,3333333333333
                        301,3,1譌･3蝗樊悃譏ｼ螟暮｣溷ｾ・7,譌･蛻・1,1,
                    """.trimIndent() + "\r\n",
                    left = 20
                )
            )
        )

        dispatcher.scheduler.advanceTimeBy(2100)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(null, viewModel.autoAssembleEvent.value)
    }

    @Test
    fun autoDebounce_resetsWhenNewFragmentArrives() {
        val viewModel = JahisQrScanViewModel()

        viewModel.recordDetections(
            listOf(
                detected(
                    text = """
                        JAHISTC01
                        1,Test User,1,20000101
                        5,20260608
                        201,1,Drug A,1,錠,4,1111111111111
                        301,1,1日1回朝食後,7,日分,1,1,
                        201,2,Drug B,1,錠,4,2222222222222
                    """.trimIndent() + "\r\n",
                    left = 10
                )
            )
        )

        dispatcher.scheduler.advanceTimeBy(1500)
        viewModel.recordDetections(
            listOf(
                detected(
                    text = """
                        301,2,1日2回朝夕,7,日分,1,1,
                        201,3,Drug C,1,錠,4,3333333333333
                        301,3,1日3回朝昼夕食後,7,日分,1,1,
                    """.trimIndent() + "\r\n",
                    left = 20
                )
            )
        )

        dispatcher.scheduler.advanceTimeBy(1900)
        assertEquals(null, viewModel.autoAssembleEvent.value)

        dispatcher.scheduler.advanceTimeBy(200)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(null, viewModel.autoAssembleEvent.value)
    }

    private fun detected(
        text: String = "fragment",
        left: Int = 0,
        saSequence: Int? = null,
        saTotal: Int? = null,
        saParity: Int? = null,
        saGroupId: String? = null,
    ): DetectedQr {
        return DetectedQr(
            text = text,
            left = left,
            saSequence = saSequence,
            saTotal = saTotal,
            saParity = saParity,
            saGroupId = saGroupId,
        )
    }
}
