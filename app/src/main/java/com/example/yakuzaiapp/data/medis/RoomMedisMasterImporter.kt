package com.example.yakuzaiapp.data.medis

import androidx.room.withTransaction
import com.example.yakuzaiapp.data.local.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

interface MedisMasterImporter {
    suspend fun importMasters(
        links: MedisDownloadLinks,
        hotZipBytes: ByteArray,
        salesBytes: ByteArray,
        onProgress: (MedisAutoUpdateState.Running) -> Unit = {},
    ): MedisAutoImportResult
}

class RoomMedisMasterImporter(
    private val database: AppDatabase,
) : MedisMasterImporter {
    override suspend fun importMasters(
        links: MedisDownloadLinks,
        hotZipBytes: ByteArray,
        salesBytes: ByteArray,
        onProgress: (MedisAutoUpdateState.Running) -> Unit,
    ): MedisAutoImportResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        onProgress(
            MedisAutoUpdateState.Running(
                phase = MedisAutoUpdateState.Phase.Parsing,
                message = "MEDIS HOT を解析中...",
            )
        )
        val hotTextBytes = extractHotTextBytes(hotZipBytes, links.hotVersionDate)
        val hotRecords = MedisCsvParser().parse(ByteArrayInputStream(hotTextBytes)).records
        require(hotRecords.isNotEmpty()) { "MEDIS HOT の有効レコードが0件です。" }

        onProgress(
            MedisAutoUpdateState.Running(
                phase = MedisAutoUpdateState.Phase.Parsing,
                message = "販売名ファイルを解析中...",
            )
        )
        val salesRecords = SalesNameCsvParser.parse(ByteArrayInputStream(salesBytes)).toList()
        require(salesRecords.isNotEmpty()) { "販売名ファイルの有効レコードが0件です。" }

        onProgress(
            MedisAutoUpdateState.Running(
                phase = MedisAutoUpdateState.Phase.Importing,
                message = "データベースを更新中...",
                processedCount = 0,
                totalCount = hotRecords.size + salesRecords.size,
            )
        )

        database.withTransaction {
            val drugMasterDao = database.drugMasterDao()
            val salesPackageDao = database.salesPackageDao()
            drugMasterDao.deleteAll()
            salesPackageDao.deleteAll()

            var inserted = 0
            hotRecords.chunked(HOT_BATCH_SIZE).forEach { batch ->
                drugMasterDao.upsertAll(batch)
                inserted += batch.size
                onProgress(
                    MedisAutoUpdateState.Running(
                        phase = MedisAutoUpdateState.Phase.Importing,
                        message = "MEDIS HOT を登録中...",
                        processedCount = inserted,
                        totalCount = hotRecords.size + salesRecords.size,
                    )
                )
            }
            salesRecords.chunked(SALES_BATCH_SIZE).forEach { batch ->
                salesPackageDao.upsertAll(batch)
                inserted += batch.size
                onProgress(
                    MedisAutoUpdateState.Running(
                        phase = MedisAutoUpdateState.Phase.Importing,
                        message = "販売名ファイルを登録中...",
                        processedCount = inserted,
                        totalCount = hotRecords.size + salesRecords.size,
                    )
                )
            }
        }

        MedisAutoImportResult(
            hotVersionDate = links.hotVersionDate,
            salesVersionDate = links.salesVersionDate,
            hotUrl = links.hotZipUrl,
            salesUrl = links.salesFileUrl,
            hotRecords = hotRecords.size,
            salesRecords = salesRecords.size,
            elapsedMs = System.currentTimeMillis() - startTime,
        )
    }

    private fun extractHotTextBytes(hotZipBytes: ByteArray, hotVersionDate: String): ByteArray {
        val expectedName = "MEDIS${hotVersionDate}_h.txt"
        ZipInputStream(ByteArrayInputStream(hotZipBytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory && entry.name.endsWith(expectedName, ignoreCase = true)) {
                    val output = ByteArrayOutputStream()
                    zip.copyTo(output)
                    return output.toByteArray()
                }
            }
        }
        throw IllegalStateException("ZIP内に $expectedName が見つかりません。")
    }

    private companion object {
        const val HOT_BATCH_SIZE = 500
        const val SALES_BATCH_SIZE = 1_000
    }
}
