package com.example.yakuzaiapp.ui.dispensing

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.yakuzaiapp.domain.dispensing.DispensingSession
import com.example.yakuzaiapp.domain.dispensing.ExpectedDrugItem
import com.example.yakuzaiapp.domain.dispensing.ItemStatus
import com.example.yakuzaiapp.domain.dispensing.ScanMatchResult
import com.example.yakuzaiapp.util.SoundFeedback
import com.example.yakuzaiapp.util.VibrationFeedback
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DispensingScreen(
    viewModel: DispensingViewModel,
    onStartScan: () -> Unit,
    onReloadQr: () -> Unit,
    onStartPtpScan: () -> Unit,
    onCompleted: () -> Unit
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val longPressDialog by viewModel.longPressDialog.collectAsStateWithLifecycle()
    val completionDialog by viewModel.completionDialog.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.scanFeedback.collect { result ->
            val message = feedbackMessage(result)
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            snackbarHostState.showSnackbar(message)
            if (result is ScanMatchResult.Success) {
                SoundFeedback.playSuccess()
            } else {
                SoundFeedback.playError()
                if (shouldVibrateForFeedback(result)) {
                    VibrationFeedback.error(context)
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        when (uiState) {
            DispensingUiState.Empty -> EmptyContent(padding = padding, onStartScan = onStartScan)
            DispensingUiState.Loading -> LoadingContent(padding = padding)
            is DispensingUiState.Error -> ErrorContent(
                padding = padding,
                message = (uiState as DispensingUiState.Error).message,
                onRetry = onStartScan
            )
            DispensingUiState.Ready -> {
                val currentSession = session
                if (currentSession == null) {
                    EmptyContent(padding = padding, onStartScan = onStartScan)
                } else {
                    ReadyContent(
                        padding = padding,
                        session = currentSession,
                        onStartPtpScan = onStartPtpScan,
                        onReloadQr = onReloadQr,
                        onItemClick = viewModel::onItemClick,
                        onLongPressItem = viewModel::onLongPressItem,
                    )
                }
            }
        }
    }

    longPressDialog?.let { dialog ->
        AlertDialog(
            onDismissRequest = viewModel::onLongPressDismiss,
            title = { Text("自動錠剤分包機") },
            text = {
                Text(
                    when (dialog.currentStatus) {
                        ItemStatus.UNCHECKED ->
                            "${dialog.drugName}を自動錠剤分包機の対象品にします。これによりピッキング対象から外れます"
                        ItemStatus.PACKING_MACHINE ->
                            "${dialog.drugName}を自動錠剤分包機の対象品から外し、ピッキング対象に戻します"
                        ItemStatus.CONFIRMED -> ""
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.onLongPressConfirm(dialog.itemId) }) {
                    Text("はい")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onLongPressDismiss) {
                    Text("いいえ")
                }
            }
        )
    }

    when (val state = completionDialog) {
        CompletionDialogState.Hidden -> Unit
        CompletionDialogState.AllCompleted -> {
            AlertDialog(
                onDismissRequest = { viewModel.onCompletionDismiss() },
                title = { Text("確認完了") },
                text = { Text("すべての薬品が確認済み。ホーム画面に戻る？") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.onCompletionProceed()
                        onCompleted()
                    }) {
                        Text("OK")
                    }
                }
            )
        }
        is CompletionDialogState.HasUnchecked -> {
            val displayNames = state.names.joinToString("\n") { "・$it" }
            val moreText = if (state.count > state.names.size) "\n他 ${state.count - state.names.size} 件" else ""
            AlertDialog(
                onDismissRequest = { viewModel.onCompletionDismiss() },
                title = { Text("未確認があります") },
                text = {
                    Text(
                        "未確認の薬品が ${state.count} 件ある。\n$displayNames$moreText\n\nそれでも終了する？"
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.onCompletionProceed()
                        onCompleted()
                    }) { Text("Yes") }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.onCompletionDismiss() }) {
                        Text("No")
                    }
                }
            )
        }
    }
}

