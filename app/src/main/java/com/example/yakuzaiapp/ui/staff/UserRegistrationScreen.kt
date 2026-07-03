package com.example.yakuzaiapp.ui.staff

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserRegistrationScreen(
    onBack: () -> Unit,
    viewModel: UserRegistrationViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = UserRegistrationViewModel.Factory
    )
) {
    val staffList by viewModel.staffList.collectAsStateWithLifecycle()
    var staffLastName by remember { mutableStateOf("") }
    var staffFirstName by remember { mutableStateOf("") }
    var staffKana by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("利用者登録") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "戻る"
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
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            OutlinedTextField(
                value = staffLastName,
                onValueChange = { staffLastName = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("姓") },
                singleLine = true
            )
            OutlinedTextField(
                value = staffFirstName,
                onValueChange = { staffFirstName = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("名") },
                singleLine = true
            )
            OutlinedTextField(
                value = staffKana,
                onValueChange = { staffKana = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("ふりがな") },
                singleLine = true
            )
            Button(
                onClick = {
                    val trimmedLastName = staffLastName.trim()
                    val trimmedFirstName = staffFirstName.trim()
                    val trimmedKana = staffKana.trim()
                    if (trimmedLastName.isBlank() || trimmedFirstName.isBlank() || trimmedKana.isBlank()) {
                        message = "姓、名、ふりがなを入力してください"
                    } else {
                        viewModel.saveStaff(trimmedLastName, trimmedFirstName, trimmedKana)
                        staffLastName = ""
                        staffFirstName = ""
                        staffKana = ""
                        message = "登録しました"
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("登録する")
            }
            message?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("登録済み利用者", fontWeight = FontWeight.Bold)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(staffList, key = { it.staffId }) { staff ->
                    StaffRow(
                        staff = staff,
                        onDelete = { viewModel.deleteStaff(staff) }
                    )
                }
            }
        }
    }
}

@Composable
private fun StaffRow(
    staff: StaffMaster,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(staff.displayName(), fontWeight = FontWeight.Bold)
                staff.staffKana?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
            }
            OutlinedButton(onClick = onDelete) {
                Text("削除")
            }
        }
    }
}

class UserRegistrationViewModel(
    private val staffMasterDao: StaffMasterDao,
    private val nowMs: () -> Long = System::currentTimeMillis
) : ViewModel() {
    val staffList: StateFlow<List<StaffMaster>> = staffMasterDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveStaff(staffLastName: String, staffFirstName: String, staffKana: String) {
        viewModelScope.launch {
            staffMasterDao.upsert(
                StaffMaster(
                    staffId = "staff_${nowMs()}",
                    staffName = "$staffLastName $staffFirstName",
                    staffLastName = staffLastName,
                    staffFirstName = staffFirstName,
                    staffKana = staffKana
                )
            )
        }
    }

    fun deleteStaff(staff: StaffMaster) {
        viewModelScope.launch {
            staffMasterDao.delete(staff)
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as YakuzaiApplication
                UserRegistrationViewModel(app.database.staffMasterDao())
            }
        }
    }
}
