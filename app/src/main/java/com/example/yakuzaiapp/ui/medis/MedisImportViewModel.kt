package com.example.yakuzaiapp.ui.medis

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.room.withTransaction
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.yakuzaiapp.YakuzaiApplication
import com.example.yakuzaiapp.data.medis.MedisImportRepository
import com.example.yakuzaiapp.data.medis.SalesNameImportRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

private data class ImportSelection(
    val kind: ImportKind,
    val uri: Uri,
    val fileName: String?,
)

private data class ImportStats(
    val totalRecords: Int,
    val skippedRecords: Int,
    val errorLineCount: Int,
    val elapsedMs: Long,
)

private enum class ImportKind {
    MedisHot,
    SalesName,
}

class MedisImportViewModel(application: Application) : AndroidViewModel(application) {
    private val medisRepository: MedisImportRepository
    private val salesNameRepository: SalesNameImportRepository
    private var selectedMedisHotUri: Uri? = null
    private var selectedSalesNameUri: Uri? = null

    companion object {
        private val MEDIS_HOT_FILE_PATTERN = Regex("""MEDIS\d{8}_h\.txt""", RegexOption.IGNORE_CASE)
        private val SALES_NAME_FILE_PATTERN = Regex("""A_\d{8}_2\.txt""", RegexOption.IGNORE_CASE)
    }

    private val _uiState = MutableStateFlow(MedisImportUiState())
    val uiState: StateFlow<MedisImportUiState> = _uiState.asStateFlow()

    init {
        val app = application as YakuzaiApplication
        medisRepository = MedisImportRepository(
            context = application.applicationContext,
            database = app.database,
        )
        salesNameRepository = SalesNameImportRepository(
            context = application.applicationContext,
            dao = app.database.salesPackageDao(),
            transactionRunner = { block ->
                app.database.withTransaction {
                    block()
                }
            },
        )
    }

    fun selectMedisHotFile(uri: Uri) {
        selectedMedisHotUri = uri
        val fileName = resolveFileName(uri)
        _uiState.update {
            it.copy(
                status = ImportStatus.Idle,
                operationLabel = "MEDIS HOT",
                selectedFileName = fileName,
                selectedMedisHotFileName = fileName,
                medisHotImported = false,
                phaseLabel = "MEDIS HOT を選んだ。取り込みボタンを押す。",
                processedCount = 0,
                totalCount = 0,
                result = null,
                errorMessage = null,
            )
        }
    }

    fun selectSalesNameFile(uri: Uri) {
        selectedSalesNameUri = uri
        val fileName = resolveFileName(uri)
        _uiState.update {
            it.copy(
                status = ImportStatus.Idle,
                operationLabel = "販売名ファイル",
                selectedFileName = fileName,
                selectedSalesNameFileName = fileName,
                salesNameImported = false,
                phaseLabel = "販売名ファイルを選んだ。取り込みボタンを押す。",
                processedCount = 0,
                totalCount = 0,
                result = null,
                errorMessage = null,
            )
        }
    }

    fun importSelectedMedisHotFile() {
        val uri = selectedMedisHotUri ?: run {
            fail("MEDIS HOT ファイルを先に選ぶ。")
            return
        }
        val fileName = resolveFileName(uri)
        if (fileName?.matches(MEDIS_HOT_FILE_PATTERN) != true) {
            fail("MEDIS HOT は MEDIS********_h.txt を選択してください。")
            return
        }
        viewModelScope.launch {
            importSelections(
                selections = listOf(ImportSelection(ImportKind.MedisHot, uri, fileName)),
            )
        }
    }

    fun importSelectedSalesNameFile() {
        val uri = selectedSalesNameUri ?: run {
            fail("販売名ファイルを先に選ぶ。")
            return
        }
        val fileName = resolveFileName(uri)
        if (fileName?.matches(SALES_NAME_FILE_PATTERN) != true) {
            fail("販売名ファイルは A_********_2.txt を選択してください。")
            return
        }
        viewModelScope.launch {
            importSelections(
                selections = listOf(ImportSelection(ImportKind.SalesName, uri, fileName)),
            )
        }
    }

    fun importSelectedFiles() {
        val selections = buildSelectedImportSelections()
        if (selections.isEmpty()) {
            fail("MEDIS HOT ファイルか販売名ファイルを選ぶ。")
            return
        }
        val invalidSelection = selections.firstOrNull { selection ->
            when (selection.kind) {
                ImportKind.MedisHot -> selection.fileName?.matches(MEDIS_HOT_FILE_PATTERN) != true
                ImportKind.SalesName -> selection.fileName?.matches(SALES_NAME_FILE_PATTERN) != true
            }
        }
        if (invalidSelection != null) {
            fail(
                when (invalidSelection.kind) {
                    ImportKind.MedisHot -> "MEDIS HOT は MEDIS********_h.txt を選択してください。"
                    ImportKind.SalesName -> "販売名ファイルは A_********_2.txt を選択してください。"
                }
            )
            return
        }
        viewModelScope.launch {
            importSelections(selections)
        }
    }

