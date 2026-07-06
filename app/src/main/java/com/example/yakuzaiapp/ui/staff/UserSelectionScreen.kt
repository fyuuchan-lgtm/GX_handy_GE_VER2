package com.example.yakuzaiapp.ui.staff

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.yakuzaiapp.YakuzaiApplication
import com.example.yakuzaiapp.data.local.dao.StaffMasterDao
import com.example.yakuzaiapp.data.local.entity.StaffMaster
import com.example.yakuzaiapp.data.repository.StaffSelectionRepository
import com.example.yakuzaiapp.ui.home.HomeBottomTab
import com.example.yakuzaiapp.ui.home.HomeBottomTabBar
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserSelectionScreen(
    onBack: () -> Unit,
    onSelected: () -> Unit,
    onHomeClick: () -> Unit,
    onAuditClick: () -> Unit,
    onReportClick: () -> Unit,
    onFillClick: () -> Unit,
    onDataUpdateClick: () -> Unit,
    viewModel: UserSelectionViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = UserSelectionViewModel.Factory
    )
) {
    val staffList by viewModel.staffList.collectAsStateWithLifecycle()
    val selectedStaffId by viewModel.selectedStaffId.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("利用者選択") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "戻る"
                        )
                    }
                }
            )
        },
        bottomBar = {
            HomeBottomTabBar(
                selectedTab = HomeBottomTab.USER_SELECT,
                onHomeClick = onHomeClick,
                onAuditClick = onAuditClick,
                onReportClick = onReportClick,
                onFillClick = onFillClick,
                onDataUpdateClick = onDataUpdateClick,
            )
        }
    ) { padding ->
        if (staffList.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("利用者が登録されていません", fontWeight = FontWeight.Bold)
                Text("右上メニューの利用者登録から登録してください")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(staffList, key = { it.staffId }) { staff ->
                    UserSelectionRow(
                        staff = staff,
                        selected = staff.staffId == selectedStaffId,
                        onClick = {
                            viewModel.selectStaff(staff)
                            onSelected()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun UserSelectionRow(
    staff: StaffMaster,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) Color(0xFF0D47A1) else Color.White,
            contentColor = if (selected) Color.White else Color.Unspecified
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(staff.displayName(), fontWeight = FontWeight.Bold)
                staff.staffKana?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (selected) {
                Text("選択中", fontWeight = FontWeight.Bold)
            }
        }
    }
}

class UserSelectionViewModel(
    private val staffSelectionRepository: StaffSelectionRepository,
    staffMasterDao: StaffMasterDao
) : ViewModel() {
    val staffList: StateFlow<List<StaffMaster>> = staffMasterDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val selectedStaffId: StateFlow<String?> = staffSelectionRepository.selectedStaffId

    fun selectStaff(staff: StaffMaster) {
        staffSelectionRepository.selectStaff(staff.staffId)
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as YakuzaiApplication
                UserSelectionViewModel(
                    staffSelectionRepository = app.staffSelectionRepository,
                    staffMasterDao = app.database.staffMasterDao()
                )
            }
        }
    }
}
