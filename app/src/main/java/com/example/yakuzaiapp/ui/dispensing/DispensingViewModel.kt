package com.example.yakuzaiapp.ui.dispensing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.yakuzaiapp.YakuzaiApplication
import com.example.yakuzaiapp.data.jahis.JahisQrParser
import com.example.yakuzaiapp.data.local.entity.DrugMaster
import com.example.yakuzaiapp.data.repository.DrugPreferenceRepository
import com.example.yakuzaiapp.domain.dispensing.DispensingSession
import com.example.yakuzaiapp.domain.dispensing.ExpectedDrugItem
import com.example.yakuzaiapp.domain.dispensing.ExpectedListBuilder
import com.example.yakuzaiapp.domain.dispensing.ExpectedListBuilderContract
import com.example.yakuzaiapp.domain.dispensing.ItemStatus
import com.example.yakuzaiapp.domain.dispensing.ScanMatchResult
import com.example.yakuzaiapp.repository.DrugMasterLookup
import com.example.yakuzaiapp.util.normalizeGtin
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DispensingViewModel(
    private val expectedListBuilder: ExpectedListBuilderContract,
    private val drugMasterLookup: DrugMasterLookup,
    private val drugPreferenceRepository: DrugPreferenceRepository
) : ViewModel() {
    private val _session = MutableStateFlow<DispensingSession?>(null)
    val session: StateFlow<DispensingSession?> = _session.asStateFlow()

    private val _isAllChecked = MutableStateFlow(false)
    val isAllChecked: StateFlow<Boolean> = _isAllChecked.asStateFlow()

    private val _uncheckedCount = MutableStateFlow(0)
    val uncheckedCount: StateFlow<Int> = _uncheckedCount.asStateFlow()

    private val _uncheckedDrugNames = MutableStateFlow(emptyList<String>())
    val uncheckedDrugNames: StateFlow<List<String>> = _uncheckedDrugNames.asStateFlow()

    private val _uiState = MutableStateFlow<DispensingUiState>(DispensingUiState.Empty)
    val uiState: StateFlow<DispensingUiState> = _uiState.asStateFlow()

    private val _scanFeedback = MutableSharedFlow<ScanMatchResult>(replay = 1, extraBufferCapacity = 1)
    val scanFeedback: SharedFlow<ScanMatchResult> = _scanFeedback.asSharedFlow()
    private val _ptpAllCheckedEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val ptpAllCheckedEvent: SharedFlow<Unit> = _ptpAllCheckedEvent.asSharedFlow()
    private var lastPtpFeedbackSignature: String? = null
    private var lastPtpFeedbackAtMs: Long = 0L
    private var lastPtpSuccessGtin: String? = null
    private var lastPtpSuccessAtMs: Long = 0L
    private var ptpCompleted = false

    private val _longPressDialog = MutableStateFlow<LongPressDialogState?>(null)
    val longPressDialog: StateFlow<LongPressDialogState?> = _longPressDialog.asStateFlow()

    private val _completionDialog = MutableStateFlow<CompletionDialogState>(CompletionDialogState.Hidden)
    val completionDialog: StateFlow<CompletionDialogState> = _completionDialog.asStateFlow()

    fun onQrScanned(rawText: String) {
        viewModelScope.launch {
            try {
                _uiState.value = DispensingUiState.Loading
                val prescription = JahisQrParser.parse(rawText)
                val session = expectedListBuilder.build(prescription)
                _session.value = session
                syncDerivedState()
                _uiState.value = DispensingUiState.Ready
                _completionDialog.value = CompletionDialogState.Hidden
            } catch (e: Exception) {
                _uiState.value = DispensingUiState.Error(e.message ?: "エラー")
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun clearSession() {
        _session.value = null
        syncDerivedState()
        _uiState.value = DispensingUiState.Empty
        _longPressDialog.value = null
        _completionDialog.value = CompletionDialogState.Hidden
        _scanFeedback.resetReplayCache()
        lastPtpFeedbackSignature = null
        lastPtpFeedbackAtMs = 0L
        lastPtpSuccessGtin = null
        lastPtpSuccessAtMs = 0L
        ptpCompleted = false
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun clearScanFeedback() {
        _scanFeedback.resetReplayCache()
        lastPtpFeedbackSignature = null
        lastPtpFeedbackAtMs = 0L
        lastPtpSuccessGtin = null
        lastPtpSuccessAtMs = 0L
    }

    fun onPtpScanned(gtin: String) {
        viewModelScope.launch {
            if (ptpCompleted) {
                return@launch
            }

            val normalizedGtin = normalizeGtin(gtin)
            val now = System.currentTimeMillis()
            val matchResult = when (normalizedGtin) {
                null -> ScanMatchResult.InvalidBarcodeFormat(gtin)
                else -> {
                    val drug = drugMasterLookup.findByAnyGtin(normalizedGtin)
                    matchScannedDrug(normalizedGtin, drug)
                }
            }

            if (
                matchResult is ScanMatchResult.AlreadyConfirmed &&
                normalizedGtin == lastPtpSuccessGtin &&
                now - lastPtpSuccessAtMs < 3000L
            ) {
                return@launch
            }

            val feedbackSignature = feedbackSignature(matchResult)
            if (feedbackSignature == lastPtpFeedbackSignature && now - lastPtpFeedbackAtMs < 5000L) {
                return@launch
            }
            lastPtpFeedbackSignature = feedbackSignature
            lastPtpFeedbackAtMs = now

            _scanFeedback.emit(matchResult)

            if (matchResult is ScanMatchResult.Success) {
                lastPtpSuccessGtin = normalizedGtin
                lastPtpSuccessAtMs = now
                updateItemStatus(matchResult.itemId, ItemStatus.CONFIRMED)
                if (_isAllChecked.value) {
                    ptpCompleted = true
                }
            }
        }
    }

    fun onLongPressItem(itemId: String) {
        val item = _session.value?.items?.find { it.id == itemId } ?: return
        when (item.status) {
            ItemStatus.CONFIRMED -> {
                viewModelScope.launch {
                    _scanFeedback.emit(ScanMatchResult.AlreadyConfirmed(item.drugName))
                }
            }

            ItemStatus.UNCHECKED, ItemStatus.PACKING_MACHINE -> {
                _longPressDialog.value = LongPressDialogState(itemId, item.drugName, item.status)
            }
        }
    }

    fun onItemClick(itemId: String) {
        viewModelScope.launch {
            val item = _session.value?.items?.find { it.id == itemId } ?: return@launch
            val newStatus = when (item.status) {
                ItemStatus.UNCHECKED -> ItemStatus.PACKING_MACHINE
                ItemStatus.PACKING_MACHINE -> ItemStatus.UNCHECKED
                ItemStatus.CONFIRMED -> return@launch
            }
            updateItemStatus(itemId, newStatus)

            item.matchedYjCode?.let { yjCode ->
                when (newStatus) {
                    ItemStatus.PACKING_MACHINE -> drugPreferenceRepository.setPackingMachine(yjCode, true)
                    ItemStatus.UNCHECKED -> drugPreferenceRepository.removeYjCode(yjCode)
                    ItemStatus.CONFIRMED -> Unit
                }
            }
        }
    }

    fun onLongPressConfirm(itemId: String) {
        viewModelScope.launch {
            val item = _session.value?.items?.find { it.id == itemId } ?: return@launch
            val newStatus = when (item.status) {
                ItemStatus.UNCHECKED -> ItemStatus.PACKING_MACHINE
                ItemStatus.PACKING_MACHINE -> ItemStatus.UNCHECKED
                ItemStatus.CONFIRMED -> return@launch
            }
            updateItemStatus(itemId, newStatus)

            item.matchedYjCode?.let { yjCode ->
                when (newStatus) {
                    ItemStatus.PACKING_MACHINE -> drugPreferenceRepository.setPackingMachine(yjCode, true)
                    ItemStatus.UNCHECKED -> drugPreferenceRepository.removeYjCode(yjCode)
                    ItemStatus.CONFIRMED -> Unit
                }
            }

            _longPressDialog.value = null
        }
    }

    fun onLongPressDismiss() {
        _longPressDialog.value = null
    }

    fun onCompleteClick() {
        val unchecked = uncheckedCount.value
        val names = uncheckedDrugNames.value
        _completionDialog.value = if (unchecked == 0) {
            CompletionDialogState.AllCompleted
        } else {
            CompletionDialogState.HasUnchecked(unchecked, names.take(5))
        }
    }

    fun onCompletionConfirm() {
        clearSession()
        _completionDialog.value = CompletionDialogState.Hidden
    }

    fun onCompletionProceed() {
        _completionDialog.value = CompletionDialogState.Hidden
    }

    fun onCompletionDismiss() {
        _completionDialog.value = CompletionDialogState.Hidden
    }

    private fun matchScannedDrug(gtin: String, drug: DrugMaster?): ScanMatchResult {
        if (drug == null) {
            return ScanMatchResult.UnregisteredGtin(gtin)
        }

        val session = _session.value
        if (session == null) {
            return ScanMatchResult.NotInList(drug.displayLabel)
        }

        val candidates = findMatchingSessionItems(drug, session)
        if (candidates.isEmpty()) {
            return ScanMatchResult.NotInList(drug.displayLabel)
        }

        val item = candidates.firstOrNull { it.status == ItemStatus.UNCHECKED }
            ?: candidates.first()
        return when (item.status) {
            ItemStatus.CONFIRMED -> ScanMatchResult.AlreadyConfirmed(item.matchedDrugName ?: item.drugName)
            ItemStatus.PACKING_MACHINE -> ScanMatchResult.PackingMachine(item.matchedDrugName ?: item.drugName)
            ItemStatus.UNCHECKED -> ScanMatchResult.Success(item.id, item.matchedDrugName ?: item.drugName)
        }
    }

    private fun findMatchingSessionItems(
        drug: DrugMaster,
        session: DispensingSession
    ): List<ExpectedDrugItem> {
        val yjMatches = drug.yjCode
            ?.takeIf { it.isNotBlank() }
            ?.let { yjCode -> session.items.filter { it.matchedYjCode == yjCode } }
            .orEmpty()
        if (yjMatches.isNotEmpty()) {
            return yjMatches
        }

        return drug.drugCode
            .takeIf { it.isNotBlank() }
            ?.let { drugCode -> session.items.filter { it.matchedYjCode == drugCode } }
            .orEmpty()
    }

    private fun feedbackSignature(result: ScanMatchResult): String {
        return when (result) {
            is ScanMatchResult.Success -> "success:${result.itemId}"
            is ScanMatchResult.NotInList -> "not-in-list:${result.drugName}"
            is ScanMatchResult.AlreadyConfirmed -> "already:${result.drugName}"
            is ScanMatchResult.PackingMachine -> "packing:${result.drugName}"
            is ScanMatchResult.PackageBarcodeNotSupported -> "package:${result.gtin}"
            is ScanMatchResult.InvalidBarcodeFormat -> "invalid:${result.rawCode}"
            is ScanMatchResult.UnregisteredGtin -> "unregistered:${result.gtin}"
        }
    }

    private fun updateItemStatus(itemId: String, newStatus: ItemStatus) {
        _session.update { session ->
            session?.copy(
                items = session.items.map {
                    if (it.id == itemId) {
                        it.copy(
                            status = newStatus,
                            checkedAt = System.currentTimeMillis()
                        )
                    } else {
                        it
                    }
                }
            )
        }
        syncDerivedState()
    }

    private fun syncDerivedState() {
        val session = _session.value
        _isAllChecked.value = session?.items?.all {
            it.status == ItemStatus.CONFIRMED || it.status == ItemStatus.PACKING_MACHINE
        } ?: false
        _uncheckedCount.value = session?.items?.count { it.status == ItemStatus.UNCHECKED } ?: 0
        _uncheckedDrugNames.value = session?.items
            ?.filter { it.status == ItemStatus.UNCHECKED }
            ?.map { it.drugName }
            ?: emptyList()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as YakuzaiApplication
                DispensingViewModel(
                    expectedListBuilder = ExpectedListBuilder(
                        app.database.drugMasterDao(),
                        app.drugPreferenceRepository
                    ),
                    drugMasterLookup = app.drugMasterRepository,
                    drugPreferenceRepository = app.drugPreferenceRepository
                )
            }
        }
    }
}

data class LongPressDialogState(
    val itemId: String,
    val drugName: String,
    val currentStatus: ItemStatus
)

sealed class CompletionDialogState {
    data object Hidden : CompletionDialogState()
    data object AllCompleted : CompletionDialogState()
    data class HasUnchecked(val count: Int, val names: List<String>) : CompletionDialogState()
}

sealed class DispensingUiState {
    data object Empty : DispensingUiState()
    data object Loading : DispensingUiState()
    data object Ready : DispensingUiState()
    data class Error(val message: String) : DispensingUiState()
}