@Composable
fun DispensingCompleteScreen(
    viewModel: DispensingViewModel,
    onDone: () -> Unit,
    onBack: () -> Unit
) {
    val session by viewModel.session.collectAsStateWithLifecycle()

    Scaffold { padding ->
        val currentSession = session
        if (currentSession == null) {
            EmptyContent(padding = padding, onStartScan = onBack)
        } else {
            CompleteContent(
                padding = padding,
                session = currentSession,
                onDone = onDone,
                onBack = onBack
            )
        }
    }
}

@Composable
private fun EmptyContent(
    padding: PaddingValues,
    onStartScan: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("QRを読み込んで開始")
            Button(onClick = onStartScan) {
                Text("QR読み込み")
            }
        }
    }
}

@Composable
private fun LoadingContent(
    padding: PaddingValues
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorContent(
    padding: PaddingValues,
    message: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = message, color = MaterialTheme.colorScheme.error)
            Button(onClick = onRetry) {
                Text("再試行")
            }
        }
    }
}

@Composable
private fun ReadyContent(
    padding: PaddingValues,
    session: DispensingSession,
    onStartPtpScan: () -> Unit,
    onReloadQr: () -> Unit,
    onItemClick: (String) -> Unit,
    onLongPressItem: (String) -> Unit,
) {
    val displayItems = remember(session.items) {
        session.items.sortedWith(compareBy<ExpectedDrugItem> { it.rpNumber }.thenBy { it.id })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .background(Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PatientHeader(session = session)
        }

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color = Color(0xFFBDBDBD)
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.White),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
        ) {
            items(displayItems, key = { it.id }) { item ->
                DrugRow(
                    item = item,
                    onClick = { onItemClick(item.id) },
                    onLongPress = { onLongPressItem(item.id) }
                )
                HorizontalDivider()
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onReloadQr,
                modifier = Modifier.weight(1f),
                shape = RectangleShape
            ) {
                Text("QR再読込", fontSize = 18.sp)
            }
            Button(
                onClick = onStartPtpScan,
                modifier = Modifier.weight(1f),
                shape = RectangleShape
            ) {
                Text("PTP確認", fontSize = 18.sp)
            }
        }
    }
}

