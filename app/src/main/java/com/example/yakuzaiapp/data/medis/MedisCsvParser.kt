package com.example.yakuzaiapp.data.medis

import android.util.Log
import com.example.yakuzaiapp.data.local.entity.DrugMaster
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.Charset

class MedisCsvParser {
    companion object {
        private const val TAG = "MedisCsvParser"
        private const val EXPECTED_OLD_COLUMN_COUNT = 44
        private const val EXPECTED_NEW_COLUMN_COUNT = 24
        private val SHIFT_JIS: Charset = Charset.forName("Shift_JIS")
    }

    data class ParseResult(
        val records: List<DrugMaster>,
        val totalLines: Int,
        val successCount: Int,
        val skipCount: Int,
        val errorLines: List<Pair<Int, String>>,
    )

    fun interface ProgressCallback {
        fun onProgress(processedLines: Int, totalLines: Int)
    }

    fun parse(
        inputStream: InputStream,
        estimatedTotalLines: Int = -1,
        progressCallback: ProgressCallback? = null,
    ): ParseResult {
        val records = mutableListOf<DrugMaster>()
        val errorLines = mutableListOf<Pair<Int, String>>()
        var lineNumber = 0
        var successCount = 0
        var skipCount = 0

        BufferedReader(InputStreamReader(inputStream, SHIFT_JIS)).use { reader ->
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                lineNumber++
                val rawLine = line ?: continue

                if (rawLine.isBlank()) {
                    skipCount++
                    continue
                }

                try {
                    val drugMaster = parseLine(rawLine)
                    if (drugMaster != null) {
                        records.add(drugMaster)
                        successCount++
                    } else {
                        skipCount++
                    }
                } catch (e: Exception) {
                    skipCount++
                    if (errorLines.size < 100) {
                        errorLines.add(lineNumber to (e.message ?: "unknown"))
                    }
                    Log.w(TAG, "Parse error at line $lineNumber: ${e.message}")
                }

                if (lineNumber % 500 == 0) {
                    progressCallback?.onProgress(lineNumber, estimatedTotalLines)
                }
            }
        }

        progressCallback?.onProgress(lineNumber, lineNumber)

