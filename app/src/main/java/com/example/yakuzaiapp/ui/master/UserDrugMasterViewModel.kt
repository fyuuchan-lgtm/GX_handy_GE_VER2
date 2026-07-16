package com.example.yakuzaiapp.ui.master

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.yakuzaiapp.YakuzaiApplication
import com.example.yakuzaiapp.data.local.entity.DrugMaster
import com.example.yakuzaiapp.repository.DrugMasterRepository
import com.example.yakuzaiapp.util.normalizeMasterBarcode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UserDrugMasterUiState(
    val items: List<DrugMaster> = emptyList(),
    val message: String? = null,
)

class UserDrugMasterViewModel(
    private val repository: DrugMasterRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(UserDrugMasterUiState())
    val uiState: StateFlow<UserDrugMasterUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeUserRegistered().collect { items ->
                _uiState.value = _uiState.value.copy(items = items)
            }
        }
    }

    fun register(
        name: String,
        barcode: String,
        packageSpec: String,
        category: String,
    ): Boolean {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            _uiState.value = _uiState.value.copy(message = "名称を入力してください")
            return false
        }
        val normalizedBarcode = normalizeMasterBarcode(barcode)
        if (normalizedBarcode == null) {
            _uiState.value = _uiState.value.copy(
                message = "3〜64文字のバーコードを入力してください"
            )
            return false
        }

        val userCode = "USER-$normalizedBarcode"
        viewModelScope.launch {
            repository.upsertUserRegistered(
                DrugMaster(
                    drugCode = userCode,
                    drugName = trimmedName,
                    hot13 = userCode,
                    gtin = normalizedBarcode,
                    packageName = trimmedName,
                    packageSpec = packageSpec.trim().ifBlank { null },
                    yjCode = userCode,
                    drugCategory = category.trim().ifBlank { "院内製剤・材料" },
                    isUserRegistered = true,
                )
            )
            _uiState.value = _uiState.value.copy(message = "登録しました")
        }
        return true
    }

    fun delete(item: DrugMaster) {
        viewModelScope.launch {
            repository.deleteUserRegistered(item.hot13)
            _uiState.value = _uiState.value.copy(message = "削除しました")
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as YakuzaiApplication
                UserDrugMasterViewModel(app.drugMasterRepository)
            }
        }
    }
}
