package com.example.yakuzaiapp.data.repository

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class StaffSelectionRepository private constructor(
    private val prefs: SharedPreferences?,
    initialSelectedStaffId: String?,
) {
    constructor(context: Context) : this(
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
        initialSelectedStaffId = null,
    )

    internal constructor(initialSelectedStaffId: String?) : this(
        prefs = null,
        initialSelectedStaffId = initialSelectedStaffId,
    )

    private val _selectedStaffId = MutableStateFlow(
        initialSelectedStaffId ?: prefs?.getString(KEY_SELECTED_STAFF_ID, null)
    )
    val selectedStaffId: StateFlow<String?> = _selectedStaffId.asStateFlow()

    fun selectStaff(staffId: String) {
        prefs?.edit()?.putString(KEY_SELECTED_STAFF_ID, staffId)?.apply()
        _selectedStaffId.value = staffId
    }

    companion object {
        private const val PREFS_NAME = "staff_selection"
        private const val KEY_SELECTED_STAFF_ID = "selected_staff_id"
    }
}
