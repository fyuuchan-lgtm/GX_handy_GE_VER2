package com.example.yakuzaiapp.domain.audit

import com.example.yakuzaiapp.data.local.dao.AuditDrugPreferenceDao
import com.example.yakuzaiapp.data.local.dao.DrugMasterDao
import com.example.yakuzaiapp.data.local.entity.AuditDrugPreference
import com.example.yakuzaiapp.data.local.entity.DrugMaster
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DrugMasterMatcherTest {
    @Test
    fun normalize_unifiesWidthSpacesAndLongVowelMarks() {
        val matcher = matcher()

        assertEquals("60mg", matcher.normalize("６０ｍｇ"))
        assertEquals("2.5mg", matcher.normalize("２．５ｍｇ"))
        assertEquals("６０ｍｇ", matcher.toFullWidth("60mg"))
        assertEquals("デエビゴ錠5mg", matcher.normalizeSmallKana("デェビゴ錠5mg"))
        assertEquals("ロキソニン", matcher.normalize("ロキソニン"))
        assertEquals("ロキソニン", matcher.normalize("ロキソーニン"))
        assertEquals("ロキソプロフェン錠60mg", matcher.normalize(" ロキソ プロフェン錠６０ｍｇ "))
    }

    @Test
    fun extractSpec_readsStrength() {
        val matcher = matcher()

        assertEquals(Spec(60.0, "mg"), matcher.extractSpec("ロキソプロフェン錠60mg"))
        assertEquals(Spec(2.5, "mg"), matcher.extractSpec("ロスバスタチンOD錠２．５ｍｇ"))
        assertEquals(Spec(0.25, "mg"), matcher.extractSpec("ブロチゾラム錠0.25mg"))
        assertEquals(Spec(300.0, "単位"), matcher.extractSpec("インスリン300単位"))
        assertNull(matcher.extractSpec("新レシカルボン坐剤"))
    }

    @Test
    fun extractCore_removesSpecDosageAndKeepsParenAliasAndDosage() {
        val matcher = matcher()

        assertEquals(
            NameComponents("ロキソプロフェン", null, "錠"),
            matcher.extractCore("ロキソプロフェン錠60mg")
        )
        assertEquals(
            NameComponents("ジクロフェナク", "ボルタレン", "坐剤"),
            matcher.extractCore("ジクロフェナク坐剤25mgJG (ボルタレン)")
        )
        assertEquals(
            NameComponents("ブロチゾラム", "レンドルミン", "錠"),
            matcher.extractCore("ブロチゾラム錠0.25mg(レンドルミン)")
        )
    }

    @Test
    fun extractCore_normalizesOcrMisreadSuppositoryForms() {
        val matcher = matcher()

        assertEquals(
            NameComponents("ジクロフェナク", null, "坐剤"),
            matcher.extractCore("ジクロフェナク生品25mgJG")
        )
        assertEquals(
            NameComponents("新レシカルボン", null, "坐剤"),
            matcher.extractCore("新レシカルボン学剤")
        )
        assertEquals(
            NameComponents("ジクロフェナク", null, "坐剤"),
            matcher.extractCore("ジクロフェナク生剤25mg")
        )
    }

    @Test
    fun toIdentities_groupsDrugMastersByYjCode() {
        val masters = listOf(
            drug("デエビゴ錠5mg", hot13 = "hot1", yjCode = "1190027F2029", packageSpec = "5mg1錠"),
            drug("デエビゴ錠5mg", hot13 = "hot2", yjCode = "1190027F2029", packageSpec = "5mg10錠"),
            drug("デエビゴ錠5mg", hot13 = "hot3", yjCode = "1190027F2029", packageSpec = "5mg100錠")
        )

        assertEquals(
            listOf(DrugIdentity("1190027F2029", "デエビゴ錠5mg", "5mg1錠", "錠")),
            masters.toIdentities()
        )
    }

    @Test
    fun match_confirmsExactNameAsIdentity() = runTest {
        val master = drug("ロキソプロフェン錠60mg", yjCode = "1149019F1563")
        val result = matcher(master).match("ロキソプロフェン錠60mg")

        assertEquals(MatchStatus.CONFIRMED, result.status)
        assertEquals("1149019F1563", result.candidates.single().yjCode)
    }

    @Test
    fun match_confirmsCoreSpecAndGroupsPackageVariations() = runTest {
        val packageA = drug(
            name = "デエビゴ錠５ｍｇ",
            hot13 = "hot-a",
            yjCode = "1190027F2029",
            packageSpec = "5mg1錠"
        )
        val packageB = drug(
            name = "デエビゴ錠５ｍｇ",
            hot13 = "hot-b",
            yjCode = "1190027F2029",
            packageSpec = "5mg10錠"
        )

        val result = matcher(packageA, packageB).match("デエビゴ錠5mg")

        assertEquals(MatchStatus.CONFIRMED, result.status)
        assertEquals(listOf("1190027F2029"), result.candidates.map { it.yjCode })
    }

    @Test
    fun match_filtersByDosageForm() = runTest {
        val tablet = drug(
            name = "ロキソプロフェンNa錠60mg",
            hot13 = "tablet",
            yjCode = "1149019F1563",
            packageSpec = "60mg1錠",
            dosageForm = "錠"
        )
        val tape = drug(
            name = "ロキソプロフェンNaテープ50mg",
            hot13 = "tape",
            yjCode = "2649735S1018",
            packageSpec = "50mg1枚",
            dosageForm = "テープ"
        )

        val result = matcher(tablet, tape).match("ロキソプロフェン錠60mg")

        assertEquals(MatchStatus.CONFIRMED, result.status)
        assertEquals("1149019F1563", result.candidates.single().yjCode)
    }

    @Test
    fun match_findsFullWidthMasterFromHalfWidthOcr() = runTest {
        val master = drug(
            name = "ロキソプロフェンＮａ錠６０ｍｇ",
            hot13 = "tablet",
            yjCode = "1149019F1563",
            packageSpec = "６０ｍｇ１錠",
            dosageForm = "錠"
        )

        val result = matcher(master).match("ロキソプロフェン錠60mg")

        assertEquals(MatchStatus.CONFIRMED, result.status)
        assertEquals("1149019F1563", result.candidates.single().yjCode)
    }

    @Test
    fun match_findsMasterWhenOcrUsesSmallKana() = runTest {
        val master = drug(
            name = "デエビゴ錠5mg",
            hot13 = "dayvigo",
            yjCode = "1190027F2029",
            packageSpec = "5mg1錠",
            dosageForm = "錠"
        )

        val result = matcher(master).match("デェビゴ錠5mg")

        assertEquals(MatchStatus.CONFIRMED, result.status)
        assertEquals("1190027F2029", result.candidates.single().yjCode)
    }

    @Test
    fun match_usesDrugNameFallbackWhenMasterDosageFormIsBlank() = runTest {
        val tablet = drug(
            name = "ロキソプロフェンNa錠60mg",
            hot13 = "tablet",
            yjCode = "1149019F1563",
            packageSpec = "60mg1錠",
            dosageForm = ""
        )
        val tape = drug(
            name = "ロキソプロフェンナトリウムテープ100mg",
            hot13 = "tape",
            yjCode = "2649735S1018",
            packageSpec = "100mg1枚",
            dosageForm = null
        )

        val result = matcher(tablet, tape).match("ロキソプロフェン錠60mg")

        assertEquals(MatchStatus.CONFIRMED, result.status)
        assertEquals("1149019F1563", result.candidates.single().yjCode)
    }

    @Test
    fun match_rescuesLoxoprofenTabletWhenMasterDosageFormIsEmpty() = runTest {
        val tablet = drug(
            name = "ロキソプロフェンNa錠60mg",
            hot13 = "tablet",
            yjCode = "1149019F1563",
            packageSpec = "60mg1錠",
            dosageForm = ""
        )

        val result = matcher(tablet).match("ロキソプロフェン錠60mg")

        assertEquals(MatchStatus.CONFIRMED, result.status)
        assertEquals("1149019F1563", result.candidates.single().yjCode)
    }

    @Test
    fun searchCandidates_includesPackageNameMatches() = runTest {
        val towa = drug(
            name = "ロキソニン錠60mg",
            packageName = "ロキソプロフェンNa錠60mg「トーワ」",
            hot13 = "towa",
            yjCode = "1149019F1999",
            packageSpec = "60mg1錠",
            dosageForm = "錠"
        )

        val result = matcher(towa).searchCandidates("ロキソプロフェン錠60mg")

        assertEquals(listOf("1149019F1999"), result.map { it.yjCode })
        assertEquals("ロキソプロフェンNa錠60mg「トーワ」", result.single().displayName)
    }

    @Test
    fun matchKeepsPackageNameOnlyAdoptedDrugAsAmbiguousCandidate() = runTest {
        val fiveVisibleCandidates = (1..5).map { index ->
            drug(
                name = "ロキソプロフェン錠60mg メーカー$index",
                hot13 = "visible-$index",
                yjCode = "1149019F15$index",
                packageSpec = "60mg1錠",
                dosageForm = "錠"
            )
        }
        val adoptedTowa = drug(
            name = "ロキソニン錠60mg",
            packageName = "ロキソプロフェンNa錠60mg「トーワ」",
            hot13 = "towa",
            yjCode = "1149019F1999",
            packageSpec = "60mg1錠",
            dosageForm = "錠"
        )

        val result = matcher(*(fiveVisibleCandidates + adoptedTowa).toTypedArray())
            .match("ロキソプロフェン錠60mg")

        assertEquals(MatchStatus.AMBIGUOUS, result.status)
        assertEquals(true, result.candidates.any { it.yjCode == "1149019F1999" })
    }

    @Test
    fun toIdentities_keepsPackageNameForCandidateDisplay() {
        val masters = listOf(
            drug(
                name = "ロキソニン錠60mg",
                packageName = "ロキソプロフェンNa錠60mg「トーワ」",
                hot13 = "towa",
                yjCode = "1149019F1999",
                packageSpec = "60mg1錠"
            )
        )

        val identity = masters.toIdentities().single()

        assertEquals("ロキソプロフェンNa錠60mg「トーワ」", identity.displayName)
        assertEquals("ロキソニン錠60mg", identity.sourceName)
    }

    @Test
    fun toIdentities_prefersPackageNameMatchingSearchTermWithinSameYj() {
        val masters = listOf(
            drug(
                name = "ロキソプロフェンNa錠60mg",
                packageName = "ロキソプロフェンNa錠60mg「サワイ」",
                hot13 = "sawai",
                yjCode = "1149019F1999",
                packageSpec = "60mg1錠"
            ),
            drug(
                name = "ロキソニン錠60mg",
                packageName = "ロキソプロフェンNa錠60mg「トーワ」",
                hot13 = "towa",
                yjCode = "1149019F1999",
                packageSpec = "60mg1錠"
            )
        )

        val identity = masters.toIdentities(preferredTerms = listOf("トーワ")).single()

        assertEquals("ロキソプロフェンNa錠60mg「トーワ」", identity.displayName)
        assertEquals("ロキソニン錠60mg", identity.sourceName)
    }

    @Test
    fun searchCandidates_usesContextNameToFilterRefinementTerm() = runTest {
        val towaDigestive = drug(
            name = "別薬錠10mg",
            packageName = "別薬錠10mg「トーワ」",
            hot13 = "digestive-towa",
            yjCode = "9999999F0001",
            packageSpec = "10mg1錠",
            dosageForm = "錠"
        )
        val loxoprofenTowa = drug(
            name = "ロキソニン錠60mg",
            packageName = "ロキソプロフェンNa錠60mg「トーワ」",
            hot13 = "loxo-towa",
            yjCode = "1149019F1999",
            packageSpec = "60mg1錠",
            dosageForm = "錠"
        )
        val loxoprofenSawai = drug(
            name = "ロキソプロフェンNa錠60mg",
            packageName = "ロキソプロフェンNa錠60mg「サワイ」",
            hot13 = "loxo-sawai",
            yjCode = "1149019F1888",
            packageSpec = "60mg1錠",
            dosageForm = "錠"
        )

        val result = matcher(towaDigestive, loxoprofenTowa, loxoprofenSawai)
            .searchCandidates(keyword = "トーワ", contextName = "ロキソプロフェン錠60mg")

        assertEquals(listOf("1149019F1999"), result.map { it.yjCode })
        assertEquals("ロキソプロフェンNa錠60mg「トーワ」", result.single().displayName)
    }

    @Test
    fun searchCandidates_doesNotInheritContextSpecWhenKeywordHasNoSpec() = runTest {
        val bicanate1000 = drug(
            name = "ビカネイト注1000",
            hot13 = "bicanate-1000",
            yjCode = "3319400A2020",
            packageSpec = "1000mL",
            dosageForm = "注"
        )

        val result = matcher(bicanate1000)
            .searchCandidates(keyword = "ビカネ", contextName = "ビカネイト注500")

        assertEquals(listOf("3319400A2020"), result.map { it.yjCode })
    }

    @Test
    fun match_returnsAmbiguousWhenMultipleYjCodesRemain() = runTest {
        val result = matcher(
            drug("カロナール錠200", yjCode = "1141007F1039", packageSpec = "200mg"),
            drug("カロナール錠500", yjCode = "1141007F3023", packageSpec = "500mg")
        ).match("カロナール錠")

        assertEquals(MatchStatus.AMBIGUOUS, result.status)
        assertEquals(2, result.candidates.size)
    }

    @Test
    fun match_staysAmbiguousWhenNoLearningExists() = runTest {
        val preferenceDao = FakeAuditDrugPreferenceDao()
        val matcher = matcher(
            masters = arrayOf(
                drug("センノシド錠12mg A", yjCode = "2354003F1010", packageSpec = "12mg"),
                drug("センノシド錠12mg B", yjCode = "2354003F2016", packageSpec = "12mg")
            ),
            preferenceDao = preferenceDao
        )

        val result = matcher.match("センノシド錠12mg")

        assertEquals(MatchStatus.AMBIGUOUS, result.status)
        assertEquals(false, result.learnedFromPreference)
    }

    @Test
    fun match_promotesAmbiguousToConfirmedWhenLearningExists() = runTest {
        val preferenceDao = FakeAuditDrugPreferenceDao()
        val matcher = matcher(
            masters = arrayOf(
                drug("センノシド錠12mg A", yjCode = "2354003F1010", packageSpec = "12mg"),
                drug("センノシド錠12mg B", yjCode = "2354003F2016", packageSpec = "12mg")
            ),
            preferenceDao = preferenceDao
        )
        val ambiguous = matcher.match("センノシド錠12mg")
        matcher.rememberSelection(ambiguous, ambiguous.candidates[1])

        val learned = matcher.match("センノシド錠12mg")

        assertEquals(MatchStatus.CONFIRMED, learned.status)
        assertEquals("2354003F2016", learned.candidates.single().yjCode)
        assertEquals(true, learned.learnedFromPreference)
    }

    @Test
    fun match_ignoresLearningWhenYjCodeIsNotInCurrentCandidates() = runTest {
        val preferenceDao = FakeAuditDrugPreferenceDao(
            "センノシド|錠|12.0mg" to "missing-yj"
        )
        val matcher = matcher(
            masters = arrayOf(
                drug("センノシド錠12mg A", yjCode = "2354003F1010", packageSpec = "12mg"),
                drug("センノシド錠12mg B", yjCode = "2354003F2016", packageSpec = "12mg")
            ),
            preferenceDao = preferenceDao
        )

        val result = matcher.match("センノシド錠12mg")

        assertEquals(MatchStatus.AMBIGUOUS, result.status)
        assertEquals(false, result.learnedFromPreference)
    }

    @Test
    fun match_returnsAmbiguousAfterLearningIsCleared() = runTest {
        val preferenceDao = FakeAuditDrugPreferenceDao()
        val matcher = matcher(
            masters = arrayOf(
                drug("センノシド錠12mg A", yjCode = "2354003F1010", packageSpec = "12mg"),
                drug("センノシド錠12mg B", yjCode = "2354003F2016", packageSpec = "12mg")
            ),
            preferenceDao = preferenceDao
        )
        val ambiguous = matcher.match("センノシド錠12mg")
        matcher.rememberSelection(ambiguous, ambiguous.candidates[0])
        val learned = matcher.match("センノシド錠12mg")

        matcher.clearLearning(learned)
        val cleared = matcher.match("センノシド錠12mg")

        assertEquals(MatchStatus.AMBIGUOUS, cleared.status)
        assertEquals(false, cleared.learnedFromPreference)
    }

    @Test
    fun match_returnsNotFound() = runTest {
        val result = matcher(drug("カロナール錠200")).match("存在しない薬錠10mg")

        assertEquals(MatchStatus.NOT_FOUND, result.status)
        assertEquals(emptyList<DrugIdentity>(), result.candidates)
    }

    @Test
    fun match_usesAliasForParenOrBrandNames() = runTest {
        val master = drug(
            name = "ジクロフェナクNa坐剤25mg",
            packageSpec = "25mg",
            alias = "ボルタレン",
            yjCode = "1147700J1234",
            dosageForm = "坐剤"
        )

        val result = matcher(master).match("ボルタレン坐剤25mg")

        assertEquals(MatchStatus.CONFIRMED, result.status)
        assertEquals("1147700J1234", result.candidates.single().yjCode)
    }

    @Test
    fun match_prefersGenericCoreBeforeBrandAlias() = runTest {
        val generic = drug(
            name = "ジクロフェナクナトリウム坐剤25mg",
            hot13 = "generic",
            packageSpec = "25mg",
            yjCode = "1147700J1234",
            dosageForm = "坐剤"
        )
        val brand = drug(
            name = "ボルタレンサポ25mg",
            hot13 = "brand",
            packageSpec = "25mg",
            alias = "ボルタレン",
            yjCode = "1147700J5678",
            dosageForm = "坐剤"
        )

        val result = matcher(generic, brand).match("ジクロフェナク坐剤25mgJG (ボルタレン)")

        assertEquals(MatchStatus.CONFIRMED, result.status)
        assertEquals("1147700J1234", result.candidates.single().yjCode)
    }

    @Test
    fun match_keepsMisreadRecalbonSuppositoryAsCandidate() = runTest {
        val master = drug("新レシカルボン坐剤", yjCode = "2359800J1020", dosageForm = "坐剤")

        val result = matcher(master).match("新レシカルボン学剤")

        assertEquals(MatchStatus.CONFIRMED, result.status)
        assertEquals("2359800J1020", result.candidates.single().yjCode)
    }

    @Test
    fun match_usesLevenshteinFallback() = runTest {
        val suppository = drug("新レシカルボン坐剤", yjCode = "2359800J1020", dosageForm = "坐剤")
        val loxoprofen = drug(
            "ロキソプロフェン錠60mg",
            packageSpec = "60mg",
            yjCode = "1149019F1563",
            dosageForm = "錠"
        )
        val matcher = matcher(suppository, loxoprofen)

        assertEquals("2359800J1020", matcher.match("新レシカルボン学剤").candidates.single().yjCode)
        assertEquals("1149019F1563", matcher.match("ロキソフロフェン錠60mg").candidates.single().yjCode)
    }

    private fun matcher(vararg masters: DrugMaster): DrugMasterMatcher {
        return DrugMasterMatcher(fakeDao(masters.toList()))
    }

    private fun matcher(
        masters: Array<out DrugMaster>,
        preferenceDao: AuditDrugPreferenceDao
    ): DrugMasterMatcher {
        return DrugMasterMatcher(
            drugMasterDao = fakeDao(masters.toList()),
            preferenceDao = preferenceDao
        )
    }

    private fun drug(
        name: String,
        hot13: String = name,
        packageName: String? = null,
        packageSpec: String? = null,
        alias: String? = null,
        yjCode: String = name,
        dosageForm: String? = inferDosageForm(name)
    ): DrugMaster {
        return DrugMaster(
            hot13 = hot13,
            drugCode = yjCode,
            drugName = name,
            packageName = packageName,
            packageSpec = packageSpec,
            alias = alias,
            yjCode = yjCode,
            dosageForm = dosageForm
        )
    }

    private fun fakeDao(masters: List<DrugMaster>): DrugMasterDao {
        return object : DrugMasterDao {
            override suspend fun insertAll(items: List<DrugMaster>) = Unit
            override suspend fun upsertAll(items: List<DrugMaster>) = Unit
            override suspend fun deleteAll() = Unit
            override fun observeAll(): Flow<List<DrugMaster>> = flowOf(masters)
            override fun searchByKeyword(keyword: String): Flow<List<DrugMaster>> = flowOf(masters)
            override suspend fun findByExactName(name: String): List<DrugMaster> =
                masters.filter { it.drugName == name }

            override suspend fun findByExactDrugOrPackageName(name: String): List<DrugMaster> =
                masters.filter { it.drugName == name || it.packageName == name }

            override suspend fun findByCore(core: String): List<DrugMaster> =
                masters.filter { master ->
                    master.drugName.contains(core) ||
                        master.packageName?.contains(core) == true ||
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

            override suspend fun findByGtin(gtin: String): DrugMaster? = null
            override suspend fun findByAnyGtin(code: String): DrugMaster? = null
            override suspend fun findByYjCode(yjCode: String): DrugMaster? = null
            override suspend fun findBySalesPackageGtin(gtin: String): DrugMaster? = null
            override suspend fun findByCaseGtin(gtin: String): DrugMaster? = null
            override suspend fun count(): Int = masters.size
        }
    }

    private companion object {
        fun inferDosageForm(name: String): String? {
            return listOf("錠", "テープ", "坐剤", "カプセル", "散", "顆粒").firstOrNull { name.contains(it) }
        }
    }

    private class FakeAuditDrugPreferenceDao(
        vararg seeds: Pair<String, String>
    ) : AuditDrugPreferenceDao {
        private val prefs = seeds.associate { (key, yjCode) ->
            key to AuditDrugPreference(
                matchKey = key,
                yjCode = yjCode,
                displayName = yjCode,
                selectCount = 1,
                selectedAt = 1L
            )
        }.toMutableMap()

        override suspend fun findByMatchKey(matchKey: String): AuditDrugPreference? {
            return prefs[matchKey]
        }

        override suspend fun upsert(pref: AuditDrugPreference) {
            prefs[pref.matchKey] = pref
        }

        override suspend fun deleteByMatchKey(matchKey: String) {
            prefs.remove(matchKey)
        }

        override suspend fun listRecent(): List<AuditDrugPreference> {
            return prefs.values.sortedByDescending { it.selectedAt }.take(100)
        }
    }
}
