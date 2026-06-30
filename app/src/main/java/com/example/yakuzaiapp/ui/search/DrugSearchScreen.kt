package com.example.yakuzaiapp.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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

internal const val MIN_QUERY_LENGTH = 3
internal const val MAX_RESULTS = 500

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrugSearchScreen(
    onBack: () -> Unit,
    onDrugClick: (String) -> Unit,
    viewModel: DrugSearchViewModel = viewModel(
        factory = DrugSearchViewModel.Factory,
    ),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("薬品検索", fontWeight = FontWeight.Bold)
                        Text(
                            text = "登録: ${"%,d".format(state.totalCount)} 件",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            OutlinedTextField(
                value = state.keyword,
                onValueChange = viewModel::onKeywordChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("薬品名・メーカー名・包装名で検索") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (state.keyword.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onKeywordChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "クリア")
                        }
                    }
                },
                singleLine = true,
            )

            HorizontalDivider()

            when {
                state.keyword.length < MIN_QUERY_LENGTH -> {
                    EmptyMessage("薬品名・メーカー名を${MIN_QUERY_LENGTH}文字以上入力して検索してください")
                }
                state.results.isEmpty() -> {
                    EmptyMessage("該当する薬品が見つかりません")
                }
                else -> {
                    Text(
                        text = "検索結果: ${state.results.size} 件" +
                            if (state.results.size >= MAX_RESULTS) "・上限表示" else "",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(
                            items = state.results,
                            key = { it.gtin ?: it.hot13 },
                        ) { drug ->
                            DrugListItem(drug = drug, onClick = { onDrugClick(drug.gtin ?: drug.hot13) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DrugListItem(
    drug: DrugMaster,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        onClick = onClick,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            if (drug.needsWarning || drug.isDiscontinued || drug.isInTransition) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (drug.needsWarning) {
                        WarningBadge(
                            text = drug.narcoticFlag ?: "注意",
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    if (drug.isDiscontinued) {
                        WarningBadge(
                            text = "販売中止",
                            color = MaterialTheme.colorScheme.outline,
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    if (drug.isInTransition) {
                        WarningBadge(
                            text = "経過措置",
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }

            Text(
                text = drug.displayLabel,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "${drug.packageSpec ?: "-"}  /  ${drug.maker ?: "-"}",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            drug.packageName?.takeIf { it != drug.drugName }?.let {
                Text(
                    text = it,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
private fun WarningBadge(text: String, color: Color) {
    Surface(
        color = color,
        contentColor = Color.White,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun EmptyMessage(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
