package com.example.yakuzaiapp.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.yakuzaiapp.data.local.dao.AuditDrugPreferenceDao
import com.example.yakuzaiapp.data.local.dao.DrugPreferenceDao
import com.example.yakuzaiapp.data.local.dao.DrugMasterDao
import com.example.yakuzaiapp.data.local.dao.MatchingDetailDao
import com.example.yakuzaiapp.data.local.dao.MatchingHistoryDao
import com.example.yakuzaiapp.data.local.dao.SalesPackageDao
import com.example.yakuzaiapp.data.local.entity.AuditDrugPreference
import com.example.yakuzaiapp.data.local.entity.DrugMaster
import com.example.yakuzaiapp.data.local.entity.DrugPreference
import com.example.yakuzaiapp.data.local.entity.MatchingDetail
import com.example.yakuzaiapp.data.local.entity.MatchingHistory
import com.example.yakuzaiapp.data.local.entity.StaffMaster
import com.example.yakuzaiapp.data.local.entity.SalesPackage

@Database(
    entities = [
        DrugMaster::class,
        DrugPreference::class,
        StaffMaster::class,
        MatchingHistory::class,
        MatchingDetail::class,
        SalesPackage::class,
        AuditDrugPreference::class
    ],
    version = 8,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun drugMasterDao(): DrugMasterDao
    abstract fun drugPreferenceDao(): DrugPreferenceDao
    abstract fun matchingHistoryDao(): MatchingHistoryDao
    abstract fun matchingDetailDao(): MatchingDetailDao
    abstract fun salesPackageDao(): SalesPackageDao
    abstract fun auditDrugPreferenceDao(): AuditDrugPreferenceDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "yakuzaiapp.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
