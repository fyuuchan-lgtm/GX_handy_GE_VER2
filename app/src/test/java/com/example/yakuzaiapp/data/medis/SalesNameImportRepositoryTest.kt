package com.example.yakuzaiapp.data.medis

import com.example.yakuzaiapp.data.local.dao.SalesPackageDao
import com.example.yakuzaiapp.data.local.entity.SalesPackage
import java.io.ByteArrayInputStream
import java.io.IOException
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SalesNameImportRepositoryTest {
    @Test
    fun failedReadDoesNotDeleteExistingRows() = runTest {
        val dao = FakeSalesPackageDao()
        val repository = SalesNameImportRepository(dao)

        val progress = repository.importFromStreamForTest(
            inputStreamProvider = { throw IOException("cannot read") },
            fileLabel = "broken.csv",
        ).toList()

        assertFalse(dao.deleteAllCalled)
        assertTrue(progress.last() is SalesNameImportRepository.Progress.Failed)
    }

    @Test
    fun emptyParsedFileDoesNotDeleteExistingRows() = runTest {
        val dao = FakeSalesPackageDao()
        val repository = SalesNameImportRepository(dao)

        val progress = repository.importFromStreamForTest(
            inputStreamProvider = { ByteArrayInputStream(ByteArray(0)) },
            fileLabel = "empty.csv",
        ).toList()

        assertFalse(dao.deleteAllCalled)
        assertTrue(progress.last() is SalesNameImportRepository.Progress.Failed)
    }

    @Test
    fun validImportDeletesThenInsertsRows() = runTest {
        val dao = FakeSalesPackageDao()
        val repository = SalesNameImportRepository(dao)

        val progress = repository.importFromStreamForTest(
            inputStreamProvider = {
                requireNotNull(
                    javaClass.getResourceAsStream(
                        "/com/example/yakuzaiapp/data/medis/sales_name_fixture_sjis.csv",
                    ),
                )
            },
            fileLabel = "sales_name_fixture_sjis.csv",
        ).toList()

        assertTrue(dao.deleteAllCalled)
        assertEquals(2, dao.inserted.size)
        assertTrue(progress.last() is SalesNameImportRepository.Progress.Completed)
    }

    @Test
    fun insertFailureRollsBackDeletedRows() = runTest {
        val existing = SalesPackage(gtin = "04900000000001", yjCode = "0000000A0000")
        val dao = FakeSalesPackageDao(initialRows = listOf(existing), failOnUpsert = true)
        val repository = SalesNameImportRepository(
            dao = dao,
            transactionRunner = { block ->
                val snapshot = dao.snapshot()
                try {
                    block()
                } catch (e: Exception) {
                    dao.restore(snapshot)
                    throw e
                }
            },
        )

        val progress = repository.importFromStreamForTest(
            inputStreamProvider = {
                requireNotNull(
                    javaClass.getResourceAsStream(
                        "/com/example/yakuzaiapp/data/medis/sales_name_fixture_sjis.csv",
                    ),
                )
            },
            fileLabel = "sales_name_fixture_sjis.csv",
        ).toList()

        assertTrue(dao.deleteAllCalled)
        assertEquals(listOf(existing), dao.inserted)
        assertTrue(progress.last() is SalesNameImportRepository.Progress.Failed)
    }

    private class FakeSalesPackageDao(
        initialRows: List<SalesPackage> = emptyList(),
        private val failOnUpsert: Boolean = false,
    ) : SalesPackageDao {
        val inserted = initialRows.toMutableList()
        var deleteAllCalled = false

        override suspend fun upsertAll(items: List<SalesPackage>) {
            if (failOnUpsert) {
                throw IOException("insert failed")
            }
            inserted += items
        }

        override suspend fun findByGtin(gtin: String): SalesPackage? = null

        override suspend fun findByAnyGtin(code: String): SalesPackage? = null

        override suspend fun findBySalesPackageGtin(gtin: String): SalesPackage? = null

        override suspend fun findByCaseGtin(gtin: String): SalesPackage? = null

        override suspend fun findAllByYjCode(yjCode: String): List<SalesPackage> = emptyList()

        override suspend fun findByJanCode(janCode: String): SalesPackage? = null

        override suspend fun count(): Int = inserted.size

        override suspend fun deleteAll() {
            deleteAllCalled = true
            inserted.clear()
        }

        fun snapshot(): List<SalesPackage> = inserted.toList()

        fun restore(rows: List<SalesPackage>) {
            inserted.clear()
            inserted += rows
        }
    }
}
