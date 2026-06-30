package com.example.yakuzaiapp.data.repository

import com.example.yakuzaiapp.data.local.dao.DrugPreferenceDao
import com.example.yakuzaiapp.data.local.entity.DrugPreference

class DrugPreferenceRepository(private val dao: DrugPreferenceDao) {
    suspend fun isPackingMachine(yjCode: String): Boolean {
        return dao.findByYjCode(yjCode)?.defaultPackingMachine ?: false
    }

    suspend fun setPackingMachine(yjCode: String, enabled: Boolean) {
        dao.upsert(DrugPreference(yjCode, enabled, System.currentTimeMillis()))
    }

    suspend fun removeYjCode(yjCode: String) {
        dao.deleteByYjCode(yjCode)
    }
}
