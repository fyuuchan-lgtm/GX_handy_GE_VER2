package com.example.yakuzaiapp.domain.audit

import com.example.yakuzaiapp.data.local.entity.DrugMaster

data class DrugIdentity(
    val yjCode: String,
    val displayName: String,
    val packageSpec: String?,
    val dosageForm: String?,
    val sourceName: String? = null
)

fun List<DrugMaster>.toIdentities(): List<DrugIdentity> {
    return toIdentities(preferredTerms = emptyList())
}

fun List<DrugMaster>.toIdentities(preferredTerms: List<String>): List<DrugIdentity> {
    val normalizedTerms = preferredTerms
        .map { it.normalizeForIdentity() }
        .filter { it.isNotBlank() }
    return this
        .filter { !it.yjCode.isNullOrBlank() }
        .groupBy { it.yjCode.orEmpty() }
        .map { (yjCode, group) ->
            val representative = group.maxByOrNull { it.identityRepresentativeScore(normalizedTerms) }
                ?: group.first()
            val displayName = representative.packageName
                ?.takeIf { it.isNotBlank() }
                ?: representative.drugName
            DrugIdentity(
                yjCode = yjCode,
                displayName = displayName,
                packageSpec = representative.packageSpec,
                dosageForm = representative.dosageForm,
                sourceName = representative.drugName.takeIf { it != displayName }
            )
        }
}

private fun DrugMaster.identityRepresentativeScore(normalizedTerms: List<String>): Int {
    val packageNameText = packageName.orEmpty().normalizeForIdentity()
    val drugNameText = drugName.normalizeForIdentity()
    val aliasText = alias.orEmpty().normalizeForIdentity()

    val termScore = normalizedTerms.fold(0) { total, term ->
        total + when {
            packageNameText.contains(term) -> 100
            drugNameText.contains(term) -> 80
            aliasText.contains(term) -> 60
            else -> 0
        }
    }

    val displayScore = when {
        !packageName.isNullOrBlank() -> 10
        !maker.isNullOrBlank() -> 5
        else -> 0
    }
    val specScore = if (!packageSpec.isNullOrBlank()) 1 else 0
    return termScore + displayScore + specScore
}

private fun String.normalizeForIdentity(): String {
    return map { ch ->
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
    }
        .joinToString("")
        .lowercase()
        .replace(Regex("""[\s　]+"""), "")
}
