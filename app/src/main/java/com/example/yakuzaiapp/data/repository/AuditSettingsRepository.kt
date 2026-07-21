package com.example.yakuzaiapp.data.repository

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AuditSettingsRepository(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _readQuantityFromDocument = MutableStateFlow(
        preferences.getBoolean(KEY_READ_QUANTITY_FROM_DOCUMENT, true)
    )
    val readQuantityFromDocument: StateFlow<Boolean> = _readQuantityFromDocument.asStateFlow()

    fun setReadQuantityFromDocument(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_READ_QUANTITY_FROM_DOCUMENT, enabled).apply()
        _readQuantityFromDocument.value = enabled
    }

    private companion object {
        const val PREFS_NAME = "audit_settings"
        const val KEY_READ_QUANTITY_FROM_DOCUMENT = "read_quantity_from_document"
    }
}
