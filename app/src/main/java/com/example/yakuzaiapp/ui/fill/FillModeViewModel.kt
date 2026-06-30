package com.example.yakuzaiapp.ui.fill

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.yakuzaiapp.YakuzaiApplication
import com.example.yakuzaiapp.data.local.entity.DrugMaster
import com.example.yakuzaiapp.repository.DrugMasterLookup
import com.example.yakuzaiapp.util.normalizeGtin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class FillModeViewModel(
    private val drugMasterLookup: DrugMasterLookup,
    private val nowMs: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val _uiState = MutableStateFlow(FillModeUiState())
    val uiState: StateFlow<FillModeUiState> = _uiState.asStateFlow()

    private var lastScanSignature: String? = null
    private var lastScanAtMs: Long = 0L

    fun onBarcodeScanned(rawBarcode: String) {
        viewModelScope.launch {
            processBarcode(rawBarcode)
        }
    }

    internal suspend fun processBarcode(rawBarcode: String) {
        if (_uiState.value.phase == FillModeStage.COMPLETED) {
            return
        }

        val now = nowMs()
        if (_uiState.value.phase == FillModeStage.SELECT_TARGET &&
            now < _uiState.value.targetScanEnabledAtMs
        ) {
            updateStatus("薬品をカメラから外して、充填先のカセットまたは瓶のコードを準備してください")
            return
        }

        val rawCode = normalizeDirectCode(rawBarcode)
        if (_uiState.value.phase == FillModeStage.SELECT_TARGET &&
            rawCode != null &&
            _uiState.value.selectedCodes.contains(rawCode)
        ) {
            completeFill(rawCode)
            return
        }

        val normalizedGtin = normalizeFillGtin(rawBarcode)
        if (normalizedGtin == null) {
            if (_uiState.value.phase == FillModeStage.SELECT_TARGET) {
                updateStatus(TARGET_SCAN_MESSAGE)
                return
            }
            updateStatus("バーコードを読み取れませんでした: $rawBarcode")
            return
        }

        if (_uiState.value.phase == FillModeStage.SELECT_TARGET &&
            normalizedGtin == _uiState.value.selectedSourceGtin
        ) {
            updateStatus(TARGET_SCAN_MESSAGE)
            return
        }

        val signature = "${_uiState.value.phase}:$normalizedGtin"
        if (signature == lastScanSignature && now - lastScanAtMs < 4000L) {
            return
        }
        lastScanSignature = signature
        lastScanAtMs = now

        val drug = drugMasterLookup.findByAnyGtin(normalizedGtin)
        if (drug == null) {
            if (_uiState.value.phase == FillModeStage.SELECT_TARGET) {
                updateStatus(TARGET_SCAN_MESSAGE)
                return
            }
            updateStatus("マスターに見つかりませんでした: $normalizedGtin")
            return
        }

        when (_uiState.value.phase) {
            FillModeStage.SELECT_DRUG -> selectDrug(drug, normalizedGtin)
            FillModeStage.SELECT_TARGET -> verifyTarget(drug, normalizedGtin)
            FillModeStage.COMPLETED -> Unit
        }
    }

    fun reset() {
        _uiState.value = FillModeUiState()
        lastScanSignature = null
        lastScanAtMs = 0L
    }

    private fun selectDrug(drug: DrugMaster, gtin: String) {
        val selectedCode = selectedCodeFor(drug)
        _uiState.value = FillModeUiState(
            phase = FillModeStage.SELECT_TARGET,
            selectedDrugName = drug.drugName,
            selectedYjCode = selectedCode,
            selectedCodes = resolveCodes(drug),
            selectedSourceGtin = gtin,
            lastScannedGtin = gtin,
            targetScanEnabledAtMs = nowMs() + TARGET_SCAN_DELAY_MS,
            statusText = TARGET_SCAN_MESSAGE
        )
    }

    private fun verifyTarget(drug: DrugMaster, gtin: String) {
        val selectedCodes = _uiState.value.selectedCodes
        val scannedCodes = resolveCodes(drug)
        val matched = scannedCodes.any { it in selectedCodes }

        if (matched) {
            completeFill(gtin)
        } else {
            updateStatus("一致しませんでした: ${drug.displayLabel}")
        }
    }

    private fun completeFill(code: String) {
        _uiState.update {
            it.copy(
                phase = FillModeStage.COMPLETED,
                lastScannedGtin = code,
                statusText = "充填OK: ${it.selectedDrugName.orEmpty()}",
                isComplete = true
            )
        }
    }

    private fun updateStatus(message: String) {
        _uiState.update { it.copy(statusText = message) }
    }

    private fun selectedCodeFor(drug: DrugMaster): String? {
        return drug.yjCode?.takeIf { it.isNotBlank() }
            ?: drug.drugCode.takeIf { it.isNotBlank() }
    }

    private fun resolveCodes(drug: DrugMaster): Set<String> {
        val codes = linkedSetOf<String>()
        drug.yjCode?.takeIf { it.isNotBlank() }?.let { codes += it }
        drug.drugCode.takeIf { it.isNotBlank() }?.let { codes += it }
        return codes
    }

    private fun normalizeFillGtin(rawBarcode: String): String? {
        normalizeGtin(rawBarcode)?.let { return it }

        val digits = rawBarcode.filter(Char::isDigit)
        digits.windowed(size = 16, step = 1)
            .firstNotNullOfOrNull { window ->
                if (window.startsWith("01")) normalizeGtin(window) else null
            }
            ?.let { return it }

        return when {
            digits.length == 14 -> digits
            digits.length == 13 -> "0$digits"
            else -> null
        }
    }

    private fun normalizeDirectCode(rawBarcode: String): String? {
        val normalized = rawBarcode
            .map { ch ->
                when (ch) {
                    in '０'..'９' -> '0' + (ch - '０')
                    in 'Ａ'..'Ｚ' -> 'A' + (ch - 'Ａ')
                    in 'ａ'..'ｚ' -> 'a' + (ch - 'ａ')
                    else -> ch
                }
            }
            .joinToString("")
            .trim()
            .replace(Regex("\\s+"), "")
            .uppercase()
        return normalized.takeIf { it.isNotBlank() }
    }

    companion object {
        private const val TARGET_SCAN_DELAY_MS = 2500L
        private const val TARGET_SCAN_MESSAGE = "充填先のカセットまたは瓶のコードをスキャンしてください"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as YakuzaiApplication
                FillModeViewModel(app.drugMasterRepository)
            }
        }
    }
}

data class FillModeUiState(
    val phase: FillModeStage = FillModeStage.SELECT_DRUG,
    val selectedDrugName: String? = null,
    val selectedYjCode: String? = null,
    val selectedCodes: Set<String> = emptySet(),
    val selectedSourceGtin: String? = null,
    val lastScannedGtin: String? = null,
    val targetScanEnabledAtMs: Long = 0L,
    val statusText: String = "充填する薬品のPTPまたは箱をスキャンしてください",
    val isComplete: Boolean = false
)

enum class FillModeStage {
    SELECT_DRUG,
    SELECT_TARGET,
    COMPLETED
}
