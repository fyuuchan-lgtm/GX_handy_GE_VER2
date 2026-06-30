package com.example.yakuzaiapp.ui.result

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.yakuzaiapp.YakuzaiApplication
import com.example.yakuzaiapp.data.local.entity.DrugMaster
import com.example.yakuzaiapp.util.normalizeGtin

@Composable
fun ResultScreen(
    gtin: String?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember {
        (context.applicationContext as YakuzaiApplication).drugMasterRepository
    }

    val rawGtin = remember(gtin) { gtin?.trim().orEmpty() }
    val normalizedGtin = remember(rawGtin) {
        if (rawGtin.isBlank()) null else normalizeGtin(rawGtin)
    }

    var loading by remember { mutableStateOf(true) }
    var matched by remember { mutableStateOf<DrugMaster?>(null) }

    LaunchedEffect(normalizedGtin) {
        if (normalizedGtin == null) {
            loading = false
            matched = null
        } else {
            loading = true
            matched = repository.findByAnyGtin(normalizedGtin)
            loading = false
        }
    }

    val bgColor = when {
        rawGtin.isBlank() -> Color(0xFFF5F5F5)
        normalizedGtin == null -> Color(0xFFFFF3CD)
        loading -> Color(0xFFF5F5F5)
        matched != null -> Color(0xFFC8E6C9)
        else -> Color(0xFFFFCDD2)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("確認結果", style = MaterialTheme.typography.headlineMedium)

        when {
            rawGtin.isBlank() -> {
                Text("GTIN が指定されていません。")
            }

            normalizedGtin == null -> {
                Text("不明なバーコード形式: $rawGtin")
            }

            loading -> {
                Text("検索中...")
            }

            matched != null -> {
                val drug = matched!!
                Text("OK", style = MaterialTheme.typography.headlineSmall)
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("薬品名: ${drug.drugName}")
                        Text("薬品コード: ${drug.drugCode}")
                        Text("規格: ${drug.packageSpec ?: "-"}")
                        Text("単位: ${drug.unit ?: "-"}")
                        Text("GTIN/HOT13: ${drug.gtin ?: drug.hot13}")
                    }
                }
            }

            else -> {
                Text("NG", style = MaterialTheme.typography.headlineSmall)
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("読み取った GTIN: $normalizedGtin")
                        Text("この GTIN はマスターに見つかりませんでした。")
                    }
                }
            }
        }

        Button(onClick = onBack) {
            Text("戻る")
        }
    }
}