        return ParseResult(
            records = records,
            totalLines = lineNumber,
            successCount = successCount,
            skipCount = skipCount,
            errorLines = errorLines,
        )
    }

    private fun parseLine(line: String): DrugMaster? {
        val cols = splitCsvLine(line)
        if (cols.size >= EXPECTED_NEW_COLUMN_COUNT) {
            return parseNewFormat(cols)
        }
        if (cols.size >= EXPECTED_OLD_COLUMN_COUNT) {
            return parseOldFormat(cols)
        }
        throw IllegalArgumentException("カラム不足: ${cols.size}")
    }

    private fun parseNewFormat(cols: List<String>): DrugMaster? {
        val hot13 = cols.getOrNull(0).orEmpty().trim()
        val hot7 = cols.getOrNull(1).orEmpty().trim()
        val priceCode = cols.getOrNull(6).orEmpty().trim()
        val individualCode = cols.getOrNull(7).orEmpty().trim()
        val drugName = cols.getOrNull(11).orEmpty().trim()
        val packageName = cols.getOrNull(12).orEmpty().trim().takeIf { it.isNotBlank() }
        val packageSpec = cols.getOrNull(13).orEmpty().trim()
        val packageForm = cols.getOrNull(14).orEmpty().trim().takeIf { it.isNotBlank() }
        val packageUnitCount = cols.getOrNull(15).orEmpty().trim().toIntOrNull()
        val packageUnitName = cols.getOrNull(16).orEmpty().trim().takeIf { it.isNotBlank() }
        val containerCount = cols.getOrNull(17).orEmpty().trim().toIntOrNull()
        val drugCategory = cols.getOrNull(19).orEmpty().trim().takeIf { it.isNotBlank() }
        val maker = cols.getOrNull(20).orEmpty().trim().takeIf { it.isNotBlank() }
        val transitionDate = cols.getOrNull(23).orEmpty().trim().takeIf { it.isNotBlank() }

        if (hot13.isBlank()) return null
        if (priceCode.isBlank()) return null
        if (drugName.isBlank()) return null

        return DrugMaster(
            hot13 = hot13,
            gtin = hot13,
            drugCode = priceCode,
            drugName = drugName,
            maker = maker,
            packageName = packageName,
            alias = packageName,
            packageSpec = packageSpec,
            quantity = packageUnitCount,
            unit = packageUnitName,
            yjCode = individualCode.ifBlank { priceCode },
            janCode = null,
            gtinSales = hot7.takeIf { it.isNotBlank() },
            gtinCase = null,
            drugCategory = drugCategory,
            dosageForm = null,
            packageForm = packageForm,
            packageUnitCount = packageUnitCount,
            packageUnitName = packageUnitName,
            containerCount = containerCount,
            narcoticFlag = null,
            biologicalFlag = null,
            solventName = null,
            solventVolume = null,
            solventUnit = null,
            noticeDate = null,
            transitionDate = transitionDate,
            discontinuedDate = null,
            lastLotExpiry = null,
            medisUpdateDate = null,
            year = null,
            updatedAt = System.currentTimeMillis(),
        )
    }

    private fun parseOldFormat(cols: List<String>): DrugMaster? {
        val medisUpdateDate = cols.getOrNull(1).orEmpty().trim()
        val maker = cols.getOrNull(2).orEmpty().trim()
        val drugName = cols.getOrNull(3).orEmpty().trim()
        val kana1 = cols.getOrNull(4).orEmpty().trim().takeIf { it.isNotBlank() }
        val kana2 = cols.getOrNull(5).orEmpty().trim().takeIf { it.isNotBlank() }
        val kana3 = cols.getOrNull(6).orEmpty().trim().takeIf { it.isNotBlank() }
        val yjCode = cols.getOrNull(8).orEmpty().trim()
        val noticeDate = cols.getOrNull(10).orEmpty().trim().takeIf { it.isNotBlank() }
        val transitionDate = cols.getOrNull(11).orEmpty().trim().takeIf { it.isNotBlank() }
        val packageSpec = cols.getOrNull(12).orEmpty().trim()
        val drugCategory = cols.getOrNull(15).orEmpty().trim().takeIf { it.isNotBlank() }
        val dosageForm = cols.getOrNull(16).orEmpty().trim().takeIf { it.isNotBlank() }
        val solventName = cols.getOrNull(17).orEmpty().trim().takeIf { it.isNotBlank() }
        val solventVolume = cols.getOrNull(18).orEmpty().trim().takeIf { it.isNotBlank() }
        val solventUnit = cols.getOrNull(19).orEmpty().trim().takeIf { it.isNotBlank() }
        val biologicalFlag = cols.getOrNull(20).orEmpty().trim().takeIf { it.isNotBlank() }
        val narcoticFlag = cols.getOrNull(21).orEmpty().trim().takeIf { it.isNotBlank() }
        val packageForm = cols.getOrNull(22).orEmpty().trim().takeIf { it.isNotBlank() }
        val packageUnitCount = cols.getOrNull(23).orEmpty().trim().toIntOrNull()
        val packageUnitName = cols.getOrNull(24).orEmpty().trim().takeIf { it.isNotBlank() }
        val containerCount = cols.getOrNull(25).orEmpty().trim().toIntOrNull()
        val gtin = cols.getOrNull(29).orEmpty().trim()
        val packageName = cols.getOrNull(30).orEmpty().trim().takeIf { it.isNotBlank() }
        val salesPackageGtin = cols.getOrNull(32).orEmpty().trim().takeIf { it.isNotBlank() }
        val caseGtin = cols.getOrNull(33).orEmpty().trim().takeIf { it.isNotBlank() }
        val janCode = cols.getOrNull(34).orEmpty().trim().takeIf { it.isNotBlank() }
        val discontinuedDate = cols.getOrNull(42).orEmpty().trim().takeIf { it.isNotBlank() }
        val lastLotExpiry = cols.getOrNull(43).orEmpty().trim().takeIf { it.isNotBlank() }

        if (gtin.isBlank()) return null
        if (yjCode.isBlank()) return null
        if (drugName.isBlank()) return null

        return DrugMaster(
            hot13 = gtin,
            drugCode = yjCode,
            drugName = drugName,
            maker = maker,
            packageName = packageName,
            drugNameKana1 = kana1,
            drugNameKana2 = kana2,
            drugNameKana3 = kana3,
            alias = packageName,
            packageSpec = packageSpec,
            quantity = packageUnitCount,
            unit = packageUnitName,
            price = null,
            yjCode = yjCode,
            janCode = janCode,
            gtin = gtin,
            gtinSales = salesPackageGtin,
            gtinCase = caseGtin,
            drugCategory = drugCategory,
            dosageForm = dosageForm,
            packageForm = packageForm,
            packageUnitCount = packageUnitCount,
            packageUnitName = packageUnitName,
            containerCount = containerCount,
            narcoticFlag = narcoticFlag,
            biologicalFlag = biologicalFlag,
            solventName = solventName,
            solventVolume = solventVolume,
            solventUnit = solventUnit,
            noticeDate = noticeDate,
            transitionDate = transitionDate,
            discontinuedDate = discontinuedDate,
            lastLotExpiry = lastLotExpiry,
            medisUpdateDate = medisUpdateDate,
            year = null,
            updatedAt = System.currentTimeMillis(),
        )
    }

    private fun splitCsvLine(line: String): List<String> {
        val columns = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var index = 0

        while (index < line.length) {
            val ch = line[index]
            when (ch) {
                '"' -> {
                    if (inQuotes && index + 1 < line.length && line[index + 1] == '"') {
                        current.append('"')
                        index++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                ',' -> {
                    if (inQuotes) {
                        current.append(ch)
                    } else {
                        columns += current.toString().trim().trim('"')
                        current.setLength(0)
                    }
                }
                else -> current.append(ch)
            }
            index++
        }

        columns += current.toString().trim().trim('"')
        return columns
    }
}
