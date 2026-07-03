package com.example.yakuzaiapp

import android.app.Application
import com.example.yakuzaiapp.data.local.AppDatabase
import com.example.yakuzaiapp.data.medis.AndroidNetworkMonitor
import com.example.yakuzaiapp.data.medis.HttpMedisRemoteDataSource
import com.example.yakuzaiapp.data.medis.MedisAutoUpdateCoordinator
import com.example.yakuzaiapp.data.medis.RoomMedisMasterImporter
import com.example.yakuzaiapp.data.medis.SharedPreferencesMedisUpdateMetadataStore
import com.example.yakuzaiapp.data.repository.DrugPreferenceRepository
import com.example.yakuzaiapp.data.repository.StaffSelectionRepository
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
    val staffSelectionRepository by lazy { StaffSelectionRepository(this) }
    val medisAutoUpdateCoordinator by lazy {
        MedisAutoUpdateCoordinator(
            networkMonitor = AndroidNetworkMonitor(this),
            metadataStore = SharedPreferencesMedisUpdateMetadataStore(this),
            remoteDataSource = HttpMedisRemoteDataSource(),
            importer = RoomMedisMasterImporter(database),
        )
    }
}
