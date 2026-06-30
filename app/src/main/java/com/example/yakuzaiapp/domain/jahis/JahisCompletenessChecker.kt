package com.example.yakuzaiapp.domain.jahis

object JahisCompletenessChecker {
    private val HEADER_PATTERN = Regex("^JAHISTC\\d{2}")

    fun check(fullText: String): CheckResult {
        if (!HEADER_PATTERN.containsMatchIn(fullText)) {
            return CheckResult.Incomplete(
                issues = listOf(CheckIssue.MissingHeader),
                drugRpNumbers = emptySet(),
                usageRpNumbers = emptySet()
            )
        }

        val version = fullText.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("JAHISTC") }
            ?.substringBefore(",")
            .orEmpty()
        val drugRpNumbers = mutableSetOf<Int>()
        val usageRpNumbers = mutableSetOf<Int>()
        val malformedDrugRpNumbers = mutableSetOf<Int>()
        val malformedUsageRpNumbers = mutableSetOf<Int>()
        val issues = mutableListOf<CheckIssue>()

        fullText.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach { line ->
                val cols = line.split(",")
                when (cols.firstOrNull()?.toIntOrNull()) {
                    201 -> {
                        val rpNumber = cols.getOrNull(1)?.toIntOrNull()
                        val isValid = rpNumber != null && isValidDrugRecord(cols, version)
                        if (isValid && rpNumber != null) {
                            drugRpNumbers += rpNumber
                        } else if (rpNumber != null) {
                            malformedDrugRpNumbers += rpNumber
                            issues += CheckIssue.MalformedDrugRecord(rpNumber)
                        }
                    }

                    301 -> {
                        val rpNumber = cols.getOrNull(1)?.toIntOrNull()
                        val usage = cols.getOrNull(2)?.takeIf { it.isNotBlank() }
                            ?: cols.getOrNull(3)?.takeIf { it.isNotBlank() }
                        val isValid = rpNumber != null && cols.size >= 3 && !usage.isNullOrBlank()
                        if (isValid && rpNumber != null) {
                            usageRpNumbers += rpNumber
                        } else if (rpNumber != null) {
                            malformedUsageRpNumbers += rpNumber
                            issues += CheckIssue.MalformedUsageRecord(rpNumber)
                        }
                    }
                }
            }

        val allRpNumbers = drugRpNumbers + usageRpNumbers + malformedDrugRpNumbers + malformedUsageRpNumbers

        if (allRpNumbers.isEmpty()) {
            issues += CheckIssue.NoRpRecords
        } else {
            val maxRp = allRpNumbers.maxOrNull() ?: 0
            val expected = (1..maxRp).toSet()
            val missing = (expected - allRpNumbers).sorted()
            if (missing.isNotEmpty()) {
                issues += CheckIssue.MissingRp(missing)
            }
        }

        (drugRpNumbers - usageRpNumbers - malformedUsageRpNumbers).sorted().forEach { rp ->
            issues += CheckIssue.MissingUsageForRp(rp)
        }
        (usageRpNumbers - drugRpNumbers - malformedDrugRpNumbers).sorted().forEach { rp ->
            issues += CheckIssue.OrphanUsageRecord(rp)
        }

        return if (issues.isEmpty()) {
            CheckResult.Complete(
                drugRpNumbers = drugRpNumbers,
                usageRpNumbers = usageRpNumbers
            )
        } else {
            CheckResult.Incomplete(
                issues = issues,
                drugRpNumbers = drugRpNumbers,
                usageRpNumbers = usageRpNumbers
            )
        }
    }

    fun describe(issues: List<CheckIssue>): String {
        return issues.joinToString(separator = " / ") { issue ->
            when (issue) {
                CheckIssue.MissingHeader -> "JAHISヘッダが見つかりません"
                CheckIssue.NoRpRecords -> "RPレコードが見つかりません"
                is CheckIssue.MissingRp -> "RP${issue.rpNumbers.joinToString(",")} が見つかりません"
                is CheckIssue.MissingUsageForRp -> "RP${issue.rpNumber} の用法レコードが見つかりません"
                is CheckIssue.OrphanUsageRecord -> "RP${issue.rpNumber} の薬剤レコードが見つかりません"
                is CheckIssue.MalformedDrugRecord -> "RP${issue.rpNumber} の薬剤レコードが不完全です"
                is CheckIssue.MalformedUsageRecord -> "RP${issue.rpNumber} の用法レコードが不完全です"
            }
        }
    }

    private fun isValidDrugRecord(cols: List<String>, version: String): Boolean {
        return if (isTc01Style(version)) {
            cols.size >= 7 &&
                !cols.getOrNull(2).isNullOrBlank() &&
                !cols.getOrNull(3).isNullOrBlank() &&
                !cols.getOrNull(4).isNullOrBlank() &&
                !cols.getOrNull(5).isNullOrBlank() &&
                !cols.getOrNull(6).isNullOrBlank()
        } else {
            cols.size >= 10 &&
                !cols.getOrNull(4).isNullOrBlank() &&
                !cols.getOrNull(5).isNullOrBlank() &&
                !cols.getOrNull(6).isNullOrBlank() &&
                !cols.getOrNull(7).isNullOrBlank() &&
                !(cols.getOrNull(9).isNullOrBlank() && cols.getOrNull(8).isNullOrBlank())
        }
    }

    private fun isTc01Style(version: String): Boolean {
        return version == "JAHISTC01" || version == "JAHISTC07"
    }
}

sealed class CheckResult {
    abstract val drugRpNumbers: Set<Int>
    abstract val usageRpNumbers: Set<Int>

    data class Complete(
        override val drugRpNumbers: Set<Int>,
        override val usageRpNumbers: Set<Int>
    ) : CheckResult()

    data class Incomplete(
        val issues: List<CheckIssue>,
        override val drugRpNumbers: Set<Int>,
        override val usageRpNumbers: Set<Int>
    ) : CheckResult()
}

sealed class CheckIssue {
    data object MissingHeader : CheckIssue()
    data object NoRpRecords : CheckIssue()
    data class MissingRp(val rpNumbers: List<Int>) : CheckIssue()
    data class MissingUsageForRp(val rpNumber: Int) : CheckIssue()
    data class OrphanUsageRecord(val rpNumber: Int) : CheckIssue()
    data class MalformedDrugRecord(val rpNumber: Int) : CheckIssue()
    data class MalformedUsageRecord(val rpNumber: Int) : CheckIssue()
}
