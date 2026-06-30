package com.example.yakuzaiapp.domain.scan

enum class ScanMode {
    PTP_GTIN,
    JAHIS_QR;

    companion object {
        fun fromRouteValue(value: String?): ScanMode {
            return runCatching {
                value?.let { valueOf(it) }
            }.getOrNull() ?: PTP_GTIN
        }
    }
}
