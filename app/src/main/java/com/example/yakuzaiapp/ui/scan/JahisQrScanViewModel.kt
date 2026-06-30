package com.example.yakuzaiapp.ui.scan

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.yakuzaiapp.domain.jahis.AssembleResult
import com.example.yakuzaiapp.domain.jahis.DetectedQr
import com.example.yakuzaiapp.domain.jahis.JahisQrAssembler
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "JahisQrScanViewModel"
private const val AUTO_ASSEMBLE_DEBOUNCE_MS = 2000L

data class RecordResult(
    val count: Int,
    val isComplete: Boolean
)

internal fun isStructuredAppendComplete(fragments: List<DetectedQr>): Boolean {
    if (fragments.isEmpty()) return false
    if (fragments.any { it.saSequence == null && it.saTotal == null && it.saParity == null }) return false

    val totals = fragments.mapNotNull { it.saTotal }.distinct()
    if (totals.size > 1) return false

    val parities = fragments.mapNotNull { it.saParity }.distinct()
    if (parities.size > 1) return false

    val sequences = fragments.mapNotNull { it.saSequence }.toSet()
    if (sequences.size != fragments.count { it.saSequence != null }) return false
    if (sequences.isEmpty()) return false

    val total = totals.singleOrNull() ?: (sequences.maxOrNull()?.plus(1) ?: return false)
    if (total > 16) return false
    if (total <= 0) return false

    val expectedSequences = (0 until total).toSet()
    return sequences == expectedSequences
}

private fun escapeForLog(s: String): String {
    return s
        .replace("\\", "\\\\")
        .replace("\r", "\\r")
        .replace("\n", "\\n")
}

class JahisQrScanViewModel : ViewModel() {
    private val _fragments = MutableStateFlow<List<DetectedQr>>(emptyList())
    val fragments: StateFlow<List<DetectedQr>> = _fragments.asStateFlow()
    private val _autoAssembleEvent = MutableStateFlow<AssembleResult.Success?>(null)
    val autoAssembleEvent: StateFlow<AssembleResult.Success?> = _autoAssembleEvent.asStateFlow()
    private var debounceJob: Job? = null

    fun recordDetections(detections: List<DetectedQr>): RecordResult {
        if (detections.isEmpty()) {
            val current = _fragments.value
            return RecordResult(current.size, isStructuredAppendComplete(current))
        }

        val current = _fragments.value.toMutableList()

        var added = 0
        var mutated = false
        detections.forEach { detection ->
            val text = detection.text
            val saSummary = "seq=${detection.saSequence ?: "null"}/total=${detection.saTotal ?: "null"}/parity=${detection.saParity ?: "null"}"
            val keyHead = escapeForLog(text.take(40))
            val keyTail = escapeForLog(text.takeLast(40))
            if (text.isBlank()) {
                Log.d(
                    TAG,
                    "no new fragment accepted; key-head='$keyHead' key-tail='$keyTail' sa=$saSummary existing-keys-count=${current.size}"
                )
                return@forEach
            }
            val existingIndex = current.indexOfFirst { existing ->
                existing.text == text ||
                    existing.text.startsWith(text.take(60)) ||
                    text.startsWith(existing.text.take(60))
            }

            if (existingIndex >= 0) {
                val existing = current[existingIndex]
                if (text.length > existing.text.length) {
                    current[existingIndex] = detection.copy(text = text)
                    mutated = true
                    Log.d(
                        TAG,
                        "replaced fragment with longer one old-len=${existing.text.length} new-len=${text.length} key-head='$keyHead' key-tail='$keyTail' sa=$saSummary existing-keys-count=${current.size}"
                    )
                } else {
                    Log.d(
                        TAG,
                        "no new fragment accepted; key-head='$keyHead' key-tail='$keyTail' sa=$saSummary existing-keys-count=${current.size}"
                    )
                }
                return@forEach
            }

            current.add(detection.copy(text = text))
            added++
            mutated = true
            Log.d(
                TAG,
                "accepted new fragment count=${current.size} added=$added key-head='$keyHead' key-tail='$keyTail' sa=$saSummary existing-keys-count=${current.size}"
            )
        }

        if (mutated) {
            _fragments.value = current.sortedWith(
                compareBy<DetectedQr>({ it.saSequence ?: Int.MAX_VALUE }, { it.left })
            )
            scheduleAutoAssemble()
        } else {
            Log.d(TAG, "no new fragment accepted; total=${_fragments.value.size}")
        }

        val snapshot = _fragments.value
        return RecordResult(
            count = snapshot.size,
            isComplete = isStructuredAppendComplete(snapshot)
        )
    }

    fun clear() {
        debounceJob?.cancel()
        debounceJob = null
        _fragments.value = emptyList()
        _autoAssembleEvent.value = null
        Log.d(TAG, "cleared")
    }

    fun assemble(source: String = "manual"): AssembleResult {
        val snapshot = _fragments.value
        Log.d(TAG, "assemble requested source=$source count=${snapshot.size}")
        return JahisQrAssembler.tryAssemble(snapshot)
    }

    fun consumeAutoAssembleEvent() {
        _autoAssembleEvent.value = null
    }

    private fun scheduleAutoAssemble() {
        debounceJob?.cancel()
        debounceJob = viewModelScope.launch {
            delay(AUTO_ASSEMBLE_DEBOUNCE_MS)
            val result = assemble(source = "auto-debounce")
            if (result is AssembleResult.Success) {
                Log.d(TAG, "auto-assemble candidate succeeded fullText-len=${result.fullText.length}; waiting for manual confirmation")
            } else {
                Log.d(TAG, "auto-assemble failed result=${result.javaClass.simpleName}")
            }
        }
    }

}
