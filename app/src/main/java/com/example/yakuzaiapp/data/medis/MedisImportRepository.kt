package com.example.yakuzaiapp.data.medis

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.yakuzaiapp.data.local.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class MedisImportRepository(
    private val context: Context,
    private val database: AppDatabase,
) {
    companion object {
        private const val TAG = "MedisImportRepository"
        private const val BATCH_SIZE = 500
        private const val ESTIMATED_TOTAL_LINES = 60_000
    }

    sealed class Progress {
        data object Reading : Progress()
        data class Parsing(val processed: Int, val total: Int) : Progress()
        data object Deleting : Progress()
        data class Inserting(val inserted: Int, val total: Int) : Progress()
        data class Completed(
            val totalRecords: Int,
            val skippedRecords: Int,
            val errorLines: List<Pair<Int, String>>,
            val elapsedMs: Long,
        ) : Progress()
        data class Failed(val message: String) : Progress()
    }

    fun import(uri: Uri): Flow<Progress> = flow {
        val startTime = System.currentTimeMillis()

        try {
            emit(Progress.Reading)

            val parser = MedisCsvParser()
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("ファイルを開けませんでした")

            val parseResult = inputStream.use { stream ->
                parser.parse(
                    inputStream = stream,
                    estimatedTotalLines = ESTIMATED_TOTAL_LINES,
                )
            }

            emit(
                Progress.Parsing(
                    processed = parseResult.successCount,
                    total = parseResult.totalLines,
                )
            )

            Log.i(TAG, "Parse完了: 成功=${parseResult.successCount}, スキップ=${parseResult.skipCount}")

            emit(Progress.Deleting)
            val dao = database.drugMasterDao()
            dao.deleteAll()
            Log.i(TAG, "全件削除完了")

            val records = parseResult.records
            val totalToInsert = records.size
            var inserted = 0

            records.chunked(BATCH_SIZE).forEach { batch ->
                dao.upsertAll(batch)
                inserted += batch.size
                emit(Progress.Inserting(inserted = inserted, total = totalToInsert))
            }

            Log.i(TAG, "INSERT完了: $inserted 件")

            val elapsedMs = System.currentTimeMillis() - startTime
            emit(
                Progress.Completed(
                    totalRecords = parseResult.successCount,
                    skippedRecords = parseResult.skipCount,
                    errorLines = parseResult.errorLines,
                    elapsedMs = elapsedMs,
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Import failed", e)
            emit(Progress.Failed(e.message ?: "不明なエラー"))
        }
    }.flowOn(Dispatchers.IO)
}
