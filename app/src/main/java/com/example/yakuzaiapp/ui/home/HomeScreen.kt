package com.example.yakuzaiapp.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yakuzaiapp.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenDrugSearch: () -> Unit,
    onOpenMedisImport: () -> Unit,
    onOpenFillMode: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDispensing: () -> Unit,
    onOpenAudit: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "薬品照合アプリ",
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "メニュー",
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("薬品検索") },
                                onClick = {
                                    menuExpanded = false
                                    onOpenDrugSearch()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("MEDISマスター取込") },
                                onClick = {
                                    menuExpanded = false
                                    onOpenMedisImport()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("充填モード") },
                                onClick = {
                                    menuExpanded = false
                                    onOpenFillMode()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("設定") },
                                onClick = {
                                    menuExpanded = false
                                    onOpenSettings()
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        HomeContent(
            paddingValues = padding,
            onDispenseModeClick = onOpenDispensing,
            onAuditModeClick = onOpenAudit,
            onFillModeClick = onOpenFillMode,
        )
    }
}

@Composable
private fun HomeContent(
    paddingValues: PaddingValues,
    onDispenseModeClick: () -> Unit,
    onAuditModeClick: () -> Unit,
    onFillModeClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ModeButton(
            title = stringResource(R.string.dispensing_mode_button),
            subtitle = stringResource(R.string.dispensing_mode_subtitle),
            icon = Icons.Outlined.Medication,
            containerColor = DispenseModeColor,
            onClick = onDispenseModeClick,
        )
        Spacer(modifier = Modifier.height(24.dp))
        ModeButton(
            title = stringResource(R.string.audit_mode_button),
            subtitle = stringResource(R.string.audit_mode_subtitle),
            icon = Icons.Outlined.DocumentScanner,
            containerColor = AuditModeColor,
            onClick = onAuditModeClick,
        )
        Spacer(modifier = Modifier.height(24.dp))
        ModeButton(
            title = stringResource(R.string.fill_mode_button),
            subtitle = stringResource(R.string.fill_mode_scan_drug_hint),
            icon = Icons.Outlined.CameraAlt,
            containerColor = ReadModeColor,
            onClick = onFillModeClick,
        )
    }
}

@Composable
private fun ModeButton(
    title: String,
    subtitle: String,
    icon: ImageVector,
    containerColor: Color,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = Color.White,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        onClick = onClick,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Column {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = Color.White,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = title, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = subtitle,
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
        }
    }
}

private val DispenseModeColor = Color(0xFF1976D2)
private val AuditModeColor = Color(0xFF6A1B9A)
private val ReadModeColor = Color(0xFF388E3C)
