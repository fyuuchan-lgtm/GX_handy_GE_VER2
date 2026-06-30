package com.example.yakuzaiapp.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.yakuzaiapp.YakuzaiApplication
import com.example.yakuzaiapp.data.local.dao.DrugMasterDao
import com.example.yakuzaiapp.data.local.entity.DrugMaster
import com.example.yakuzaiapp.util.toKatakana
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DrugSearchUiState(
    val keyword: String = "",
    val results: List<DrugMaster> = emptyList(),
    val totalCount: Int = 0,
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class DrugSearchViewModel(
    private val dao: DrugMasterDao,
) : ViewModel() {

    private val _state = MutableStateFlow(DrugSearchUiState())
    val state: StateFlow<DrugSearchUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.update { it.copy(totalCount = dao.count()) }
        }

        _state
            .map { it.keyword }
            .distinctUntilChanged()
            .debounce(50)
            .flatMapLatest { keyword ->
                if (keyword.length < MIN_QUERY_LENGTH) {
                    flowOf(emptyList())
                } else {
                    val normalizedQuery = keyword.toKatakana()
                    dao.searchByKeyword(normalizedQuery)
                }
            }
            .onEach { results ->
                _state.update { it.copy(results = results.take(MAX_RESULTS)) }
            }
            .launchIn(viewModelScope)
    }

    fun onKeywordChange(newKeyword: String) {
        _state.update { it.copy(keyword = newKeyword) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as YakuzaiApplication
                DrugSearchViewModel(app.database.drugMasterDao())
            }
        }
    }
}
