package com.example.yakuzaiapp.repository

import com.example.yakuzaiapp.data.local.dao.MatchingDetailDao
import com.example.yakuzaiapp.data.local.dao.MatchingHistoryDao
import com.example.yakuzaiapp.data.local.entity.MatchingDetail
import com.example.yakuzaiapp.data.local.entity.MatchingHistory

class MatchingRepository(
    private val historyDao: MatchingHistoryDao,
    private val detailDao: MatchingDetailDao
) {
    suspend fun startHistory(
        staffId: String?,
        imageFilePath: String?,
        totalItems: Int
    ): Long {
        return historyDao.insert(
            MatchingHistory(
                startedAt = System.currentTimeMillis(),
                staffId = staffId,
                imageFilePath = imageFilePath,
                totalItems = totalItems,
                matchedItems = 0,
                status = "RUNNING"
            )
        )
    }

    suspend fun saveDetails(items: List<MatchingDetail>) {
        detailDao.insertAll(items)
    }

    suspend fun finishHistory(id: Long, matchedItems: Int, status: String) {
        historyDao.finish(id, System.currentTimeMillis(), matchedItems, status)
    }
}

