package com.example.yakuzaiapp.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.yakuzaiapp.YakuzaiApplication
import com.example.yakuzaiapp.data.local.dao.DrugMasterDao
import com.example.yakuzaiapp.data.local.entity.DrugMaster
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DrugDetailViewModel(
    private val dao: DrugMasterDao,
) : ViewModel() {

    private val _drug = MutableStateFlow<DrugMaster?>(null)
    val drug: StateFlow<DrugMaster?> = _drug.asStateFlow()

    fun load(gtin: String) {
        viewModelScope.launch {
            val result = dao.findByAnyGtin(gtin)
            _drug.value = result
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as YakuzaiApplication
                DrugDetailViewModel(app.database.drugMasterDao())
            }
        }
    }
}
