package com.example.yakuzaiapp

import android.app.Application
import com.example.yakuzaiapp.data.local.AppDatabase
import com.example.yakuzaiapp.data.repository.DrugPreferenceRepository
import com.example.yakuzaiapp.repository.DrugMasterRepository

class YakuzaiApplication : Application() {
    val database by lazy { AppDatabase.getInstance(this) }
    val drugMasterRepository by lazy {
        DrugMasterRepository(
            drugMasterDao = database.drugMasterDao(),
            salesPackageDao = database.salesPackageDao(),
        )
    }
    val drugPreferenceRepository by lazy { DrugPreferenceRepository(database.drugPreferenceDao()) }
}
