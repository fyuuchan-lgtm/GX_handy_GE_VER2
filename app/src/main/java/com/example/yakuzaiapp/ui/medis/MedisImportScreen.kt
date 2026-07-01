package com.example.yakuzaiapp.ui.medis

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.yakuzaiapp.YakuzaiApplication
import com.example.yakuzaiapp.data.medis.MedisAutoUpdateState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedisImportScreen(
    onBack: () -> Unit,
    autoUpdateState: MedisAutoUpdateState = MedisAutoUpdateState.Idle,
    onManualUpdate: () -> Unit = {},
    onDismissAutoUpdateMessage: () -> Unit = {},
) {
    val context = LocalContext.current
    val viewModel: MedisImportViewModel = viewModel(
        factory = MedisImportViewModel.Factory(
            context.applicationContext as YakuzaiApplication,
        ),
    )
    val uiState by viewModel.uiState.collectAsState()

    val medisPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            viewModel.selectMedisHotFile(uri)
        }
    }

    val salesPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            viewModel.selectSalesNameFile(uri)
        }
    }

    BackHandler(enabled = uiState.isBackBlocked) {
        // 取り込み中は戻らない
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MEDISマスター取込") },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        enabled = !uiState.isBackBlocked,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "戻る",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(),
            )
        },
    ) { padding ->
        MedisImportContent(
            paddingValues = padding,
            uiState = uiState,
            autoUpdateState = autoUpdateState,
            onSelectMedisHotFile = {
                medisPickerLauncher.launch(
                    arrayOf("text/*", "text/plain", "text/csv", "application/octet-stream", "*/*")
                )
            },
            onSelectSalesNameFile = {
                salesPickerLauncher.launch(
                    arrayOf("text/*", "text/plain", "text/csv", "application/octet-stream", "*/*")
                )
            },
            onImportSelectedFiles = { viewModel.importSelectedFiles() },
            onManualUpdate = onManualUpdate,
            onDismissAutoUpdateMessage = onDismissAutoUpdateMessage,
            onBackToHome = onBack,
            onReset = { viewModel.reset() },
        )
    }
}

@Composable
private fun MedisImportContent(
    paddingValues: PaddingValues,
    uiState: MedisImportUiState,
    autoUpdateState: MedisAutoUpdateState,
    onSelectMedisHotFile: () -> Unit,
    onSelectSalesNameFile: () -> Unit,
    onImportSelectedFiles: () -> Unit,
    onManualUpdate: () -> Unit,
    onDismissAutoUpdateMessage: () -> Unit,
    onBackToHome: () -> Unit,
    onReset: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (uiState.status) {
            ImportStatus.Idle -> IdleView(
                uiState = uiState,
                autoUpdateState = autoUpdateState,
                onSelectMedisHotFile = onSelectMedisHotFile,
                onSelectSalesNameFile = onSelectSalesNameFile,
                onImportSelectedFiles = onImportSelectedFiles,
                onManualUpdate = onManualUpdate,
                onDismissAutoUpdateMessage = onDismissAutoUpdateMessage,
            )
            ImportStatus.Reading,
            ImportStatus.Parsing,
            ImportStatus.Deleting,
            ImportStatus.Inserting -> ProcessingView(uiState = uiState)
            ImportStatus.Completed -> CompletedView(
                uiState = uiState,
                onBackToHome = onBackToHome,
                onImportAgain = onReset,
            )
            ImportStatus.Failed -> FailedView(
                errorMessage = uiState.errorMessage ?: "不明なエラー",
                onRetry = onReset,
                onBackToHome = onBackToHome,
            )
        }
    }
}

