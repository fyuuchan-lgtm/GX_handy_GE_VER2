package com.example.yakuzaiapp.data.medis

import android.content.Context

data class MedisUpdateMetadata(
    val lastHomepageAccessSuccessAt: Long = 0L,
    val lastImportSuccessAt: Long = 0L,
    val hotVersionDate: String? = null,
    val salesVersionDate: String? = null,
    val hotUrl: String? = null,
    val salesUrl: String? = null,
)

interface MedisUpdateMetadataStore {
    fun read(): MedisUpdateMetadata
    fun markHomepageAccessSuccess(timestampMillis: Long)
    fun markImportSuccess(result: MedisAutoImportResult, timestampMillis: Long)
}

class SharedPreferencesMedisUpdateMetadataStore(
    context: Context,
) : MedisUpdateMetadataStore {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun read(): MedisUpdateMetadata {
        return MedisUpdateMetadata(
            lastHomepageAccessSuccessAt = preferences.getLong(KEY_LAST_HOMEPAGE_ACCESS_SUCCESS_AT, 0L),
            lastImportSuccessAt = preferences.getLong(KEY_LAST_IMPORT_SUCCESS_AT, 0L),
            hotVersionDate = preferences.getString(KEY_HOT_VERSION_DATE, null),
            salesVersionDate = preferences.getString(KEY_SALES_VERSION_DATE, null),
            hotUrl = preferences.getString(KEY_HOT_URL, null),
            salesUrl = preferences.getString(KEY_SALES_URL, null),
        )
    }

    override fun markHomepageAccessSuccess(timestampMillis: Long) {
        preferences.edit()
            .putLong(KEY_LAST_HOMEPAGE_ACCESS_SUCCESS_AT, timestampMillis)
            .apply()
    }

    override fun markImportSuccess(result: MedisAutoImportResult, timestampMillis: Long) {
        preferences.edit()
            .putLong(KEY_LAST_IMPORT_SUCCESS_AT, timestampMillis)
            .putString(KEY_HOT_VERSION_DATE, result.hotVersionDate)
            .putString(KEY_SALES_VERSION_DATE, result.salesVersionDate)
            .putString(KEY_HOT_URL, result.hotUrl)
            .putString(KEY_SALES_URL, result.salesUrl)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "medis_auto_update"
        const val KEY_LAST_HOMEPAGE_ACCESS_SUCCESS_AT = "lastHomepageAccessSuccessAt"
        const val KEY_LAST_IMPORT_SUCCESS_AT = "lastImportSuccessAt"
        const val KEY_HOT_VERSION_DATE = "hotVersionDate"
        const val KEY_SALES_VERSION_DATE = "salesVersionDate"
        const val KEY_HOT_URL = "hotUrl"
        const val KEY_SALES_URL = "salesUrl"
    }
}
