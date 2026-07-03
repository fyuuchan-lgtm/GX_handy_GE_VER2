package com.example.yakuzaiapp.data.medis

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.yakuzaiapp.data.local.dao.SalesPackageDao
import com.example.yakuzaiapp.data.local.entity.SalesPackage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.IOException

class SalesNameImportRepository(
    private val context: Context?,
    private val dao: SalesPackageDao,
    private val transactionRunner: suspend (suspend () -> Unit) -> Unit = { block -> block() },
) {
    internal constructor(
        dao: SalesPackageDao,
        transactionRunner: suspend (suspend () -> Unit) -> Unit = { block -> block() },
    ) : this(context = null, dao = dao, transactionRunner = transactionRunner)

    companion object {
        private const val TAG = "SalesNameImportRepository"
        private const val BATCH_SIZE = 1_000
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
            val elapsedMs: Long,
        ) : Progress()
        data class Failed(val message: String) : Progress()
    }

    fun importFromUri(uri: Uri): Flow<Progress> = flow {
        val appContext = requireNotNull(context) {
            "Context is required for URI imports."
        }
        importInternal(
            inputStreamProvider = {
                appContext.contentResolver.openInputStream(uri)
                    ?: throw IOException("Cannot open URI: $uri")
            },
            fileLabel = uri.toString(),
        )
    }.flowOn(Dispatchers.IO)

    internal fun importFromStreamForTest(
        inputStreamProvider: () -> java.io.InputStream,
        fileLabel: String,
    ): Flow<Progress> = flow {
        importInternal(inputStreamProvider, fileLabel)
    }

    suspend fun count(): Int = dao.count()

    private suspend fun FlowCollector<Progress>.importInternal(
        inputStreamProvider: () -> java.io.InputStream,
        fileLabel: String,
    ) {
        val startTime = System.currentTimeMillis()
        try {
            emit(Progress.Reading)
            Log.i(TAG, "全件削除完了")

            val records = inputStreamProvider().use { stream ->
                SalesNameCsvParser.parse(stream).toList()
            }
            if (records.isEmpty()) {
                throw IOException("販売名ファイルに取り込める行がありません。")
            }

            emit(
                Progress.Parsing(
                    processed = records.size,
                    total = ESTIMATED_TOTAL_LINES,
                )
            )

            Log.i(TAG, "Parse完了: file=$fileLabel records=${records.size}")

            emit(Progress.Deleting)
            replaceAll(records) { inserted ->
                emit(Progress.Inserting(inserted = inserted, total = records.size))
            }

            val elapsedMs = System.currentTimeMillis() - startTime
            emit(
                Progress.Completed(
                    totalRecords = records.size,
                    skippedRecords = 0,
                    elapsedMs = elapsedMs,
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Import failed: $fileLabel", e)
            emit(Progress.Failed(e.message ?: "不明なエラー"))
        }
    }

    private suspend fun replaceAll(
        records: List<SalesPackage>,
        onInserted: suspend (Int) -> Unit,
    ) {
        transactionRunner {
            dao.deleteAll()
            var inserted = 0
            records.chunked(BATCH_SIZE).forEach { batch ->
                dao.upsertAll(batch)
                inserted += batch.size
                onInserted(inserted)
            }
        }
    }
}
