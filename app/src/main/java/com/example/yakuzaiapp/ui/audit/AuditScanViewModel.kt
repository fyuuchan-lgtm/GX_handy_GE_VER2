package com.example.yakuzaiapp.ui.audit

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.yakuzaiapp.YakuzaiApplication
import com.example.yakuzaiapp.domain.audit.DetectedDrugLine
import com.example.yakuzaiapp.domain.audit.DocumentOcrParser
import com.example.yakuzaiapp.domain.audit.DrugIdentity
import com.example.yakuzaiapp.domain.audit.DrugMasterMatcher
import com.example.yakuzaiapp.domain.audit.MatchResult
import com.example.yakuzaiapp.domain.audit.MatchStatus
import com.example.yakuzaiapp.data.repository.AuditSettingsRepository
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "AuditOcr"

class AuditScanViewModel(
    private val matcher: DrugMasterMatcher,
    private val recognizer: OcrRecognizer = MlKitOcrRecognizer,
    private val auditSettingsRepository: AuditSettingsRepository? = null
) : ViewModel() {
    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()
    private val _detectedLines = MutableStateFlow<List<DetectedDrugLine>>(emptyList())
    val detectedLines: StateFlow<List<DetectedDrugLine>> = _detectedLines.asStateFlow()
    private val _matchResults = MutableStateFlow<List<MatchResult>>(emptyList())
    val matchResults: StateFlow<List<MatchResult>> = _matchResults.asStateFlow()
    private val _baseMatchResults = MutableStateFlow<List<MatchResult>>(emptyList())
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    private val _ocrCompletedEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val ocrCompletedEvents: SharedFlow<Unit> = _ocrCompletedEvents

    fun processImage(
        image: InputImage,
        onFinished: () -> Unit
    ) {
        if (_isProcessing.value) {
            onFinished()
            return
        }

        _isProcessing.value = true
        recognizer.process(image)
            .addOnSuccessListener { result ->
                logOcrResult(result)
                viewModelScope.launch {
                    try {
                        val lines = DocumentOcrParser.parse(
                            text = result,
                            includeQuantity = auditSettingsRepository
                                ?.readQuantityFromDocument
                                ?.value
                                ?: true
                        )
                        _detectedLines.value = lines
                        val matched = matcher.matchAll(lines)
                        _baseMatchResults.value = matched
                        _matchResults.value = matched
                        _errorMessage.value = null
                        _ocrCompletedEvents.tryEmit(Unit)
                    } catch (e: Throwable) {
                        _errorMessage.value = e.message ?: "Master matching failed"
                        Log.e(TAG, "Master matching failed", e)
                    } finally {
                        _isProcessing.value = false
                        onFinished()
                    }
                }
            }
            .addOnFailureListener { e ->
                _errorMessage.value = e.message ?: "OCR failed"
                Log.e(TAG, "OCR failed", e)
                _isProcessing.value = false
                onFinished()
            }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun clearResult() {
        _detectedLines.value = emptyList()
        _matchResults.value = emptyList()
        _baseMatchResults.value = emptyList()
    }

    internal fun seedResultsForTest(results: List<MatchResult>) {
        _baseMatchResults.value = results
        _matchResults.value = results
    }

    fun selectCandidate(index: Int, identity: DrugIdentity) {
        viewModelScope.launch {
            val result = _matchResults.value.getOrNull(index)
            if (result != null) {
                matcher.rememberSelection(result, identity)
            }
            _matchResults.update { current ->
                current.mapIndexed { itemIndex, item ->
                    if (itemIndex == index) {
                        item.copy(
                            candidates = listOf(identity),
                            status = MatchStatus.CONFIRMED,
                            learnedFromPreference = true
                        )
                    } else {
                        item
                    }
                }
            }
        }
    }

    fun clearMatch(index: Int) {
        _matchResults.update { current ->
            current.mapIndexed { itemIndex, result ->
                if (itemIndex == index) {
                    result.copy(
                        candidates = emptyList(),
                        status = MatchStatus.NOT_FOUND
                    )
                } else {
                    result
                }
            }
        }
    }

    fun clearLearning(index: Int) {
        viewModelScope.launch {
            val result = _matchResults.value.getOrNull(index) ?: return@launch
            val baseResult = _baseMatchResults.value.getOrNull(index)
            if (result.learnedFromPreference) {
                matcher.clearLearning(result)
                val rematched = matcher.match(result.ocrName, result.quantityText)
                _baseMatchResults.update { current ->
                    current.mapIndexed { itemIndex, item ->
                        if (itemIndex == index) rematched else item
                    }
                }
                _matchResults.update { current ->
                    current.mapIndexed { itemIndex, item ->
                        if (itemIndex == index) rematched else item
                    }
                }
            } else if (baseResult != null) {
                _matchResults.update { current ->
                    current.mapIndexed { itemIndex, item ->
                        if (itemIndex == index) baseResult else item
                    }
                }
            }
        }
    }

    fun searchCandidates(keyword: String, onResult: (List<DrugIdentity>) -> Unit) {
        viewModelScope.launch {
            onResult(matcher.searchCandidates(keyword))
        }
    }

    fun searchCandidates(
        keyword: String,
        contextName: String?,
        onResult: (List<DrugIdentity>) -> Unit
    ) {
        viewModelScope.launch {
            onResult(matcher.searchCandidates(keyword = keyword, contextName = contextName))
        }
    }

    override fun onCleared() {
        recognizer.close()
        super.onCleared()
    }

    private fun logOcrResult(result: Text) {
        val lineCount = result.textBlocks.sumOf { it.lines.size }
        Log.d(
            TAG,
            "OCR completed text-len=${result.text.length} blocks=${result.textBlocks.size} lines=$lineCount"
        )
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as YakuzaiApplication
                AuditScanViewModel(
                    matcher = DrugMasterMatcher(
                        drugMasterDao = app.database.drugMasterDao(),
                        preferenceDao = app.database.auditDrugPreferenceDao()
                    ),
                    auditSettingsRepository = app.auditSettingsRepository
                )
            }
        }
    }
}

interface OcrRecognizer {
    fun process(image: InputImage): com.google.android.gms.tasks.Task<Text>
    fun close()
}

private object MlKitOcrRecognizer : OcrRecognizer {
    private val delegate = TextRecognition.getClient(
        JapaneseTextRecognizerOptions.Builder().build()
    )

    override fun process(image: InputImage): com.google.android.gms.tasks.Task<Text> {
        return delegate.process(image)
    }

    override fun close() {
        delegate.close()
    }
}
