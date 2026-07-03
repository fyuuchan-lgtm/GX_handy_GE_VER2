package com.example.yakuzaiapp.ui.audit

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.yakuzaiapp.R
import com.example.yakuzaiapp.domain.audit.DrugIdentity
import com.example.yakuzaiapp.domain.audit.MatchResult
import com.example.yakuzaiapp.domain.audit.MatchStatus
import com.example.yakuzaiapp.ui.home.HomeBottomTab
import com.example.yakuzaiapp.ui.home.HomeBottomTabBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditResultScreen(
    onBack: () -> Unit,
    onRetake: () -> Unit,
    onProceedPtp: () -> Unit,
    onHomeClick: () -> Unit,
    onAuditClick: () -> Unit,
    onReportClick: () -> Unit,
    onFillClick: () -> Unit,
    onDataUpdateClick: () -> Unit,
    viewModel: AuditScanViewModel = viewModel(factory = AuditScanViewModel.Factory)
) {
    val matchResults by viewModel.matchResults.collectAsStateWithLifecycle()
    var candidateDialogIndex by remember { mutableStateOf<Int?>(null) }
    var manualSearchIndex by remember { mutableStateOf<Int?>(null) }

    val confirmedCount = matchResults.count { it.status == MatchStatus.CONFIRMED }
    val needReviewCount = matchResults.size - confirmedCount

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.audit_result_title)) },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text(stringResource(R.string.scan_back))
                    }
                },
                actions = {
                    TextButton(onClick = onRetake) {
                        Text(stringResource(R.string.audit_retake))
                    }
                }
            )
        },
        bottomBar = {
            Column {
                Button(
                    onClick = onProceedPtp,
                    enabled = matchResults.isNotEmpty() && needReviewCount == 0,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp)
                        .height(58.dp)
                ) {
                    Text(
                        text = stringResource(R.string.audit_proceed_ptp),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
                HomeBottomTabBar(
                    selectedTab = HomeBottomTab.AUDIT,
                    onHomeClick = onHomeClick,
                    onAuditClick = onAuditClick,
                    onReportClick = onReportClick,
                    onFillClick = onFillClick,
                    onDataUpdateClick = onDataUpdateClick
                )
            }
        }
    ) { padding ->
        if (matchResults.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(R.string.audit_no_drugs_detected),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = stringResource(
                            R.string.audit_summary,
                            matchResults.size,
                            confirmedCount,
                            needReviewCount
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                items(matchResults.size) { index ->
                    MatchResultCard(
                        result = matchResults[index],
                        onClick = {
                            when (matchResults[index].status) {
                                MatchStatus.CONFIRMED -> Unit
                                MatchStatus.AMBIGUOUS -> candidateDialogIndex = index
                                MatchStatus.NOT_FOUND -> manualSearchIndex = index
                            }
                        },
                        onClearLearning = {
                            viewModel.clearLearning(index)
                        }
                    )
                }
            }
        }
    }

    candidateDialogIndex?.let { index ->
        CandidateDialog(
            title = stringResource(R.string.audit_select_candidate),
            candidates = matchResults.getOrNull(index)?.candidates.orEmpty(),
            onSelect = { drug ->
                viewModel.selectCandidate(index, drug)
                candidateDialogIndex = null
            },
            onManualSearch = {
                manualSearchIndex = index
                candidateDialogIndex = null
            },
            onNoMatch = {
                viewModel.clearMatch(index)
                candidateDialogIndex = null
            },
            onDismiss = { candidateDialogIndex = null }
        )
    }

    manualSearchIndex?.let { index ->
        ManualSearchDialog(
            contextName = matchResults.getOrNull(index)?.ocrName,
            viewModel = viewModel,
            onSelect = { drug ->
                viewModel.selectCandidate(index, drug)
                manualSearchIndex = null
            },
            onDismiss = { manualSearchIndex = null }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MatchResultCard(
    result: MatchResult,
    onClick: () -> Unit,
    onClearLearning: () -> Unit
) {
    val containerColor = when (result.status) {
        MatchStatus.CONFIRMED -> Color(0xFFE8F5E9)
        MatchStatus.AMBIGUOUS -> Color(0xFFFFF8E1)
        MatchStatus.NOT_FOUND -> Color(0xFFFFEBEE)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    if (result.learnedFromPreference) onClearLearning()
                }
            ),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = result.displayName(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                StatusChip(result = result)
            }
            if (result.learnedFromPreference) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = {},
                        label = { Text(stringResource(R.string.audit_learned_badge)) }
                    )
                    TextButton(onClick = onClearLearning) {
                        Text(stringResource(R.string.audit_clear_learning))
                    }
                }
                Text(
                    text = stringResource(R.string.audit_learned_confirmed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StatusChip(result: MatchResult) {
    val label = when (result.status) {
        MatchStatus.CONFIRMED -> stringResource(R.string.audit_status_confirmed)
        MatchStatus.AMBIGUOUS -> stringResource(R.string.audit_status_ambiguous, result.candidates.size)
        MatchStatus.NOT_FOUND -> stringResource(R.string.audit_status_not_found)
    }
    AssistChip(onClick = {}, label = { Text(label) })
}

@Composable
private fun CandidateDialog(
    title: String,
    candidates: List<DrugIdentity>,
    onSelect: (DrugIdentity) -> Unit,
    onManualSearch: () -> Unit,
    onNoMatch: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(candidates) { drug ->
                    DrugCandidateCard(identity = drug, onClick = { onSelect(drug) })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onNoMatch) {
                Text(stringResource(R.string.audit_no_match))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onManualSearch) {
                    Text(stringResource(R.string.audit_manual_search))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.scan_back))
                }
            }
        }
    )
}

@Composable
private fun ManualSearchDialog(
    contextName: String?,
    viewModel: AuditScanViewModel,
    onSelect: (DrugIdentity) -> Unit,
    onDismiss: () -> Unit
) {
    var keyword by remember { mutableStateOf("") }
    var candidates by remember { mutableStateOf<List<DrugIdentity>>(emptyList()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.audit_manual_search)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = keyword,
                    onValueChange = { value ->
                        keyword = value
                        viewModel.searchCandidates(value, contextName) { result ->
                            candidates = result
                        }
                    },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.audit_search_hint)) },
                    modifier = Modifier.fillMaxWidth()
                )
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(candidates) { drug ->
                        DrugCandidateCard(identity = drug, onClick = { onSelect(drug) })
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.scan_back))
            }
        }
    )
}

@Composable
private fun DrugCandidateCard(
    identity: DrugIdentity,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(identity.displayName, fontWeight = FontWeight.Bold)
            identity.sourceName?.let { source ->
                Text(
                    text = source,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = identity.packageSpec ?: "-",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun MatchResult.displayName(): String {
    return confirmedIdentity()?.displayName ?: ocrName
}

private fun MatchResult.confirmedIdentity(): DrugIdentity? {
    return candidates.singleOrNull().takeIf { status == MatchStatus.CONFIRMED }
}