    fun reset() {
        selectedMedisHotUri = null
        selectedSalesNameUri = null
        _uiState.value = MedisImportUiState()
    }

    private suspend fun importSelections(selections: List<ImportSelection>) {
        val operationLabel = selections.joinToString(" + ") {
            when (it.kind) {
                ImportKind.MedisHot -> "MEDIS HOT"
                ImportKind.SalesName -> "販売名ファイル"
            }
        }
        val selectedSummary = selections.joinToString(" / ") { it.fileName ?: "未取得" }
        _uiState.update {
            it.copy(
                status = ImportStatus.Reading,
                operationLabel = operationLabel,
                selectedFileName = selectedSummary,
                phaseLabel = "$operationLabel を読み込む...",
                processedCount = 0,
                totalCount = 0,
                result = null,
                errorMessage = null,
            )
        }

        var totalRecords = 0
        var skippedRecords = 0
        var errorLineCount = 0
        var elapsedMs = 0L
        var importedHot = false
        var importedSales = false

        selections.forEachIndexed { index, selection ->
            val stats = when (selection.kind) {
                ImportKind.MedisHot -> importMedisHotFile(selection.uri, operationLabel, selectedSummary)
                ImportKind.SalesName -> importSalesNameFile(selection.uri, operationLabel, selectedSummary)
            } ?: return

            totalRecords += stats.totalRecords
            skippedRecords += stats.skippedRecords
            errorLineCount += stats.errorLineCount
            elapsedMs += stats.elapsedMs
            importedHot = importedHot || selection.kind == ImportKind.MedisHot
            importedSales = importedSales || selection.kind == ImportKind.SalesName

            if (index != selections.lastIndex) {
                _uiState.update {
                    it.copy(
                        status = ImportStatus.Reading,
                        operationLabel = operationLabel,
                        selectedFileName = selectedSummary,
                        phaseLabel = "$operationLabel の取り込み完了。次のファイルを読み込む。",
                        result = null,
                        errorMessage = null,
                    )
                }
            }
        }

        _uiState.update {
            it.copy(
                status = ImportStatus.Completed,
                operationLabel = operationLabel,
                selectedFileName = selectedSummary,
                medisHotImported = importedHot,
                salesNameImported = importedSales,
                phaseLabel = "$operationLabel の取り込み完了。",
                result = ImportResult(
                    totalRecords = totalRecords,
                    skippedRecords = skippedRecords,
                    errorLineCount = errorLineCount,
                    elapsedMs = elapsedMs,
                ),
                errorMessage = null,
            )
        }
    }

    private suspend fun importMedisHotFile(
        uri: Uri,
        operationLabel: String,
        selectedSummary: String,
    ): ImportStats? {
        val fileName = resolveFileName(uri)
        _uiState.update {
            it.copy(
                status = ImportStatus.Reading,
                operationLabel = operationLabel,
                selectedFileName = selectedSummary,
                selectedMedisHotFileName = fileName,
                medisHotImported = false,
                phaseLabel = "MEDIS HOT を読み込む...",
                processedCount = 0,
                totalCount = 0,
                result = null,
                errorMessage = null,
            )
        }

        var result: ImportStats? = null
        medisRepository.import(uri).collect { progress ->
            when (progress) {
                is MedisImportRepository.Progress.Reading -> {
                    _uiState.update {
                        it.copy(
                            status = ImportStatus.Reading,
                            phaseLabel = "MEDIS HOT を読み込み中...",
                        )
                    }
                }

                is MedisImportRepository.Progress.Parsing -> {
                    _uiState.update {
                        it.copy(
                            status = ImportStatus.Parsing,
                            phaseLabel = "CSV を解析中...",
                            processedCount = progress.processed,
                            totalCount = progress.total,
                        )
                    }
                }

                is MedisImportRepository.Progress.Deleting -> {
                    _uiState.update {
                        it.copy(
                            status = ImportStatus.Deleting,
                            phaseLabel = "既存データを削除中...",
                        )
                    }
                }

                is MedisImportRepository.Progress.Inserting -> {
                    _uiState.update {
                        it.copy(
                            status = ImportStatus.Inserting,
                            phaseLabel = "データベースへ登録中...",
                            processedCount = progress.inserted,
                            totalCount = progress.total,
                        )
                    }
                }

                is MedisImportRepository.Progress.Completed -> {
                    result = ImportStats(
                        totalRecords = progress.totalRecords,
                        skippedRecords = progress.skippedRecords,
                        errorLineCount = progress.errorLines.size,
                        elapsedMs = progress.elapsedMs,
                    )
                    _uiState.update {
                        it.copy(
                            status = ImportStatus.Reading,
                            medisHotImported = true,
                            phaseLabel = "MEDIS HOT の取り込み完了。",
                            result = null,
                        )
                    }
                }

                is MedisImportRepository.Progress.Failed -> {
                    _uiState.update {
                        it.copy(
                            status = ImportStatus.Failed,
                            phaseLabel = "エラーが出た。",
                            errorMessage = progress.message,
                        )
                    }
                    result = null
                }
            }
        }
        return result
    }

