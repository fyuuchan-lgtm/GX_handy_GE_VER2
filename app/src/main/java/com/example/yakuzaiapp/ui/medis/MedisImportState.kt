package com.example.yakuzaiapp.ui.medis

data class MedisImportUiState(
    val status: ImportStatus = ImportStatus.Idle,
    val processedCount: Int = 0,
    val totalCount: Int = 0,
    val phaseLabel: String = "",
    val operationLabel: String = "MEDIS HOT",
    val selectedFileName: String? = null,
    val selectedMedisHotFileName: String? = null,
    val selectedSalesNameFileName: String? = null,
    val medisHotImported: Boolean = false,
    val salesNameImported: Boolean = false,
    val result: ImportResult? = null,
    val errorMessage: String? = null,
) {
    val progressFraction: Float
        get() = if (totalCount > 0) {
            (processedCount.toFloat() / totalCount).coerceIn(0f, 1f)
        } else {
            0f
        }

    val isProcessing: Boolean
        get() = status == ImportStatus.Reading ||
            status == ImportStatus.Parsing ||
            status == ImportStatus.Deleting ||
            status == ImportStatus.Inserting

    val isBackBlocked: Boolean
        get() = isProcessing
}

enum class ImportStatus {
    Idle,
    Reading,
    Parsing,
    Deleting,
    Inserting,
    Completed,
    Failed,
}

data class ImportResult(
    val totalRecords: Int,
    val skippedRecords: Int,
    val errorLineCount: Int,
    val elapsedMs: Long,
)
