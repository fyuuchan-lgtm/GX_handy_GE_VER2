package com.example.yakuzaiapp.ui.master

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.yakuzaiapp.data.local.entity.DrugMaster

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserDrugMasterScreen(
    onBack: () -> Unit,
    viewModel: UserDrugMasterViewModel = viewModel(factory = UserDrugMasterViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf("") }
    var barcode by remember { mutableStateOf("") }
    var packageSpec by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("院内製剤・材料") }
    var deleteTarget by remember { mutableStateOf<DrugMaster?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("院内製剤・材料マスター") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("戻る") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "帳票に記載される名称と、ピッキング時に読み取るバーコードを登録します。",
                )
            }
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称（必須）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                OutlinedTextField(
                    value = barcode,
                    onValueChange = { barcode = it },
                    label = { Text("バーコード（必須）") },
                    supportingText = { Text("GTINまたは施設独自のバーコード文字列を入力") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                OutlinedTextField(
                    value = packageSpec,
                    onValueChange = { packageSpec = it },
                    label = { Text("規格・包装（任意）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("区分（任意）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Button(
                    onClick = {
                        if (viewModel.register(name, barcode, packageSpec, category)) {
                            name = ""
                            barcode = ""
                            packageSpec = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("マスターに登録")
                }
            }
            if (state.items.isNotEmpty()) {
                item {
                    Text("登録済み", fontWeight = FontWeight.Bold)
                }
                items(state.items, key = { it.hot13 }) { item ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(item.drugName, fontWeight = FontWeight.Bold)
                            Text(item.packageSpec ?: "規格・包装なし")
                            Text("バーコード: ${item.gtin.orEmpty()}")
                            TextButton(onClick = { deleteTarget = item }) {
                                Text("削除")
                            }
                        }
                    }
                }
            }
        }
    }

    state.message?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::clearMessage,
            title = { Text("マスター登録") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = viewModel::clearMessage) { Text("OK") }
            },
        )
    }

    deleteTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("登録を削除") },
            text = { Text("${item.drugName}を削除しますか？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(item)
                        deleteTarget = null
                    },
                ) { Text("削除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("キャンセル") }
            },
        )
    }
}
