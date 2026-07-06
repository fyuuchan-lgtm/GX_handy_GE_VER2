package com.example.yakuzaiapp.data.repository

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class FacilityInfo(
    val name: String = "",
    val postalCode: String = "",
    val prefecture: String = "",
    val city: String = "",
    val town: String = "",
    val streetAddress: String = "",
    val address: String = ""
)

class FacilityRepository(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _facility = MutableStateFlow(readFacility())
    val facility: StateFlow<FacilityInfo> = _facility.asStateFlow()

    fun save(
        name: String,
        postalCode: String,
        prefecture: String,
        city: String,
        town: String,
        streetAddress: String
    ) {
        val address = listOf(prefecture, city, town, streetAddress)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString("")
        val facilityInfo = FacilityInfo(
            name = name,
            postalCode = postalCode,
            prefecture = prefecture,
            city = city,
            town = town,
            streetAddress = streetAddress,
            address = address
        )
        preferences.edit()
            .putString(KEY_NAME, facilityInfo.name)
            .putString(KEY_POSTAL_CODE, facilityInfo.postalCode)
            .putString(KEY_PREFECTURE, facilityInfo.prefecture)
            .putString(KEY_CITY, facilityInfo.city)
            .putString(KEY_TOWN, facilityInfo.town)
            .putString(KEY_STREET_ADDRESS, facilityInfo.streetAddress)
            .putString(KEY_ADDRESS, facilityInfo.address)
            .apply()
        _facility.value = facilityInfo
    }

    private fun readFacility(): FacilityInfo {
        return FacilityInfo(
            name = preferences.getString(KEY_NAME, "").orEmpty(),
            postalCode = preferences.getString(KEY_POSTAL_CODE, "").orEmpty(),
            prefecture = preferences.getString(KEY_PREFECTURE, "").orEmpty(),
            city = preferences.getString(KEY_CITY, "").orEmpty(),
            town = preferences.getString(KEY_TOWN, "").orEmpty(),
            streetAddress = preferences.getString(KEY_STREET_ADDRESS, "").orEmpty(),
            address = preferences.getString(KEY_ADDRESS, "").orEmpty()
        )
    }

    private companion object {
        const val PREFS_NAME = "facility_settings"
        const val KEY_NAME = "facility_name"
        const val KEY_POSTAL_CODE = "facility_postal_code"
        const val KEY_PREFECTURE = "facility_prefecture"
        const val KEY_CITY = "facility_city"
        const val KEY_TOWN = "facility_town"
        const val KEY_STREET_ADDRESS = "facility_street_address"
        const val KEY_ADDRESS = "facility_address"
    }
}
