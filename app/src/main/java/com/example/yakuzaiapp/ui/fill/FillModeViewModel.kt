package com.example.yakuzaiapp.ui.fill

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.yakuzaiapp.YakuzaiApplication
import com.example.yakuzaiapp.data.local.dao.FillHistoryDao
import com.example.yakuzaiapp.data.local.dao.StaffMasterDao
import com.example.yakuzaiapp.data.local.entity.DrugMaster
import com.example.yakuzaiapp.data.local.entity.FillHistory
import com.example.yakuzaiapp.data.local.entity.StaffMaster
import com.example.yakuzaiapp.data.repository.StaffSelectionRepository
import com.example.yakuzaiapp.repository.DrugMasterLookup
import com.example.yakuzaiapp.util.normalizeGtin
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class FillModeViewModel(
    private val drugMasterLookup: DrugMasterLookup,
    staffMasterDao: StaffMasterDao,
    private val fillHistoryDao: FillHistoryDao,
    private val staffSelectionRepository: StaffSelectionRepository,
    private val nowMs: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val _uiState = MutableStateFlow(FillModeUiState())
    val uiState: StateFlow<FillModeUiState> = _uiState.asStateFlow()
    val staffList: StateFlow<List<StaffMaster>> = staffMasterDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            combine(staffSelectionRepository.selectedStaffId, staffList) { selectedId, list ->
                list.firstOrNull { it.staffId == selectedId }
            }.collect { staff ->
                if (staff != null && _uiState.value.selectedStaffId != staff.staffId) {
                    applySelectedStaff(staff)
                }
            }
        }
    }

    private var lastScanSignature: String? = null
    private var lastScanAtMs: Long = 0L
    private var lastSelectedSourceGtinSeenAtMs: Long? = null

    fun onBarcodeScanned(rawBarcode: String) {
        viewModelScope.launch {
            processBarcode(rawBarcode)
        }
    }

    fun selectStaff(staff: StaffMaster) {
        staffSelectionRepository.selectStaff(staff.staffId)
        applySelectedStaff(staff)
    }

    private fun applySelectedStaff(staff: StaffMaster) {
        _uiState.update {
            it.copy(
                selectedStaffId = staff.staffId,
                selectedStaffName = staff.displayName(),
                selectedStaffKana = staff.staffKana,
                statusText = if (it.phase == FillModeStage.SELECT_DRUG) {
                    "充填する薬品のPTPまたは箱をスキャンしてください"
                } else {
                    it.statusText
                }
            )
        }
    }

    internal suspend fun processBarcode(rawBarcode: String) {
        if (_uiState.value.phase == FillModeStage.COMPLETED) {
            return
        }
        if (_uiState.value.selectedStaffId.isNullOrBlank()) {
            updateStatus("実施者を選択してください")
            return
        }

        val now = nowMs()
        val rawCode = normalizeDirectCode(rawBarcode)
        val parsedBarcode = parseFillBarcode(rawBarcode)
        val normalizedGtin = parsedBarcode.gtin

        if (_uiState.value.phase == FillModeStage.SELECT_TARGET) {
            val isSameAsSelectedSource = normalizedGtin != null &&
                normalizedGtin == _uiState.value.selectedSourceGtin
            if (isSameAsSelectedSource && parsedBarcode.expirationDateText != null) {
                updateSelectedSourceExpirationDate(parsedBarcode.expirationDateText)
            }
            val lastSourceSeenAt = lastSelectedSourceGtinSeenAtMs
            val needsSourceSeparation = isSameAsSelectedSource &&
                lastSourceSeenAt != null &&
                now - lastSourceSeenAt < SAME_SOURCE_REARM_QUIET_MS

            if (now < _uiState.value.targetScanEnabledAtMs || needsSourceSeparation) {
                if (isSameAsSelectedSource) {
                    lastSelectedSourceGtinSeenAtMs = now
                }
                updateStatus("薬品をカメラから外して、充填先のカセットまたは瓶のコードを準備してください")
                return
            }
        }

        if (_uiState.value.phase == FillModeStage.SELECT_TARGET &&
            rawCode != null &&
            _uiState.value.selectedCodes.contains(rawCode)
        ) {
            completeFill(rawCode)
            return
        }

        if (normalizedGtin == null) {
            if (_uiState.value.phase == FillModeStage.SELECT_TARGET) {
                updateStatus(TARGET_SCAN_MESSAGE)
                return
            }
            updateStatus("バーコードを読み取れませんでした: $rawBarcode")
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
            FillModeStage.SELECT_DRUG -> selectDrug(drug, normalizedGtin, parsedBarcode.expirationDateText)
            FillModeStage.SELECT_TARGET -> verifyTarget(drug, normalizedGtin, parsedBarcode.expirationDateText)
            FillModeStage.COMPLETED -> Unit
        }
    }

    fun reset() {
        val current = _uiState.value
        _uiState.value = FillModeUiState(
            selectedStaffId = current.selectedStaffId,
            selectedStaffName = current.selectedStaffName,
            selectedStaffKana = current.selectedStaffKana,
            statusText = if (current.selectedStaffId.isNullOrBlank()) {
                "実施者を選択してください"
            } else {
                "充填する薬品のPTPまたは箱をスキャンしてください"
            }
        )
        lastScanSignature = null
        lastScanAtMs = 0L
        lastSelectedSourceGtinSeenAtMs = null
    }

    fun dismissExpirationWarning() {
        _uiState.update {
            it.copy(
                expirationWarningMessage = null,
                dismissedExpirationWarningDate = it.warningExpirationDate,
                warningExpirationDate = null
            )
        }
    }

    private fun selectDrug(drug: DrugMaster, gtin: String, expirationDateText: String?) {
        val selectedCode = selectedCodeFor(drug)
        val expirationWarningMessage = expirationWarningMessageFor(
            expirationDateText = expirationDateText,
            dismissedExpirationDate = null
        )
        _uiState.value = FillModeUiState(
            phase = FillModeStage.SELECT_TARGET,
            selectedStaffId = _uiState.value.selectedStaffId,
            selectedStaffName = _uiState.value.selectedStaffName,
            selectedStaffKana = _uiState.value.selectedStaffKana,
            selectedDrugName = drug.drugName,
            selectedYjCode = selectedCode,
            selectedCodes = resolveCodes(drug),
            selectedSourceGtin = gtin,
            selectedSourceExpirationDate = expirationDateText,
            lastScannedGtin = gtin,
            lastScannedExpirationDate = expirationDateText,
            targetScanEnabledAtMs = nowMs() + TARGET_SCAN_DELAY_MS,
            statusText = TARGET_SCAN_MESSAGE,
            expirationWarningMessage = expirationWarningMessage,
            warningExpirationDate = expirationDateText.takeIf { expirationWarningMessage != null }
        )
        lastSelectedSourceGtinSeenAtMs = nowMs()
    }

    private suspend fun verifyTarget(drug: DrugMaster, gtin: String, expirationDateText: String?) {
        val selectedCodes = _uiState.value.selectedCodes
        val scannedCodes = resolveCodes(drug)
        val matched = scannedCodes.any { it in selectedCodes }

        if (matched) {
            completeFill(gtin, expirationDateText)
        } else {
            updateStatus("一致しませんでした: ${drug.displayLabel}")
        }
    }

    private suspend fun completeFill(code: String, expirationDateText: String? = null) {
        val before = _uiState.value
        _uiState.update {
            it.copy(
                phase = FillModeStage.COMPLETED,
                lastScannedGtin = code,
                lastScannedExpirationDate = expirationDateText,
                statusText = "充填OK: ${it.selectedDrugName.orEmpty()}",
                isComplete = true
            )
        }
        fillHistoryDao.insert(
            FillHistory(
                completedAt = nowMs(),
                staffId = before.selectedStaffId.orEmpty(),
                staffName = before.selectedStaffName,
                staffKana = before.selectedStaffKana,
                drugName = before.selectedDrugName.orEmpty(),
                yjCode = before.selectedYjCode,
                sourceGtin = before.selectedSourceGtin,
                targetCode = code,
                expirationDate = before.selectedSourceExpirationDate ?: expirationDateText,
                status = "OK"
            )
        )
    }

    private fun updateStatus(message: String) {
        _uiState.update { it.copy(statusText = message) }
    }

    private fun updateSelectedSourceExpirationDate(expirationDateText: String) {
        _uiState.update {
            val expirationWarningMessage = expirationWarningMessageFor(
                expirationDateText = expirationDateText,
                dismissedExpirationDate = it.dismissedExpirationWarningDate
            )
            it.copy(
                selectedSourceExpirationDate = it.selectedSourceExpirationDate ?: expirationDateText,
                lastScannedExpirationDate = if (it.lastScannedGtin == it.selectedSourceGtin) {
                    it.lastScannedExpirationDate ?: expirationDateText
                } else {
                    it.lastScannedExpirationDate
                },
                expirationWarningMessage = it.expirationWarningMessage ?: expirationWarningMessage,
                warningExpirationDate = it.warningExpirationDate
                    ?: expirationDateText.takeIf { expirationWarningMessage != null }
            )
        }
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

    private fun parseFillBarcode(rawBarcode: String): FillBarcode {
        return FillBarcode(
            gtin = normalizeFillGtin(rawBarcode),
            expirationDateText = extractGs1ExpirationDate(rawBarcode)
        )
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

    private fun extractGs1ExpirationDate(rawBarcode: String): String? {
        Regex("""\(17\)(\d{6})""")
            .find(rawBarcode)
            ?.groups
            ?.get(1)
            ?.value
            ?.let { rawDate -> formatGs1ExpirationDate(rawDate) }
            ?.let { return it }

        val digits = rawBarcode.filter(Char::isDigit)
        if (digits.length <= 16) {
            return null
        }

        val rawDate = when {
            digits.startsWith("01") &&
                digits.length >= 24 &&
                digits.substring(16, 18) == "17" -> digits.substring(18, 24)
            digits.startsWith("17") -> digits.substring(2, 8)
            else -> null
        } ?: return null

        return formatGs1ExpirationDate(rawDate)
    }

    private fun formatGs1ExpirationDate(rawDate: String): String? {
        if (rawDate.length != 6 || !rawDate.all(Char::isDigit)) {
            return null
        }

        val year = 2000 + rawDate.substring(0, 2).toInt()
        val month = rawDate.substring(2, 4).toInt()
        val day = rawDate.substring(4, 6).toInt()
        if (month !in 1..12) {
            return null
        }

        val yearMonth = runCatching { YearMonth.of(year, month) }.getOrNull() ?: return null
        return when {
            day == 0 -> "%04d-%02d".format(year, month)
            day in 1..yearMonth.lengthOfMonth() -> "%04d-%02d-%02d".format(year, month, day)
            else -> null
        }
    }

    private fun expirationWarningMessageFor(
        expirationDateText: String?,
        dismissedExpirationDate: String?
    ): String? {
        if (expirationDateText == null ||
            expirationDateText == dismissedExpirationDate ||
            !isExpired(expirationDateText)
        ) {
            return null
        }
        return "期限が切れています"
    }

    private fun isExpired(expirationDateText: String): Boolean {
        val today = Instant.ofEpochMilli(nowMs())
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
        val expirationDate = parseExpirationEndDate(expirationDateText) ?: return false
        return today.isAfter(expirationDate)
    }

    private fun parseExpirationEndDate(expirationDateText: String): LocalDate? {
        Regex("""\d{4}-\d{2}-\d{2}""")
            .matchEntire(expirationDateText)
            ?.let {
                return runCatching { LocalDate.parse(expirationDateText) }.getOrNull()
            }

        Regex("""\d{4}-\d{2}""")
            .matchEntire(expirationDateText)
            ?: return null

        return runCatching {
            val yearMonth = YearMonth.parse(expirationDateText)
            yearMonth.atEndOfMonth()
        }.getOrNull()
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
        private const val SAME_SOURCE_REARM_QUIET_MS = 2200L
        private const val TARGET_SCAN_MESSAGE = "充填先のカセットまたは瓶のコードをスキャンしてください"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as YakuzaiApplication
                FillModeViewModel(
                    drugMasterLookup = app.drugMasterRepository,
                    staffMasterDao = app.database.staffMasterDao(),
                    fillHistoryDao = app.database.fillHistoryDao(),
                    staffSelectionRepository = app.staffSelectionRepository
                )
            }
        }
    }
}

private data class FillBarcode(
    val gtin: String?,
    val expirationDateText: String?
)

data class FillModeUiState(
    val phase: FillModeStage = FillModeStage.SELECT_DRUG,
    val selectedStaffId: String? = null,
    val selectedStaffName: String? = null,
    val selectedStaffKana: String? = null,
    val selectedDrugName: String? = null,
    val selectedYjCode: String? = null,
    val selectedCodes: Set<String> = emptySet(),
    val selectedSourceGtin: String? = null,
    val selectedSourceExpirationDate: String? = null,
    val lastScannedGtin: String? = null,
    val lastScannedExpirationDate: String? = null,
    val targetScanEnabledAtMs: Long = 0L,
    val statusText: String = "充填する薬品のPTPまたは箱をスキャンしてください",
    val expirationWarningMessage: String? = null,
    val warningExpirationDate: String? = null,
    val dismissedExpirationWarningDate: String? = null,
    val isComplete: Boolean = false
)

enum class FillModeStage {
    SELECT_DRUG,
    SELECT_TARGET,
    COMPLETED
}
