package com.example.yakuzaiapp.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.yakuzaiapp.data.local.dao.AuditDrugPreferenceDao
import com.example.yakuzaiapp.data.local.dao.DrugPreferenceDao
import com.example.yakuzaiapp.data.local.dao.DrugMasterDao
import com.example.yakuzaiapp.data.local.dao.FillHistoryDao
import com.example.yakuzaiapp.data.local.dao.MatchingDetailDao
import com.example.yakuzaiapp.data.local.dao.MatchingHistoryDao
import com.example.yakuzaiapp.data.local.dao.SalesPackageDao
import com.example.yakuzaiapp.data.local.dao.StaffMasterDao
import com.example.yakuzaiapp.data.local.entity.AuditDrugPreference
import com.example.yakuzaiapp.data.local.entity.DrugMaster
import com.example.yakuzaiapp.data.local.entity.DrugPreference
import com.example.yakuzaiapp.data.local.entity.FillHistory
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
        AuditDrugPreference::class,
        FillHistory::class
    ],
    version = 11,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun drugMasterDao(): DrugMasterDao
    abstract fun drugPreferenceDao(): DrugPreferenceDao
    abstract fun matchingHistoryDao(): MatchingHistoryDao
    abstract fun matchingDetailDao(): MatchingDetailDao
    abstract fun salesPackageDao(): SalesPackageDao
    abstract fun auditDrugPreferenceDao(): AuditDrugPreferenceDao
    abstract fun staffMasterDao(): StaffMasterDao
    abstract fun fillHistoryDao(): FillHistoryDao

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
                    .addMigrations(MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE staff_master ADD COLUMN staffKana TEXT")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS fill_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        completedAt INTEGER NOT NULL,
                        staffId TEXT NOT NULL,
                        staffName TEXT,
                        staffKana TEXT,
                        drugName TEXT NOT NULL,
                        yjCode TEXT,
                        sourceGtin TEXT,
                        targetCode TEXT NOT NULL,
                        expirationDate TEXT,
                        status TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE staff_master ADD COLUMN staffLastName TEXT")
                db.execSQL("ALTER TABLE staff_master ADD COLUMN staffFirstName TEXT")
                db.execSQL(
                    """
                    UPDATE staff_master
                    SET staffLastName = staffName
                    WHERE staffLastName IS NULL AND staffName IS NOT NULL AND staffName != ''
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE drug_master ADD COLUMN isUserRegistered INTEGER NOT NULL DEFAULT 0"
                )
            }
        }
    }
}
