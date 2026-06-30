package com.example.yakuzaiapp.domain.jahis

import android.util.Log
import com.example.yakuzaiapp.data.jahis.JahisQrParser

data class DetectedQr(
    val text: String,
    val left: Int,
    val saSequence: Int? = null,
    val saTotal: Int? = null,
    val saParity: Int? = null,
)

sealed class AssembleResult {
    data class Success(val fullText: String) : AssembleResult()
    data class Incomplete(
        val fragmentCount: Int,
        val reason: IncompleteReason,
        val detailMessage: String? = null
    ) : AssembleResult()
    data object NoHeader : AssembleResult()
    data class ParseFailed(val message: String) : AssembleResult()
}

enum class IncompleteReason {
    MISSING_HEADER,
    UNTERMINATED,
    MISSING_USAGE,
    MISSING_RP,
    MISSING_DRUG_RECORD
}

object JahisQrAssembler {
    private val RECORD_START_PATTERN = Regex("^(JAHISTC\\d{2}|\\d+,)")

    fun tryAssemble(qrs: List<DetectedQr>): AssembleResult {
        val filtered = qrs
            .asSequence()
            .filter { it.text.isNotBlank() }
            .distinctBy { "${it.left}:${it.text}" }
            .toList()
        val (headers, others) = filtered.partition { hasSupportedHeader(it.text) }
        val ordered = headers.sortedBy { it.left } + others.sortedBy { it.left }

        if (ordered.isEmpty()) {
            return AssembleResult.NoHeader
        }

        if (headers.isEmpty()) {
            return AssembleResult.Incomplete(ordered.size, IncompleteReason.MISSING_HEADER)
        }

        val fullText = buildFullText(ordered)

        when (val completeness = JahisCompletenessChecker.check(fullText)) {
            is CheckResult.Complete -> Unit
            is CheckResult.Incomplete -> {
                val reason = completeness.toIncompleteReason()
                val detail = JahisCompletenessChecker.describe(completeness.issues)
                debugLog("result: Incomplete reason=$reason detail=$detail fragmentCount=${ordered.size}")
                return AssembleResult.Incomplete(ordered.size, reason, detail)
            }
        }

        return try {
            val parsed = JahisQrParser.parse(fullText)
            if (parsed.rps.isEmpty()) {
                AssembleResult.ParseFailed("parsed prescription had no RP items")
            } else {
                val itemCount = parsed.rps.sumOf { it.drugs.size }
                debugLog("result: Success itemCount=$itemCount")
                AssembleResult.Success(fullText)
            }
        } catch (e: Throwable) {
            debugLog("result: ParseFailed(${e.message})")
            AssembleResult.ParseFailed(e.message ?: "JAHIS QR parse failed")
        }
    }

    private fun debugLog(message: String) {
        runCatching { Log.d("JahisQrAssembler", message) }
    }

    private fun CheckResult.Incomplete.toIncompleteReason(): IncompleteReason {
        return when {
            issues.any { it is CheckIssue.MissingHeader } -> IncompleteReason.MISSING_HEADER
            issues.any { it is CheckIssue.MissingRp } -> IncompleteReason.MISSING_RP
            issues.any { it is CheckIssue.MalformedDrugRecord } -> IncompleteReason.MISSING_DRUG_RECORD
            issues.any { it is CheckIssue.MalformedUsageRecord } -> IncompleteReason.MISSING_USAGE
            issues.any { it is CheckIssue.MissingUsageForRp } -> IncompleteReason.MISSING_USAGE
            issues.any { it is CheckIssue.OrphanUsageRecord } -> IncompleteReason.MISSING_DRUG_RECORD
            else -> IncompleteReason.MISSING_USAGE
        }
    }

    private fun buildFullText(ordered: List<DetectedQr>): String {
        val sb = StringBuilder()
        ordered.forEachIndexed { index, fragment ->
            if (index > 0) {
                val prev = ordered[index - 1].text
                val prevEndsWithNewline = prev.endsWith("\n") || prev.endsWith("\r")
                val nextStartsRecord = RECORD_START_PATTERN.containsMatchIn(fragment.text)
                if (!prevEndsWithNewline && nextStartsRecord) {
                    sb.append('\n')
                }
            }
            sb.append(fragment.text)
        }
        return sb.toString()
    }

    private fun hasSupportedHeader(text: String): Boolean {
        return text.startsWith("JAHISTC01") ||
            text.startsWith("JAHISTC02") ||
            text.startsWith("JAHISTC07")
    }
}
