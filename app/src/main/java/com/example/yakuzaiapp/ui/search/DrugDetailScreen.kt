package com.example.yakuzaiapp.ui.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.yakuzaiapp.data.local.entity.DrugMaster

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrugDetailScreen(
    gtin: String,
    onBack: () -> Unit,
    viewModel: DrugDetailViewModel = viewModel(
        factory = DrugDetailViewModel.Factory,
    ),
) {
    LaunchedEffect(gtin) {
        viewModel.load(gtin)
    }

    val drug by viewModel.drug.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(drug?.displayLabel ?: "薬品詳細") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "戻る",
                        )
                    }
                },
            )
        },
    ) { padding ->
        val current = drug
        if (current == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text("読込中...")
            }
        } else {
            DrugDetailContent(drug = current, modifier = Modifier.padding(padding))
        }
    }
}

@Composable
private fun DrugDetailContent(drug: DrugMaster, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        WarningSection(drug)

        DetailSection(title = "基本情報") {
            DetailRow("薬品名", drug.drugName)
            DetailRow("規格", drug.packageSpec ?: "-")
            DetailRow("メーカー", drug.maker ?: "-")
            drug.drugCategory?.let { DetailRow("分類", it) }
            drug.dosageForm?.let { DetailRow("剤形", it) }
        }

        Spacer(Modifier.height(12.dp))

        DetailSection(title = "識別コード") {
            DetailRow("GTIN", drug.gtin ?: drug.hot13)
            DetailRow("YJコード", drug.yjCode ?: "-")
            DetailRow("販売包装GTIN", drug.gtinSales ?: "-")
            DetailRow("元梱包装GTIN", drug.gtinCase ?: "-")
            drug.janCode?.let { DetailRow("JANコード", it) }
        }

        Spacer(Modifier.height(12.dp))

        DetailSection(title = "包装情報") {
            drug.packageForm?.let { DetailRow("包装形態", it) }
            if (drug.packageUnitCount != null && drug.packageUnitName != null) {
                DetailRow("包装単位", "${drug.packageUnitCount} ${drug.packageUnitName}")
            }
            drug.containerCount?.let { DetailRow("1箱の包装数", it.toString()) }
            drug.packageName?.let { DetailRow("包装名", it) }
        }

        if (!drug.solventName.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            DetailSection(title = "溶解情報") {
                DetailRow("名称", drug.solventName)
                if (!drug.solventVolume.isNullOrBlank()) {
                    DetailRow("容量", "${drug.solventVolume} ${drug.solventUnit ?: ""}".trim())
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        DetailSection(title = "ライフサイクル") {
            drug.noticeDate?.let { DetailRow("通知日", formatYmd(it)) }
            drug.transitionDate?.let { DetailRow("経過措置日", formatYmd(it)) }
            drug.discontinuedDate?.let { DetailRow("販売中止日", formatYmd(it)) }
            drug.lastLotExpiry?.let { DetailRow("最終ロット使用期限", formatYmd(it)) }
            DetailRow("MEDIS更新日", formatYmd(drug.medisUpdateDate ?: "-"))
        }
    }
}

@Composable
private fun WarningSection(drug: DrugMaster) {
    if (!drug.needsWarning && !drug.isDiscontinued && !drug.isInTransition) return

    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        if (drug.needsWarning) {
            WarningBanner(
                text = "注意: ${drug.narcoticFlag ?: drug.biologicalFlag ?: ""}".trim(),
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(4.dp))
        }
        if (drug.isDiscontinued) {
            WarningBanner(
                text = "販売中止 (${formatYmd(drug.discontinuedDate ?: "")})",
                color = MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.height(4.dp))
        }
        if (drug.isInTransition) {
            WarningBanner(
                text = "経過措置中 (${formatYmd(drug.transitionDate ?: "")})",
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

@Composable
private fun WarningBanner(text: String, color: Color) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = color,
        contentColor = Color.White,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(12.dp),
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun DetailSection(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            content()
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            text = label,
            modifier = Modifier.width(120.dp),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            fontSize = 13.sp,
        )
    }
}

private fun formatYmd(ymd: String): String {
    if (ymd.length != 8) return ymd
    return "${ymd.substring(0, 4)}/${ymd.substring(4, 6)}/${ymd.substring(6, 8)}"
}
