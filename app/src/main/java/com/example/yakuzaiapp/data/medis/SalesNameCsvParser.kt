package com.example.yakuzaiapp.data.medis

import android.util.Log
import com.example.yakuzaiapp.data.local.entity.SalesPackage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

object SalesNameCsvParser {
    private const val TAG = "SalesNameCsvParser"
    private const val MIN_COLUMN_COUNT = 43
    private val SHIFT_JIS: Charset = Charset.forName("Shift_JIS")
    private val UTF_8: Charset = Charsets.UTF_8

    sealed class ImportProgress {
        data object Reading : ImportProgress()
        data class Parsing(val processedLines: Int, val totalLines: Int) : ImportProgress()
        data class Completed(
            val totalRecords: Int,
            val skippedRecords: Int,
        ) : ImportProgress()
        data class Failed(val message: String) : ImportProgress()
    }

    fun parse(inputStream: InputStream): Sequence<SalesPackage> = sequence {
        createReader(inputStream).use { reader ->
            reader.lineSequence()
                .drop(1)
                .forEach { line ->
                    val parsed = parseLine(line)
                    if (parsed != null) {
                        yield(parsed)
                    }
                }
        }
    }

    fun parseWithProgress(inputStream: InputStream): Flow<ImportProgress> = flow {
        emit(ImportProgress.Reading)
        var totalRecords = 0
        var skippedRecords = 0

        createReader(inputStream).use { reader ->
            reader.lineSequence()
                .drop(1)
                .forEachIndexed { index, line ->
                    emit(ImportProgress.Parsing(processedLines = index + 1, totalLines = -1))
                    val parsed = parseLine(line)
                    if (parsed != null) {
                        totalRecords++
                    } else {
                        skippedRecords++
                    }
                }
        }

        emit(
            ImportProgress.Completed(
                totalRecords = totalRecords,
                skippedRecords = skippedRecords,
            )
        )
    }.flowOn(Dispatchers.IO)

    private fun parseLine(line: String): SalesPackage? {
        if (line.isBlank()) return null
        val cols = splitCsvLine(line).normalizeToMinimumColumns()

        val yjCode = cols[8].takeIfNotBlank()
        val gtin = cols[29].takeIfNotBlank()
        val drugName = cols[3].takeIfNotBlank()
        if (yjCode == null) {
            Log.w(TAG, "Skip line without yjCode")
            return null
        }
        if (gtin == null) {
            Log.w(TAG, "Skip line without gtin")
            return null
        }
        if (drugName == null) {
            Log.w(TAG, "Skip line without packageName")
            return null
        }

        return SalesPackage(
            gtin = gtin,
            yjCode = yjCode,
            gtinSales = cols[32].takeIfNotBlank(),
            gtinCase = cols[33].takeIfNotBlank(),
            janCode = cols[34].takeIfNotBlank(),
            packageName = drugName,
            drugNameKana1 = cols[4].takeIfNotBlank(),
            drugNameKana2 = cols[5].takeIfNotBlank(),
            drugNameKana3 = cols[6].takeIfNotBlank(),
            maker = cols[2].takeIfNotBlank(),
            packageSpec = cols[12].takeIfNotBlank(),
            drugCategory = cols[15].takeIfNotBlank(),
            dosageForm = cols[16].takeIfNotBlank(),
            packageForm = cols[22].takeIfNotBlank(),
            packageUnitCount = cols[23].toIntOrNull(),
            packageUnitName = cols[24].takeIfNotBlank(),
            quantity = cols[25].toIntOrNull(),
            unit = cols[26].takeIfNotBlank(),
            containerCount = cols[27].toIntOrNull(),
            solventName = cols[17].takeIfNotBlank(),
            solventVolume = cols[18].takeIfNotBlank(),
            solventUnit = cols[19].takeIfNotBlank(),
            biologicalFlag = cols[20].takeIfNotBlank(),
            narcoticFlag = cols[21].takeIfNotBlank(),
            noticeDate = cols[10].takeIfNotBlank(),
            transitionDate = cols[11].takeIfNotBlank(),
            discontinuedDate = cols.salesDiscontinuedDate(),
            lastLotExpiry = cols.salesLastLotExpiry(),
            medisUpdateDate = cols[1].takeIfNotBlank(),
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

    private fun createReader(inputStream: InputStream): BufferedReader {
        val bytes = inputStream.readBytes()
        val charset = detectCharset(bytes)
        Log.i(TAG, "Detected sales-name charset=$charset bytes=${bytes.size}")
        return BufferedReader(InputStreamReader(ByteArrayInputStream(bytes), charset))
    }

    private fun detectCharset(bytes: ByteArray): Charset {
        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        ) {
            return UTF_8
        }

        val validUtf8 = runCatching {
            UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
            true
        }.getOrDefault(false)

        return if (validUtf8) UTF_8 else SHIFT_JIS
    }

    private fun List<String>.normalizeToMinimumColumns(): List<String> {
        return when {
            size >= MIN_COLUMN_COUNT -> this
            else -> {
                Log.w(TAG, "Padding short line: cols=$size expected=$MIN_COLUMN_COUNT")
                this + List(MIN_COLUMN_COUNT - size) { "" }
            }
        }
    }

    private fun List<String>.salesDiscontinuedDate(): String? {
        val index = when {
            size >= 45 -> 43
            size >= 44 -> 42
            else -> 41
        }
        return getOrNull(index)?.takeIfNotBlank()
    }

    private fun List<String>.salesLastLotExpiry(): String? {
        val index = when {
            size >= 45 -> 44
            size >= 44 -> 43
            else -> 42
        }
        return getOrNull(index)?.takeIfNotBlank()
    }

    private fun String.takeIfNotBlank(): String? = trim().takeIf { it.isNotBlank() }
}
