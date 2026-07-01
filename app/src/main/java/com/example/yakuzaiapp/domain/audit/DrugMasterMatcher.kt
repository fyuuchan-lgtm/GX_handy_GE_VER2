package com.example.yakuzaiapp.domain.audit

import android.util.Log
import com.example.yakuzaiapp.data.local.dao.AuditDrugPreferenceDao
import com.example.yakuzaiapp.data.local.dao.DrugMasterDao
import com.example.yakuzaiapp.data.local.entity.AuditDrugPreference
import com.example.yakuzaiapp.data.local.entity.DrugMaster
import java.text.Normalizer
import kotlin.math.min

private const val TAG = "AuditMatcher"

data class MatchResult(
    val ocrName: String,
    val candidates: List<DrugIdentity>,
    val status: MatchStatus,
    val matchKey: String? = null,
    val learnedFromPreference: Boolean = false
)

enum class MatchStatus {
    CONFIRMED,
    AMBIGUOUS,
    NOT_FOUND
}

data class NameComponents(
    val core: String,
    val parenAlias: String?,
    val dosageForm: String?
)

data class Spec(
    val value: Double,
    val unit: String
)

class DrugMasterMatcher(
    private val drugMasterDao: DrugMasterDao,
    private val preferenceDao: AuditDrugPreferenceDao? = null
) {
    private val specPattern = Regex(
        """([0-9０-９]+(?:[.．][0-9０-９]+)?)\s*(μg|μｇ|ug|ｕｇ|mg|ｍｇ|mL|ｍL|ml|IU|単位|g|％|%)""",
        RegexOption.IGNORE_CASE
    )
    private val dosageFormPatterns = linkedMapOf(
        "ドライシロップ" to "ドライシロップ",
        "カプセル" to "カプセル",
        "注射液" to "注",
        "貼付剤" to "貼付",
        "ローション" to "ローション",
        "クリーム" to "クリーム",
        "シロップ" to "シロップ",
        "内用液" to "液",
        "坐削" to "坐剤",
        "生剤" to "坐剤",
        "生品" to "坐剤",
        "学剤" to "坐剤",
        "テープ" to "テープ",
        "パッチ" to "パッチ",
        "パップ" to "パップ",
        "軟膏" to "軟膏",
        "ゲル" to "ゲル",
        "坐剤" to "坐剤",
        "吸入" to "吸入",
        "点眼" to "点眼",
        "点鼻" to "点鼻",
        "点耳" to "点耳",
        "顆粒" to "顆粒",
        "細粒" to "細粒",
        "錠剤" to "錠",
        "貼付" to "貼付",
        "錠" to "錠",
        "散" to "散",
        "液" to "液",
        "注" to "注"
    )
    private val dosagePattern = Regex(
        dosageFormPatterns.keys
            .sortedByDescending { it.length }
            .joinToString("|") { Regex.escape(it) }
    )
    private val dosageFormAliases = mapOf(
        "坐剤" to listOf("坐剤", "サポ", "坐薬"),
        "錠" to listOf("錠"),
        "カプセル" to listOf("カプセル", "C", "Ｃ"),
        "散" to listOf("散"),
        "顆粒" to listOf("顆粒"),
        "細粒" to listOf("細粒"),
        "シロップ" to listOf("シロップ"),
        "ドライシロップ" to listOf("ドライシロップ"),
        "液" to listOf("液", "内用液"),
        "軟膏" to listOf("軟膏"),
        "クリーム" to listOf("クリーム"),
        "ゲル" to listOf("ゲル"),
        "ローション" to listOf("ローション"),
        "注" to listOf("注", "注射液"),
        "吸入" to listOf("吸入"),
        "点眼" to listOf("点眼"),
        "点鼻" to listOf("点鼻"),
        "点耳" to listOf("点耳"),
        "貼付" to listOf("貼付", "貼付剤"),
        "テープ" to listOf("テープ"),
        "パッチ" to listOf("パッチ"),
        "パップ" to listOf("パップ")
    )
    private val parenPattern = Regex("""[（(]([^）)]+)[）)]""")

    suspend fun match(ocrName: String): MatchResult {
        val components = extractCore(ocrName)
        val spec = extractSpec(ocrName)
        Log.d(
            TAG,
            "input='$ocrName' core='${components.core}' dosage=${components.dosageForm} spec=${spec?.value}${spec?.unit.orEmpty()}"
        )

        val searchTerms = listOfNotNull(
            components.core.takeIf { it.length >= 2 },
            components.parenAlias?.takeIf { it.length >= 2 },
            ocrName.takeIf { it.length >= 2 }
        ).distinct()

        val exactHits = gatherCandidates(searchTerms)
            .filter { normalize(it.drugName) == normalize(ocrName) }
        Log.d(TAG, "stage=exact hits=${exactHits.size}")
        if (exactHits.isNotEmpty()) return finalizeResult(ocrName, exactHits, components, spec)

        val coreHits = gatherCandidates(listOfNotNull(components.core.takeIf { it.length >= 2 }))
        val coreSpecDosageHits = coreHits
            .filterBySpec(spec)
            .filterByDosageForm(components.dosageForm, "core+spec+dosage")
        if (coreSpecDosageHits.isNotEmpty()) return finalizeResult(ocrName, coreSpecDosageHits, components, spec)

        val coreSpecHits = coreHits.filterBySpec(spec)
        Log.d(TAG, "stage=core+spec hits=${coreSpecHits.size}")
        if (coreSpecHits.isNotEmpty()) return finalizeResult(ocrName, coreSpecHits, components, spec)

        val coreDosageHits = coreHits.filterByDosageForm(components.dosageForm, "core+dosage")
        if (coreDosageHits.isNotEmpty()) return finalizeResult(ocrName, coreDosageHits, components, spec)

        Log.d(TAG, "stage=core hits=${coreHits.size}")
        if (coreHits.isNotEmpty()) return finalizeResult(ocrName, coreHits, components, spec)

        val aliasHits = gatherCandidates(listOfNotNull(components.parenAlias?.takeIf { it.length >= 2 }))
        val aliasSpecDosageHits = aliasHits
            .filterBySpec(spec)
            .filterByDosageForm(components.dosageForm, "alias+spec+dosage")
        if (aliasSpecDosageHits.isNotEmpty()) return finalizeResult(ocrName, aliasSpecDosageHits, components, spec)

        val fuzzyHits = if (components.core.length >= 3) {
            drugMasterDao.findAllForLevenshtein()
                .filter { master ->
                    val nameCore = extractCore(master.drugName).core
                    levenshtein(normalize(components.core), normalize(nameCore)) <= 2
                }
                .filterByDosageForm(components.dosageForm, "fuzzy")
                .take(20)
        } else {
            emptyList()
        }
        Log.d(TAG, "stage=fuzzy hits=${fuzzyHits.size}")

        return finalizeResult(ocrName, fuzzyHits, components, spec)
    }

    suspend fun matchAll(lines: List<DetectedDrugLine>): List<MatchResult> {
        return lines.map { line -> match(line.name) }
    }

    suspend fun searchCandidates(keyword: String): List<DrugIdentity> {
        return searchCandidates(keyword = keyword, contextName = null)
    }

    suspend fun searchCandidates(keyword: String, contextName: String?): List<DrugIdentity> {
        val components = extractCore(keyword)
        val contextComponents = contextName?.let { extractCore(it) }
        val terms = listOfNotNull(
            components.core.ifBlank { keyword.trim() }.takeIf { it.length >= 2 },
            components.parenAlias?.takeIf { it.length >= 2 },
            contextComponents?.core?.takeIf { it.length >= 2 },
            contextComponents?.parenAlias?.takeIf { it.length >= 2 }
        ).distinct()
        if (terms.isEmpty()) return emptyList()
        val expectedDosageForm = components.dosageForm ?: contextComponents?.dosageForm
        val expectedSpec = extractSpec(keyword)
        val requiredTerms = if (contextName.isNullOrBlank()) {
            emptyList()
        } else {
            listOf(keyword, contextComponents?.core.orEmpty())
                .joinToString(" ")
                .split(Regex("""[\s　]+"""))
                .map { it.trim() }
                .filter { it.length >= 2 }
        }
        return gatherCandidates(terms)
            .filterByRequiredTerms(requiredTerms)
            .filterBySpec(expectedSpec)
            .filterByDosageForm(expectedDosageForm, "manual-search")
            .toIdentities(preferredTerms = listOfNotNull(keyword, contextName) + terms)
    }

    suspend fun rememberSelection(result: MatchResult, identity: DrugIdentity) {
        val dao = preferenceDao ?: return
        val matchKey = result.matchKey ?: buildMatchKey(result.ocrName)
        val current = dao.findByMatchKey(matchKey)
        dao.upsert(
            AuditDrugPreference(
                matchKey = matchKey,
                yjCode = identity.yjCode,
                displayName = identity.displayName,
                selectCount = (current?.selectCount ?: 0) + 1,
                selectedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun clearLearning(result: MatchResult) {
        val dao = preferenceDao ?: return
        val matchKey = result.matchKey ?: buildMatchKey(result.ocrName)
        dao.deleteByMatchKey(matchKey)
    }

    internal fun buildMatchKey(ocrName: String): String {
        val components = extractCore(ocrName)
        val spec = extractSpec(ocrName)
        return buildMatchKey(components.core, components.dosageForm, spec)
    }

    internal fun normalize(s: String): String {
        val normalized = Normalizer.normalize(toHalfWidth(s), Normalizer.Form.NFKC)
            .trim()
            .replace(Regex("""[\s　]+"""), "")
            .replace("ー", "")
            .replace("-", "")
            .replace("－", "")
            .lowercase()
        return normalized.map { char ->
            when (char) {
                in 'ぁ'..'ゖ' -> char + ('ァ' - 'ぁ')
                else -> char
            }
        }.joinToString("")
    }

    internal fun extractCore(s: String): NameComponents {
        val halfWidth = toHalfWidth(s)
        val parenAlias = parenPattern.find(halfWidth)?.groupValues?.getOrNull(1)?.trim()
        val dosageForm = extractDosageForm(halfWidth)
        val withoutParen = halfWidth.replace(parenPattern, "")
        val withoutSpec = withoutParen.replace(specPattern, "")
        val core = withoutSpec
            .replace(dosagePattern, "")
            .replace(Regex("""[「」『』【】\[\]・･,，.．\s　]+"""), "")
            .replace(Regex("""[A-Za-z0-9]+$"""), "")
            .trim()
        return NameComponents(core = core, parenAlias = parenAlias, dosageForm = dosageForm)
    }

    internal fun extractSpec(s: String): Spec? {
        val match = specPattern.find(toHalfWidth(s)) ?: return null
        val value = normalize(match.groupValues[1])
            .replace(",", "")
            .toDoubleOrNull() ?: return null
        val unit = normalizeUnit(match.groupValues[2])
        return Spec(value = value, unit = unit)
    }

    private suspend fun gatherCandidates(terms: List<String>): List<DrugMaster> {
        return terms
            .filter { it.length >= 2 }
            .flatMap { term ->
                drugMasterDao.findByCoreNormalized(
                    core = term,
                    coreFullWidth = toFullWidth(term),
                    coreSmallKanaNormalized = normalizeSmallKana(term)
                )
            }
            .distinctBy { it.hot13 }
    }

    private suspend fun finalizeResult(
        ocrName: String,
        candidates: List<DrugMaster>,
        components: NameComponents,
        spec: Spec?
    ): MatchResult {
        val matchKey = buildMatchKey(components.core, components.dosageForm, spec)
        val base = result(ocrName, candidates, matchKey)
        if (base.status != MatchStatus.AMBIGUOUS) return base

        val pref = preferenceDao?.findByMatchKey(matchKey) ?: return base
        val preferred = base.candidates.find { it.yjCode == pref.yjCode } ?: return base
        Log.d(TAG, "preference hit key='$matchKey' yj=${pref.yjCode}")
        return base.copy(
            candidates = listOf(preferred),
            status = MatchStatus.CONFIRMED,
            learnedFromPreference = true
        )
    }

    private fun result(ocrName: String, candidates: List<DrugMaster>, matchKey: String): MatchResult {
        val identities = candidates.toIdentities(preferredTerms = listOf(ocrName, matchKey))
        val status = when (identities.size) {
            0 -> MatchStatus.NOT_FOUND
            1 -> MatchStatus.CONFIRMED
            else -> MatchStatus.AMBIGUOUS
        }
        Log.d(
            TAG,
            "result $status drug='${identities.firstOrNull()?.displayName}' yj=${identities.firstOrNull()?.yjCode}"
        )
        return MatchResult(
            ocrName = ocrName,
            candidates = identities,
            status = status,
            matchKey = matchKey
        )
    }

    private fun buildMatchKey(core: String?, dosageForm: String?, spec: Spec?): String {
        val specText = spec?.let { "${it.value}${it.unit}" }.orEmpty()
        return "${core.orEmpty()}|${dosageForm.orEmpty()}|$specText"
    }

    private fun List<DrugMaster>.filterBySpec(expected: Spec?): List<DrugMaster> {
        if (expected == null) return this
        return filter { master -> master.matchesSpec(expected) }
    }

    private fun List<DrugMaster>.filterByDosageForm(expected: String?, stage: String): List<DrugMaster> {
        if (expected.isNullOrBlank()) {
            Log.d(TAG, "stage=$stage hits=$size (dosage filter skipped)")
            return this
        }
        val normalizedExpected = normalize(expected)
        val expectedAliases = dosageFormAliases[expected].orEmpty()
            .ifEmpty { listOf(expected) }
            .map { normalize(it) }
        val filtered = filter { master ->
            master.matchesDosageForm(normalizedExpected, expectedAliases)
        }
        Log.d(TAG, "stage=$stage hits=${filtered.size} (before dosage filter: $size, after: ${filtered.size})")
        return filtered
    }

    private fun List<DrugMaster>.filterByRequiredTerms(requiredTerms: List<String>): List<DrugMaster> {
        val normalizedTerms = requiredTerms
            .map { normalize(it) }
            .filter { it.isNotBlank() }
        if (normalizedTerms.isEmpty()) return this

        val filtered = filter { master ->
            val searchable = listOfNotNull(
                master.drugName,
                master.packageName,
                master.maker,
                master.alias,
                master.packageSpec,
                master.drugNameKana1,
                master.drugNameKana2,
                master.drugNameKana3
            ).joinToString(" ").let { normalize(it) }
            normalizedTerms.all { term -> searchable.contains(term) }
        }
        Log.d(TAG, "manual-search requiredTerms=${requiredTerms.joinToString("|")} hits=${filtered.size} (before: $size)")
        return filtered
    }

    private fun DrugMaster.matchesSpec(expected: Spec): Boolean {
        val fields = listOfNotNull(drugName, packageSpec, packageName)
        return fields.any { field ->
            extractSpec(field)?.let { actual ->
                actual.unit == expected.unit && kotlin.math.abs(actual.value - expected.value) < 0.0001
            } ?: false
        }
    }

    private fun normalizeUnit(unit: String): String {
        return when (normalize(unit)) {
            "μg", "ug" -> "μg"
            "ml" -> "mL"
            "mg" -> "mg"
            "g" -> "g"
            "%" -> "%"
            "iu" -> "IU"
            "単位" -> "単位"
            else -> unit
        }
    }

    private fun extractDosageForm(text: String): String? {
        val matched = dosagePattern.find(text)?.value ?: return null
        return dosageFormPatterns[matched] ?: matched
    }

    private fun DrugMaster.matchesDosageForm(
        normalizedExpected: String,
        normalizedAliases: List<String>
    ): Boolean {
        val dosage = dosageForm?.takeIf { it.isNotBlank() }
        if (dosage != null) {
            val normalizedDosage = normalize(dosage)
            return normalizedAliases.any { alias -> normalizedDosage.contains(alias) }
        }
        val normalizedName = normalize(drugName)
        return normalizedAliases.any { alias -> normalizedName.contains(alias) } ||
            normalizedName.contains(normalizedExpected)
    }

    internal fun toFullWidth(s: String): String {
        return s.map { ch ->
            when {
                ch in '0'..'9' -> '０' + (ch - '0')
                ch in 'A'..'Z' -> 'Ａ' + (ch - 'A')
                ch in 'a'..'z' -> 'ａ' + (ch - 'a')
                ch == ' ' -> '　'
                else -> ch
            }
        }.joinToString("")
    }

    internal fun normalizeSmallKana(s: String): String {
        val map = mapOf(
            'ァ' to 'ア',
            'ィ' to 'イ',
            'ゥ' to 'ウ',
            'ェ' to 'エ',
            'ォ' to 'オ',
            'ャ' to 'ヤ',
            'ュ' to 'ユ',
            'ョ' to 'ヨ',
            'ッ' to 'ツ',
            'ぁ' to 'あ',
            'ぃ' to 'い',
            'ぅ' to 'う',
            'ぇ' to 'え',
            'ぉ' to 'お',
            'ゃ' to 'や',
            'ゅ' to 'ゆ',
            'ょ' to 'よ',
            'っ' to 'つ'
        )
        return s.map { map[it] ?: it }.joinToString("")
    }

    private fun toHalfWidth(s: String): String {
        return s.map { ch ->
            when {
                ch in '０'..'９' -> '0' + (ch - '０')
                ch in 'Ａ'..'Ｚ' -> 'A' + (ch - 'Ａ')
                ch in 'ａ'..'ｚ' -> 'a' + (ch - 'ａ')
                ch == '．' -> '.'
                ch == '，' -> ','
                ch == '（' -> '('
                ch == '）' -> ')'
                ch == '　' -> ' '
                else -> ch
            }
        }.joinToString("")
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)

        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = min(
                    min(current[j - 1] + 1, previous[j] + 1),
                    previous[j - 1] + cost
                )
            }
            val tmp = previous
            previous = current
            current = tmp
        }
        return previous[b.length]
    }
}
