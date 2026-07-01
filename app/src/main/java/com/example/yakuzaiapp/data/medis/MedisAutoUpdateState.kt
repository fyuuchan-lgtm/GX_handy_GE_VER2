package com.example.yakuzaiapp.data.medis

sealed interface MedisAutoUpdateState {
    data object Idle : MedisAutoUpdateState

    data class Running(
        val phase: Phase,
        val message: String,
        val processedCount: Int = 0,
        val totalCount: Int = 0,
    ) : MedisAutoUpdateState {
        val progressFraction: Float
            get() = if (totalCount > 0) {
                (processedCount.toFloat() / totalCount).coerceIn(0f, 1f)
            } else {
                0f
            }
    }

    data class Completed(
        val hotVersionDate: String,
        val salesVersionDate: String,
        val hotRecords: Int,
        val salesRecords: Int,
        val elapsedMs: Long,
    ) : MedisAutoUpdateState

    data class Error(
        val message: String,
    ) : MedisAutoUpdateState

    enum class Phase {
        Checking,
        Downloading,
        Parsing,
        Importing,
    }
}

data class MedisAutoImportResult(
    val hotVersionDate: String,
    val salesVersionDate: String,
    val hotUrl: String,
    val salesUrl: String,
    val hotRecords: Int,
    val salesRecords: Int,
    val elapsedMs: Long,
)
