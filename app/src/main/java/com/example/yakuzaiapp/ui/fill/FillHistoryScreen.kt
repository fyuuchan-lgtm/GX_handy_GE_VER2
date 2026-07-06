package com.example.yakuzaiapp.ui.fill

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.yakuzaiapp.R
import com.example.yakuzaiapp.YakuzaiApplication
import com.example.yakuzaiapp.data.local.dao.FillHistoryDao
import com.example.yakuzaiapp.data.local.entity.FillHistory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

private const val DAY_MS = 24L * 60L * 60L * 1000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FillHistoryScreen(
    onBack: () -> Unit,
    viewModel: FillHistoryViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = FillHistoryViewModel.Factory
    )
) {
    val historyList by viewModel.historyList.collectAsStateWithLifecycle()
    var selectedPeriod by remember { mutableStateOf(FillHistoryPeriod.TODAY) }
    val nowMs = remember(historyList, selectedPeriod) { System.currentTimeMillis() }
    val filteredList = remember(historyList, selectedPeriod, nowMs) {
        selectedPeriod.filter(historyList, nowMs)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.fill_history_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.scan_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FillHistoryPeriod.entries.forEach { period ->
                    FilterChip(
                        selected = selectedPeriod == period,
                        onClick = { selectedPeriod = period },
                        label = { Text(stringResource(period.labelRes)) }
                    )
                }
            }
            if (filteredList.isEmpty()) {
                Text(stringResource(R.string.fill_history_empty))
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filteredList, key = { it.id }) { history ->
                        FillHistoryRow(history)
                    }
                }
            }
        }
    }
}

@Composable
private fun FillHistoryRow(history: FillHistory) {
    val unselectedStaff = stringResource(R.string.fill_history_unselected)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(history.drugName, fontWeight = FontWeight.Bold)
            Text(formatDateTime(history.completedAt), style = MaterialTheme.typography.bodySmall)
            Text(
                stringResource(
                    R.string.fill_history_staff,
                    history.staffName.orEmpty().ifBlank {
                        unselectedStaff
                    }
                )
            )
            Text(stringResource(R.string.fill_history_source, history.sourceGtin.orEmpty().ifBlank { "-" }))
            Text(stringResource(R.string.fill_history_confirm_code, history.targetCode))
            Text(
                stringResource(
                    R.string.fill_history_expiration_status,
                    history.expirationDate.orEmpty().ifBlank { "-" },
                    history.status
                )
            )
        }
    }
}

private enum class FillHistoryPeriod(@StringRes val labelRes: Int) {
    TODAY(R.string.fill_history_period_today),
    SEVEN_DAYS(R.string.fill_history_period_seven_days),
    THIRTY_DAYS(R.string.fill_history_period_thirty_days),
    ALL(R.string.fill_history_period_all);

    fun filter(historyList: List<FillHistory>, nowMs: Long): List<FillHistory> {
        val startMs = when (this) {
            TODAY -> startOfToday(nowMs)
            SEVEN_DAYS -> nowMs - 7L * DAY_MS
            THIRTY_DAYS -> nowMs - 30L * DAY_MS
            ALL -> Long.MIN_VALUE
        }
        return historyList.filter { it.completedAt >= startMs }
    }
}

private fun startOfToday(nowMs: Long): Long {
    val formatter = SimpleDateFormat("yyyyMMdd", Locale.JAPAN)
    val today = formatter.format(Date(nowMs))
    return formatter.parse(today)?.time ?: nowMs
}

private fun formatDateTime(ms: Long): String {
    return SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.JAPAN).format(Date(ms))
}

class FillHistoryViewModel(
    fillHistoryDao: FillHistoryDao
) : ViewModel() {
    val historyList: StateFlow<List<FillHistory>> = fillHistoryDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as YakuzaiApplication
                FillHistoryViewModel(app.database.fillHistoryDao())
            }
        }
    }
}