@Composable
private fun CompleteContent(
    padding: PaddingValues,
    session: DispensingSession,
    onDone: () -> Unit,
    onBack: () -> Unit
) {
    val displayItems = remember(session.items) {
        session.items.sortedWith(compareBy<ExpectedDrugItem> { it.rpNumber }.thenBy { it.id })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .background(Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PatientHeader(session = session)
        }

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color = Color(0xFFBDBDBD)
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.White),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
        ) {
            items(displayItems, key = { it.id }) { item ->
                DrugRow(
                    item = item,
                    onClick = {},
                    onLongPress = {}
                )
                HorizontalDivider()
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onDone,
                modifier = Modifier
                    .weight(2f)
                    .height(58.dp),
                shape = RectangleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFD32F2F),
                    contentColor = Color.White
                )
            ) {
                Text("完了", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = onBack,
                modifier = Modifier
                    .weight(1f)
                    .height(58.dp),
                shape = RectangleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1565C0),
                    contentColor = Color.White
                )
            ) {
                Text("戻る", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PatientHeader(session: DispensingSession) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RectangleShape,
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = patientSummary(session),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun RpGroupCard(
    items: List<ExpectedDrugItem>,
    onItemClick: (String) -> Unit,
    onLongPressItem: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RectangleShape,
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            items.forEachIndexed { index, item ->
                if (index > 0) {
                    HorizontalDivider()
                }
                DrugRow(
                    item = item,
                    onClick = { onItemClick(item.id) },
                    onLongPress = { onLongPressItem(item.id) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DrugRow(
    item: ExpectedDrugItem,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    val isExcluded = item.status == ItemStatus.PACKING_MACHINE
    val rowBackground = if (isExcluded) Color(0xFF808080) else Color.White
    val badgeText = when (item.status) {
        ItemStatus.UNCHECKED -> "未"
        ItemStatus.CONFIRMED -> "済"
        ItemStatus.PACKING_MACHINE -> "対象外"
    }
    val badgeColor = when (item.status) {
        ItemStatus.UNCHECKED -> Color(0xFFD32F2F)
        ItemStatus.CONFIRMED -> Color(0xFF2E7D32)
        ItemStatus.PACKING_MACHINE -> Color(0xFF1565C0)
    }
    val contentColor = if (isExcluded) Color.White else MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(rowBackground)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.width(64.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "RP${item.rpNumber}",
                color = contentColor,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(badgeColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = badgeText,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
        Text(
            text = item.drugName,
            color = contentColor,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}


private fun feedbackMessage(result: ScanMatchResult): String {
    return when (result) {
        is ScanMatchResult.Success -> "✓ ${result.drugName} 確認"
        is ScanMatchResult.NotInList -> "この薬は処方に含まれていません: ${result.drugName}"
        is ScanMatchResult.AlreadyConfirmed -> "既に確認済みです: ${result.drugName}"
        is ScanMatchResult.PackingMachine -> "この薬は分包機対象です: ${result.drugName}"
        is ScanMatchResult.PackageBarcodeNotSupported -> "箱バーコードは未対応です。シートを読んでください"
        is ScanMatchResult.InvalidBarcodeFormat -> "不明なバーコード形式: ${result.rawCode}"
        is ScanMatchResult.UnregisteredGtin -> "マスター未登録のGTIN: ${result.gtin}"
    }
}

private fun shouldVibrateForFeedback(result: ScanMatchResult): Boolean {
    return result !is ScanMatchResult.Success &&
        result !is ScanMatchResult.AlreadyConfirmed &&
        result !is ScanMatchResult.PackingMachine
}

private fun genderLabel(code: String?): String {
    return when (code) {
        "1" -> "男"
        "2" -> "女"
        else -> "-"
    }
}

private fun patientSummary(session: DispensingSession): String {
    val name = session.patientName?.takeIf { it.isNotBlank() } ?: "-"
    val gender = genderLabel(session.patientGender)
    val ageOrBirthDate = ageLabel(session.patientBirthDate) ?: formatBirthDate(session.patientBirthDate)
    return listOf(name, gender, ageOrBirthDate)
        .filter { it.isNotBlank() && it != "-" }
        .joinToString("　")
}

private fun ageLabel(rawBirthDate: String?): String? {
    val parts = parseBirthDate(rawBirthDate) ?: return null
    val (year, month, day) = parts
    val today = Calendar.getInstance()
    var age = today.get(Calendar.YEAR) - year
    val currentMonth = today.get(Calendar.MONTH) + 1
    val currentDay = today.get(Calendar.DAY_OF_MONTH)
    if (currentMonth < month || (currentMonth == month && currentDay < day)) {
        age--
    }
    return age.takeIf { it in 0..130 }?.let { "${it}才" }
}

private fun formatBirthDate(rawBirthDate: String?): String {
    val parts = parseBirthDate(rawBirthDate) ?: return rawBirthDate?.takeIf { it.isNotBlank() } ?: "-"
    val (year, month, day) = parts
    return "%04d/%02d/%02d".format(year, month, day)
}

private fun parseBirthDate(rawBirthDate: String?): Triple<Int, Int, Int>? {
    val digits = rawBirthDate?.filter { it.isDigit() } ?: return null
    if (digits.length != 8) return null
    val year = digits.substring(0, 4).toIntOrNull() ?: return null
    val month = digits.substring(4, 6).toIntOrNull() ?: return null
    val day = digits.substring(6, 8).toIntOrNull() ?: return null
    if (month !in 1..12 || day !in 1..31) return null
    return Triple(year, month, day)
}
