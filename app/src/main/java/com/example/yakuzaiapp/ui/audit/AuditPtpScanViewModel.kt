package com.example.yakuzaiapp.ui.audit

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.yakuzaiapp.YakuzaiApplication
import com.example.yakuzaiapp.data.local.dao.DrugMasterDao
import com.example.yakuzaiapp.data.local.dao.SalesPackageDao
import com.example.yakuzaiapp.data.local.entity.DrugMaster
import com.example.yakuzaiapp.domain.audit.DrugIdentity
import com.example.yakuzaiapp.domain.dispensing.ScanMatchResult
import com.example.yakuzaiapp.repository.DrugMasterLookup
import com.example.yakuzaiapp.repository.DrugMasterRepository
import com.example.yakuzaiapp.util.normalizeGtin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "AuditPtpScan"

class AuditPtpScanViewModel(
    private val drugMasterLookup: DrugMasterLookup
) : ViewModel() {
    constructor(
        drugMasterDao: DrugMasterDao,
        salesPackageDao: SalesPackageDao
    ) : this(DrugMasterRepository(drugMasterDao, salesPackageDao))

    data class PtpScanRow(
        val yjCode: String,
        val displayName: String,
        val packageSpec: String?,
        val dosageForm: String?,
        val scanned: Boolean = false,
        val scannedGtin: String? = null,
        val scannedAt: Long? = null
    )

    data class UiState(
        val rows: List<PtpScanRow> = emptyList(),
        val lastMessage: String? = null,
        val isComplete: Boolean = false
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    private val _scanFeedback = MutableSharedFlow<ScanMatchResult>(extraBufferCapacity = 1)
    val scanFeedback: SharedFlow<ScanMatchResult> = _scanFeedback.asSharedFlow()
    private var initializedSignature: String? = null

    fun initializeFromAudit(confirmedDrugs: List<DrugIdentity>) {
        val signature = confirmedDrugs.joinToString("|") { "${it.yjCode}:${it.displayName}" }
        if (_uiState.value.rows.isNotEmpty() && initializedSignature == signature) return

        initializedSignature = signature
        _uiState.value = UiState(
            rows = confirmedDrugs.map { drug ->
                PtpScanRow(
                    yjCode = drug.yjCode,
                    displayName = drug.displayName,
                    packageSpec = drug.packageSpec,
                    dosageForm = drug.dosageForm
                )
            },
            lastMessage = null,
            isComplete = false
        )
    }

    fun onBarcodeScanned(rawBarcode: String) {
        viewModelScope.launch {
            val gtin = extractGtin(rawBarcode) ?: run {
                _uiState.update { it.copy(lastMessage = "バーコード解析失敗: $rawBarcode") }
                _scanFeedback.emit(ScanMatchResult.InvalidBarcodeFormat(rawBarcode))
                return@launch
            }

            val drugMaster = drugMasterLookup.findByAnyGtin(gtin)
            if (drugMaster == null) {
                _uiState.update { it.copy(lastMessage = "マスターに該当なし: $gtin") }
                _scanFeedback.emit(ScanMatchResult.UnregisteredGtin(gtin))
                Log.d(TAG, "scan gtin=$gtin -> not found")
                return@launch
            }

            val resolvedYjCodes = resolveAuditYjCodes(drugMaster)
            val matchedRow = findMatchingAuditRow(drugMaster, _uiState.value.rows)
            if (matchedRow == null) {
                _uiState.update { it.copy(lastMessage = "帳票にない薬品です") }
                _scanFeedback.emit(ScanMatchResult.NotInList(drugMaster.displayLabel))
                Log.d(
                    TAG,
                    "scan gtin=$gtin masterYj=${drugMaster.yjCode} drugCode=${drugMaster.drugCode} resolvedYj=${resolvedYjCodes.joinToString("|")} -> not in audit"
                )
                return@launch
            }

            val alreadyScanned = _uiState.value.rows.find { it.yjCode == matchedRow.yjCode && it.scanned }
            if (alreadyScanned != null) {
                _uiState.update { it.copy(lastMessage = "スキャン済みです") }
                _scanFeedback.emit(ScanMatchResult.AlreadyConfirmed(alreadyScanned.displayName))
                Log.d(TAG, "scan gtin=$gtin yj=${matchedRow.yjCode} -> already scanned")
                return@launch
            }

            val updatedRows = _uiState.value.rows.map { row ->
                if (row.yjCode == matchedRow.yjCode) {
                    row.copy(
                        scanned = true,
                        scannedGtin = gtin,
                        scannedAt = System.currentTimeMillis()
                    )
                } else {
                    row
                }
            }
            val complete = updatedRows.all { it.scanned }
            _uiState.update {
                it.copy(
                    rows = updatedRows,
                    lastMessage = "OK",
                    isComplete = complete
                )
            }
            _scanFeedback.emit(ScanMatchResult.Success(matchedRow.yjCode, matchedRow.displayName))
            Log.d(
                TAG,
                "scan gtin=$gtin masterYj=${drugMaster.yjCode} drugCode=${drugMaster.drugCode} matchedYj=${matchedRow.yjCode} -> matched complete=$complete"
            )
        }
    }

    private fun resolveAuditYjCodes(drugMaster: DrugMaster): Set<String> {
        val codes = linkedSetOf<String>()
        drugMaster.yjCode?.takeIf { it.isNotBlank() }?.let { codes += it }
        drugMaster.drugCode.takeIf { it.isNotBlank() }?.let { codes += it }
        return codes
    }

    private fun findMatchingAuditRow(drugMaster: DrugMaster, rows: List<PtpScanRow>): PtpScanRow? {
        drugMaster.yjCode
            ?.takeIf { it.isNotBlank() }
            ?.let { yjCode -> rows.firstOrNull { it.yjCode == yjCode } }
            ?.let { return it }

        return drugMaster.drugCode
            .takeIf { it.isNotBlank() }
            ?.let { drugCode -> rows.firstOrNull { it.yjCode == drugCode } }
    }

    fun clearMessage() {
        _uiState.update { it.copy(lastMessage = null) }
    }

    fun resetScan(yjCode: String) {
        _uiState.update {
            val rows = it.rows.map { row ->
                if (row.yjCode == yjCode) {
                    row.copy(scanned = false, scannedGtin = null, scannedAt = null)
                } else {
                    row
                }
            }
            it.copy(rows = rows, isComplete = rows.all { row -> row.scanned })
        }
    }

    internal fun extractGtin(raw: String): String? {
        return normalizeGtin(raw)
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as YakuzaiApplication
                AuditPtpScanViewModel(app.drugMasterRepository)
            }
        }
    }
}
