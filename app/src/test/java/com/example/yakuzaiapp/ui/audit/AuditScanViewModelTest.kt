package com.example.yakuzaiapp.ui.audit

import com.example.yakuzaiapp.data.local.dao.AuditDrugPreferenceDao
import com.example.yakuzaiapp.data.local.dao.DrugMasterDao
import com.example.yakuzaiapp.data.local.entity.AuditDrugPreference
import com.example.yakuzaiapp.data.local.entity.DrugMaster
import com.example.yakuzaiapp.domain.audit.DrugIdentity
import com.example.yakuzaiapp.domain.audit.DrugMasterMatcher
import com.example.yakuzaiapp.domain.audit.MatchResult
import com.example.yakuzaiapp.domain.audit.MatchStatus
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.text.Text
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuditScanViewModelTest {
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
    fun clearLearning_restoresOriginalResultForManualSelection() = runTest {
        val viewModel = AuditScanViewModel(matcher = matcher(), recognizer = fakeRecognizer())
        val original = ambiguousResult()
        viewModel.seedResultsForTest(listOf(original))

        viewModel.selectCandidate(0, original.candidates.first())
        advanceUntilIdle()

        val selected = viewModel.matchResults.value.single()
        assertEquals(MatchStatus.CONFIRMED, selected.status)
        assertTrue(selected.learnedFromPreference)

        viewModel.clearLearning(0)
        advanceUntilIdle()

        val restored = viewModel.matchResults.value.single()
        assertEquals(MatchStatus.AMBIGUOUS, restored.status)
        assertEquals(original.candidates.map { it.yjCode }, restored.candidates.map { it.yjCode })
    }

    @Test
    fun clearLearning_removesPreferenceAndRevertsToAmbiguousState() = runTest {
        val preferenceDao = FakeAuditDrugPreferenceDao()
        val matcher = matcher(preferenceDao)
        val viewModel = AuditScanViewModel(matcher, fakeRecognizer())
        val original = ambiguousResult()
        viewModel.seedResultsForTest(listOf(original))

        viewModel.selectCandidate(0, original.candidates.last())
        advanceUntilIdle()

        val learned = viewModel.matchResults.value.single()
        assertTrue(learned.learnedFromPreference)

        viewModel.clearLearning(0)
        advanceUntilIdle()

        val cleared = viewModel.matchResults.value.single()
        assertEquals(MatchStatus.AMBIGUOUS, cleared.status)
        assertFalse(cleared.learnedFromPreference)

        val rematched = matcher.match(original.ocrName)
        assertEquals(MatchStatus.AMBIGUOUS, rematched.status)
        assertFalse(rematched.learnedFromPreference)
    }

    private fun ambiguousResult(): MatchResult {
        val candidates = listOf(
            DrugIdentity("2354003F1010", "繧ｻ繝ｳ繝弱す繝蛾権12mg A", "12mg", "錠"),
            DrugIdentity("2354003F2016", "繧ｻ繝ｳ繝弱す繝蛾権12mg B", "12mg", "錠")
        )
        return MatchResult(
            ocrName = "繧ｻ繝ｳ繝弱す繝蛾権12mg",
            quantityText = null,
            candidates = candidates,
            status = MatchStatus.AMBIGUOUS,
            matchKey = "繧ｻ繝ｳ繝弱す繝蛾権|錠|12.0mg"
        )
    }

    private fun matcher(preferenceDao: AuditDrugPreferenceDao? = null): DrugMasterMatcher {
        return DrugMasterMatcher(
            drugMasterDao = fakeDrugDao(),
            preferenceDao = preferenceDao
        )
    }

    private fun fakeDrugDao(): DrugMasterDao {
        val masters = listOf(
            drug("繧ｻ繝ｳ繝弱す繝蛾権12mg A", yjCode = "2354003F1010", packageSpec = "12mg"),
            drug("繧ｻ繝ｳ繝弱す繝蛾権12mg B", yjCode = "2354003F2016", packageSpec = "12mg")
        )
        return object : DrugMasterDao {
            override suspend fun insertAll(items: List<DrugMaster>) = Unit
            override suspend fun upsertAll(items: List<DrugMaster>) = Unit
            override suspend fun deleteAll() = Unit
            override suspend fun deleteImported() = Unit
            override suspend fun deleteUserRegistered(hot13: String) = Unit
            override fun observeUserRegistered(): Flow<List<DrugMaster>> = flowOf(emptyList())
            override fun observeAll(): Flow<List<DrugMaster>> = flowOf(masters)
            override fun searchByKeyword(keyword: String): Flow<List<DrugMaster>> = flowOf(masters)
            override suspend fun findByExactName(name: String): List<DrugMaster> = emptyList()
            override suspend fun findByExactDrugOrPackageName(name: String): List<DrugMaster> = emptyList()
            override suspend fun findByCore(core: String): List<DrugMaster> =
                masters.filter { master ->
                    master.drugName.contains(core) || master.packageSpec?.contains(core) == true
                }
            override suspend fun findByCoreNormalized(
                core: String,
                coreFullWidth: String,
                coreSmallKanaNormalized: String
            ): List<DrugMaster> = masters
            override suspend fun findAllForLevenshtein(limit: Int): List<DrugMaster> = masters
            override suspend fun findByGtin(gtin: String): DrugMaster? = null
            override suspend fun findByAnyGtin(code: String): DrugMaster? = null
            override suspend fun findByYjCode(yjCode: String): DrugMaster? = null
            override suspend fun findBySalesPackageGtin(gtin: String): DrugMaster? = null
            override suspend fun findByCaseGtin(gtin: String): DrugMaster? = null
            override suspend fun count(): Int = masters.size
        }
    }

    private fun drug(
        name: String,
        yjCode: String,
        packageSpec: String? = null
    ): DrugMaster {
        return DrugMaster(
            hot13 = yjCode,
            drugCode = yjCode,
            drugName = name,
            packageSpec = packageSpec,
            yjCode = yjCode,
            dosageForm = "錠"
        )
    }

    private class FakeAuditDrugPreferenceDao : AuditDrugPreferenceDao {
        private val prefs = mutableMapOf<String, AuditDrugPreference>()

        override suspend fun findByMatchKey(matchKey: String): AuditDrugPreference? = prefs[matchKey]
        override suspend fun upsert(pref: AuditDrugPreference) { prefs[pref.matchKey] = pref }
        override suspend fun deleteByMatchKey(matchKey: String) { prefs.remove(matchKey) }
        override suspend fun listRecent(): List<AuditDrugPreference> = prefs.values.toList()
    }

    private fun fakeRecognizer(): OcrRecognizer {
        return object : OcrRecognizer {
            override fun process(image: com.google.mlkit.vision.common.InputImage): Task<Text> {
                throw UnsupportedOperationException("Not used in this test")
            }

            override fun close() = Unit
        }
    }
}