    private suspend fun importSalesNameFile(
        uri: Uri,
        operationLabel: String,
        selectedSummary: String,
    ): ImportStats? {
        val fileName = resolveFileName(uri)
        _uiState.update {
            it.copy(
                status = ImportStatus.Reading,
                operationLabel = operationLabel,
                selectedFileName = selectedSummary,
                selectedSalesNameFileName = fileName,
                salesNameImported = false,
                phaseLabel = "販売名ファイルを読み込む...",
                processedCount = 0,
                totalCount = 0,
                result = null,
                errorMessage = null,
            )
        }

        var result: ImportStats? = null
        salesNameRepository.importFromUri(uri).collect { progress ->
            when (progress) {
                is SalesNameImportRepository.Progress.Reading -> {
                    _uiState.update {
                        it.copy(
                            status = ImportStatus.Reading,
                            phaseLabel = "販売名ファイルを読み込み中...",
                        )
                    }
                }

                is SalesNameImportRepository.Progress.Parsing -> {
                    _uiState.update {
                        it.copy(
                            status = ImportStatus.Parsing,
                            phaseLabel = "販売名ファイルを解析中...",
                            processedCount = progress.processed,
                            totalCount = progress.total,
                        )
                    }
                }

                is SalesNameImportRepository.Progress.Deleting -> {
                    _uiState.update {
                        it.copy(
                            status = ImportStatus.Deleting,
                            phaseLabel = "既存の販売名データを削除中...",
                        )
                    }
                }

                is SalesNameImportRepository.Progress.Inserting -> {
                    _uiState.update {
                        it.copy(
                            status = ImportStatus.Inserting,
                            phaseLabel = "販売名データを登録中...",
                            processedCount = progress.inserted,
                            totalCount = progress.total,
                        )
                    }
                }

                is SalesNameImportRepository.Progress.Completed -> {
                    result = ImportStats(
                        totalRecords = progress.totalRecords,
                        skippedRecords = progress.skippedRecords,
                        errorLineCount = 0,
                        elapsedMs = progress.elapsedMs,
                    )
                    _uiState.update {
                        it.copy(
                            status = ImportStatus.Reading,
                            salesNameImported = true,
                            phaseLabel = "販売名ファイルの取り込み完了。",
                            result = null,
                        )
                    }
                }

                is SalesNameImportRepository.Progress.Failed -> {
                    _uiState.update {
                        it.copy(
                            status = ImportStatus.Failed,
                            phaseLabel = "販売名ファイルでエラーが出た。",
                            errorMessage = progress.message,
                        )
                    }
                    result = null
                }
            }
        }
        return result
    }

    private fun buildSelectedImportSelections(): List<ImportSelection> {
        val selections = mutableListOf<ImportSelection>()
        selectedMedisHotUri?.let { uri ->
            selections += ImportSelection(
                kind = ImportKind.MedisHot,
                uri = uri,
                fileName = resolveFileName(uri),
            )
        }
        selectedSalesNameUri?.let { uri ->
            selections += ImportSelection(
                kind = ImportKind.SalesName,
                uri = uri,
                fileName = resolveFileName(uri),
            )
        }
        return selections
    }

    private fun fail(message: String) {
        _uiState.update {
            it.copy(
                status = ImportStatus.Failed,
                errorMessage = message,
            )
        }
    }

    private fun resolveFileName(uri: Uri): String? {
        return try {
            getApplication<Application>().contentResolver.query(uri, null, null, null, null)
                ?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && nameIndex >= 0) {
                        cursor.getString(nameIndex)
                    } else {
                        null
                    }
                }
        } catch (_: Exception) {
            null
        }
    }

    class Factory(
        private val application: Application,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MedisImportViewModel::class.java)) {
                return MedisImportViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