@Composable
private fun IdleView(
    uiState: MedisImportUiState,
    autoUpdateState: MedisAutoUpdateState,
    onSelectMedisHotFile: () -> Unit,
    onSelectSalesNameFile: () -> Unit,
    onImportSelectedFiles: () -> Unit,
    onManualUpdate: () -> Unit,
    onDismissAutoUpdateMessage: () -> Unit,
) {
    val canImport = uiState.selectedMedisHotFileName != null || uiState.selectedSalesNameFileName != null
    val autoUpdateRunning = autoUpdateState is MedisAutoUpdateState.Running

    Icon(
        imageVector = Icons.Default.FileOpen,
        contentDescription = null,
        modifier = Modifier.height(80.dp),
        tint = MaterialTheme.colorScheme.primary,
    )
    Spacer(modifier = Modifier.height(24.dp))
    Text(
        text = "ファイルを選んでから、取り込みボタンを押してください。",
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = "MEDIS HOT と販売名ファイルは別々に選びます。選んだものだけ取り込みます。",
        fontSize = 14.sp,
        color = Color.Gray,
        textAlign = TextAlign.Center,
    )

    Spacer(modifier = Modifier.height(24.dp))
    Button(
        onClick = onManualUpdate,
        enabled = !autoUpdateRunning,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(if (autoUpdateRunning) "データ更新中..." else "最新版をネットから更新")
    }

    when (autoUpdateState) {
        is MedisAutoUpdateState.Completed -> {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "データ更新完了しました",
                fontSize = 12.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onDismissAutoUpdateMessage,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("更新結果を閉じる")
            }
        }
        is MedisAutoUpdateState.Error -> {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = autoUpdateState.message,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onDismissAutoUpdateMessage,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("メッセージを閉じる")
            }
        }
        MedisAutoUpdateState.Idle,
        is MedisAutoUpdateState.Running -> Unit
    }

    Spacer(modifier = Modifier.height(24.dp))
    OutlinedButton(
        onClick = onSelectMedisHotFile,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("ファイルを選ぶ")
    }

    Spacer(modifier = Modifier.height(24.dp))
    OutlinedButton(
        onClick = onSelectSalesNameFile,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("ファイルを選ぶ")
    }

    Spacer(modifier = Modifier.height(24.dp))
    Button(
        onClick = onImportSelectedFiles,
        enabled = canImport,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("選択済みのファイルを取り込む")
    }
}

@Composable
private fun ProcessingView(uiState: MedisImportUiState) {
    Text(
        text = uiState.phaseLabel,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
    )

    if (uiState.selectedFileName != null) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = uiState.selectedFileName,
            fontSize = 12.sp,
            color = Color.Gray,
        )
    }

    Spacer(modifier = Modifier.height(32.dp))

    if (uiState.totalCount > 0) {
        val percent = (uiState.progressFraction * 100).toInt().coerceIn(0, 100)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            LinearProgressIndicator(
                progress = { uiState.progressFraction },
                modifier = Modifier.fillMaxSize(),
            )
            Text(
                text = "$percent%",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    } else {
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp),
        )
    }

    Spacer(modifier = Modifier.height(24.dp))
    Text(
        text = "処理中は画面を閉じないでください。",
        fontSize = 12.sp,
        color = Color.Gray,
    )
}

@Composable
private fun CompletedView(
    uiState: MedisImportUiState,
    onBackToHome: () -> Unit,
    onImportAgain: () -> Unit,
) {
    val result = uiState.result ?: return

    Icon(
        imageVector = Icons.Default.CheckCircle,
        contentDescription = null,
        modifier = Modifier.height(80.dp),
        tint = Color(0xFF388E3C),
    )
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = "${uiState.operationLabel} の取り込み完了",
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
    )
    Spacer(modifier = Modifier.height(24.dp))

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            ResultRow("取込件数", "${formatNumber(result.totalRecords)} 行")
            Spacer(modifier = Modifier.height(8.dp))
            ResultRow("スキップ件数", "${formatNumber(result.skippedRecords)} 行")
            Spacer(modifier = Modifier.height(8.dp))
            ResultRow("エラー行数", "${formatNumber(result.errorLineCount)} 行")
            Spacer(modifier = Modifier.height(8.dp))
            ResultRow("所要時間", "${result.elapsedMs / 1000} 秒")
        }
    }

    Spacer(modifier = Modifier.height(32.dp))

    Button(
        onClick = onBackToHome,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("ホームに戻る")
    }
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedButton(
        onClick = onImportAgain,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("もう一度取り込む")
    }
}

@Composable
private fun FailedView(
    errorMessage: String,
    onRetry: () -> Unit,
    onBackToHome: () -> Unit,
) {
    Icon(
        imageVector = Icons.Default.Error,
        contentDescription = null,
        modifier = Modifier.height(80.dp),
        tint = Color(0xFFD32F2F),
    )
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = "取り込み失敗",
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
    )
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = errorMessage,
        fontSize = 14.sp,
        color = Color.Gray,
        textAlign = TextAlign.Center,
    )
    Spacer(modifier = Modifier.height(32.dp))
    Button(
        onClick = onRetry,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("やり直す")
    }
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedButton(
        onClick = onBackToHome,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("ホームに戻る")
    }
}

@Composable
private fun ResultRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            fontSize = 14.sp,
        )
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun formatNumber(n: Int): String = "%,d".format(n)
