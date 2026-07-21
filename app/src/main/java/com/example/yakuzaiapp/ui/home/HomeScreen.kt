package com.example.yakuzaiapp.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yakuzaiapp.R
import java.time.LocalTime
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    selectedStaffName: String? = null,
    onOpenDrugSearch: () -> Unit,
    onOpenMedisImport: () -> Unit,
    onOpenUserDrugMaster: () -> Unit,
    onOpenFacilityRegistration: () -> Unit,
    onOpenUserRegistration: () -> Unit,
    onOpenFillHistory: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onOpenUserSelection: () -> Unit,
    onOpenFillMode: () -> Unit,
    onOpenDispensing: () -> Unit,
    onOpenAudit: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = greetingText(selectedStaffName),
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
                                text = { Text("院内製剤・材料マスター") },
                                onClick = {
                                    menuExpanded = false
                                    onOpenUserDrugMaster()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("施設登録") },
                                onClick = {
                                    menuExpanded = false
                                    onOpenFacilityRegistration()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("利用者登録") },
                                onClick = {
                                    menuExpanded = false
                                    onOpenUserRegistration()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("充填ログ確認") },
                                onClick = {
                                    menuExpanded = false
                                    onOpenFillHistory()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("帳票監査モード設定") },
                                onClick = {
                                    menuExpanded = false
                                    onOpenSettings()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("プライバシーポリシー") },
                                onClick = {
                                    menuExpanded = false
                                    onOpenPrivacyPolicy()
                                },
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            HomeBottomTabBar(
                selectedTab = HomeBottomTab.HOME,
                onHomeClick = {},
                onAuditClick = onOpenAudit,
                onReportClick = onOpenDispensing,
                onFillClick = onOpenFillMode,
                onDataUpdateClick = onOpenUserSelection,
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

private fun greetingText(staffName: String?, now: LocalTime = LocalTime.now()): String {
    val greeting = when (now.hour) {
        in 5..10 -> "おはようございます"
        in 11..16 -> "お疲れさまです"
        in 17..21 -> "こんばんは"
        else -> "深夜までお疲れさまです"
    }
    return staffName
        ?.takeIf { it.isNotBlank() }
        ?.let { "${it}さん、$greeting" }
        ?: greeting
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
            iconResId = R.drawable.icon_dispensing_mode,
            containerColor = DispenseModeColor,
            onClick = onDispenseModeClick,
        )
        Spacer(modifier = Modifier.height(24.dp))
        ModeButton(
            title = stringResource(R.string.audit_mode_button),
            subtitle = stringResource(R.string.audit_mode_subtitle),
            iconResId = R.drawable.icon_audit_mode,
            containerColor = AuditModeColor,
            onClick = onAuditModeClick,
        )
        Spacer(modifier = Modifier.height(24.dp))
        ModeButton(
            title = stringResource(R.string.fill_mode_button),
            subtitle = stringResource(R.string.fill_mode_scan_drug_hint),
            iconResId = R.drawable.icon_fill_mode,
            containerColor = ReadModeColor,
            onClick = onFillModeClick,
        )
    }
}

@Composable
private fun ModeButton(
    title: String,
    subtitle: String,
    iconResId: Int,
    containerColor: Color,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp),
        shape = RoundedCornerShape(16.dp),
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .background(
                            color = Color.White.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(18.dp),
                        )
                        .padding(2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(id = iconResId),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                }
                Spacer(modifier = Modifier.width(18.dp))
                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = title, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = subtitle,
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DispensingModeIcon(color: Color) {
    TabIconCanvas(iconSize = 58.dp) { point, stroke ->
        val bottle = Path().apply {
            moveTo(point(4.5f, 8f).x, point(4.5f, 8f).y)
            cubicTo(point(4.5f, 6f).x, point(4.5f, 6f).y, point(6.5f, 5f).x, point(6.5f, 5f).y, point(9f, 5f).x, point(9f, 5f).y)
            cubicTo(point(11.5f, 5f).x, point(11.5f, 5f).y, point(13.5f, 6f).x, point(13.5f, 6f).y, point(13.5f, 8f).x, point(13.5f, 8f).y)
            lineTo(point(13.5f, 19f).x, point(13.5f, 19f).y)
            cubicTo(point(13.5f, 21f).x, point(13.5f, 21f).y, point(12f, 22f).x, point(12f, 22f).y, point(9f, 22f).x, point(9f, 22f).y)
            cubicTo(point(6f, 22f).x, point(6f, 22f).y, point(4.5f, 21f).x, point(4.5f, 21f).y, point(4.5f, 19f).x, point(4.5f, 19f).y)
            close()
        }
        drawPath(path = bottle, color = color, style = stroke)
        drawLine(color, point(5f, 9f), point(13f, 9f), stroke.width, StrokeCap.Round)
        drawLine(color, point(6f, 14f), point(12f, 14f), stroke.width, StrokeCap.Round)
        drawLine(color, point(9f, 11f), point(9f, 17f), stroke.width, StrokeCap.Round)
        drawLine(color, point(6f, 20f), point(12f, 20f), stroke.width, StrokeCap.Round)

        val tray = Path().apply {
            moveTo(point(13f, 8f).x, point(13f, 8f).y)
            lineTo(point(21f, 9.5f).x, point(21f, 9.5f).y)
            lineTo(point(19.5f, 21f).x, point(19.5f, 21f).y)
            lineTo(point(12f, 19.5f).x, point(12f, 19.5f).y)
            close()
        }
        drawPath(path = tray, color = color, style = stroke)
        drawLine(color, point(15f, 11f), point(19f, 11.8f), stroke.width, StrokeCap.Round)
        drawLine(color, point(14f, 15f), point(19.5f, 16f), stroke.width, StrokeCap.Round)
        drawCircle(
            color = color,
            radius = 1.8f * (size.minDimension / 24f),
            center = point(15f, 18.5f),
            style = stroke,
        )
        drawCircle(
            color = color,
            radius = 1.7f * (size.minDimension / 24f),
            center = point(19f, 18.5f),
            style = stroke,
        )

        drawLine(color, point(3f, 22f), point(7f, 20.5f), stroke.width, StrokeCap.Round)
        drawLine(color, point(2.5f, 20.5f), point(5.5f, 22.5f), stroke.width, StrokeCap.Round)
    }
}

@Composable
private fun AuditModeIcon(color: Color) {
    TabIconCanvas(iconSize = 58.dp) { point, stroke ->
        val board = Path().apply {
            moveTo(point(4f, 5.5f).x, point(4f, 5.5f).y)
            lineTo(point(16f, 5.5f).x, point(16f, 5.5f).y)
            lineTo(point(16f, 21f).x, point(16f, 21f).y)
            lineTo(point(4f, 21f).x, point(4f, 21f).y)
            close()
        }
        drawPath(path = board, color = color, style = stroke)
        val clip = Path().apply {
            moveTo(point(8f, 5.5f).x, point(8f, 5.5f).y)
            quadraticBezierTo(point(8f, 3f).x, point(8f, 3f).y, point(10f, 3f).x, point(10f, 3f).y)
            quadraticBezierTo(point(12f, 3f).x, point(12f, 3f).y, point(12f, 5.5f).x, point(12f, 5.5f).y)
            lineTo(point(14f, 5.5f).x, point(14f, 5.5f).y)
            lineTo(point(14f, 8f).x, point(14f, 8f).y)
            lineTo(point(6f, 8f).x, point(6f, 8f).y)
            lineTo(point(6f, 5.5f).x, point(6f, 5.5f).y)
            close()
        }
        drawPath(path = clip, color = color, style = stroke)

        drawLine(color, point(6.5f, 11f), point(13.5f, 11f), stroke.width, StrokeCap.Round)
        drawLine(color, point(6.5f, 14f), point(13.5f, 14f), stroke.width, StrokeCap.Round)
        drawLine(color, point(6.5f, 17f), point(12f, 17f), stroke.width, StrokeCap.Round)
        val check = Path().apply {
            moveTo(point(6.2f, 13.6f).x, point(6.2f, 13.6f).y)
            lineTo(point(7.6f, 15f).x, point(7.6f, 15f).y)
            lineTo(point(10f, 12f).x, point(10f, 12f).y)
        }
        drawPath(path = check, color = color, style = stroke)

        drawCircle(
            color = color,
            radius = 4.2f * (size.minDimension / 24f),
            center = point(16.5f, 15f),
            style = stroke,
        )
        drawLine(color, point(19.5f, 18f), point(22.5f, 21f), stroke.width, StrokeCap.Round)
    }
}

@Composable
private fun FillModeIcon(color: Color) {
    TabIconCanvas(iconSize = 58.dp) { point, stroke ->
        val phone = Path().apply {
            moveTo(point(3.5f, 3.5f).x, point(3.5f, 3.5f).y)
            lineTo(point(11f, 3.5f).x, point(11f, 3.5f).y)
            quadraticBezierTo(point(12.5f, 3.5f).x, point(12.5f, 3.5f).y, point(12.5f, 5f).x, point(12.5f, 5f).y)
            lineTo(point(12.5f, 14f).x, point(12.5f, 14f).y)
            quadraticBezierTo(point(12.5f, 15.5f).x, point(12.5f, 15.5f).y, point(11f, 15.5f).x, point(11f, 15.5f).y)
            lineTo(point(3.5f, 15.5f).x, point(3.5f, 15.5f).y)
            quadraticBezierTo(point(2f, 15.5f).x, point(2f, 15.5f).y, point(2f, 14f).x, point(2f, 14f).y)
            lineTo(point(2f, 5f).x, point(2f, 5f).y)
            quadraticBezierTo(point(2f, 3.5f).x, point(2f, 3.5f).y, point(3.5f, 3.5f).x, point(3.5f, 3.5f).y)
            close()
        }
        drawPath(path = phone, color = color, style = stroke)
        drawLine(color, point(5.5f, 5.2f), point(9f, 5.2f), stroke.width, StrokeCap.Round)
        drawLine(color, point(4.5f, 8f), point(6.5f, 8f), stroke.width, StrokeCap.Round)
        drawLine(color, point(4.5f, 8f), point(4.5f, 10f), stroke.width, StrokeCap.Round)
        drawLine(color, point(10f, 8f), point(8f, 8f), stroke.width, StrokeCap.Round)
        drawLine(color, point(10f, 8f), point(10f, 10f), stroke.width, StrokeCap.Round)
        drawLine(color, point(4.5f, 13f), point(6.5f, 13f), stroke.width, StrokeCap.Round)
        drawLine(color, point(4.5f, 13f), point(4.5f, 11f), stroke.width, StrokeCap.Round)
        drawLine(color, point(10f, 13f), point(8f, 13f), stroke.width, StrokeCap.Round)
        drawLine(color, point(10f, 13f), point(10f, 11f), stroke.width, StrokeCap.Round)

        val arrow = Path().apply {
            moveTo(point(12.8f, 10.2f).x, point(12.8f, 10.2f).y)
            lineTo(point(16f, 10.2f).x, point(16f, 10.2f).y)
            lineTo(point(16f, 8.4f).x, point(16f, 8.4f).y)
            lineTo(point(20f, 12f).x, point(20f, 12f).y)
            lineTo(point(16f, 15.6f).x, point(16f, 15.6f).y)
            lineTo(point(16f, 13.8f).x, point(16f, 13.8f).y)
            lineTo(point(12.8f, 13.8f).x, point(12.8f, 13.8f).y)
        }
        drawPath(path = arrow, color = color, style = stroke)

        drawLine(color, point(17.5f, 4f), point(22f, 4f), stroke.width, StrokeCap.Round)
        val bottle = Path().apply {
            moveTo(point(18f, 6f).x, point(18f, 6f).y)
            lineTo(point(21.5f, 6f).x, point(21.5f, 6f).y)
            lineTo(point(21.5f, 9f).x, point(21.5f, 9f).y)
            quadraticBezierTo(point(23f, 10.5f).x, point(23f, 10.5f).y, point(23f, 13.5f).x, point(23f, 13.5f).y)
            lineTo(point(23f, 20f).x, point(23f, 20f).y)
            quadraticBezierTo(point(23f, 22f).x, point(23f, 22f).y, point(21f, 22f).x, point(21f, 22f).y)
            lineTo(point(18.5f, 22f).x, point(18.5f, 22f).y)
            quadraticBezierTo(point(16.5f, 22f).x, point(16.5f, 22f).y, point(16.5f, 20f).x, point(16.5f, 20f).y)
            lineTo(point(16.5f, 13.5f).x, point(16.5f, 13.5f).y)
            quadraticBezierTo(point(16.5f, 10.5f).x, point(16.5f, 10.5f).y, point(18f, 9f).x, point(18f, 9f).y)
            close()
        }
        drawPath(path = bottle, color = color, style = stroke)
        drawLine(color, point(17.2f, 12.5f), point(22.3f, 12.5f), stroke.width, StrokeCap.Round)
        drawLine(color, point(18.2f, 18.8f), point(21.2f, 18.8f), stroke.width, StrokeCap.Round)
        drawLine(color, point(19.7f, 16.2f), point(19.7f, 19.2f), stroke.width, StrokeCap.Round)
    }
}

enum class HomeBottomTab {
    HOME,
    AUDIT,
    REPORT,
    FILL,
    USER_SELECT
}

@Composable
fun HomeBottomTabBar(
    selectedTab: HomeBottomTab,
    onHomeClick: () -> Unit,
    onAuditClick: () -> Unit,
    onReportClick: () -> Unit,
    onFillClick: () -> Unit,
    onDataUpdateClick: () -> Unit,
) {
    Surface(
        color = Color.White,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BottomTabButton(
                label = "ホーム",
                selected = selectedTab == HomeBottomTab.HOME,
                onClick = onHomeClick,
                icon = { HomeTabIcon(it) },
                modifier = Modifier.weight(1f),
            )
            BottomTabButton(
                label = "調剤モード",
                selected = selectedTab == HomeBottomTab.REPORT,
                onClick = onReportClick,
                icon = { AuditTabIcon(it) },
                modifier = Modifier.weight(1f),
            )
            BottomTabButton(
                label = "帳票モード",
                selected = selectedTab == HomeBottomTab.AUDIT,
                onClick = onAuditClick,
                icon = { ReportTabIcon(it) },
                modifier = Modifier.weight(1f),
            )
            BottomTabButton(
                label = "充填モード",
                selected = selectedTab == HomeBottomTab.FILL,
                onClick = onFillClick,
                icon = { FillTabIcon(it) },
                modifier = Modifier.weight(1f),
            )
            BottomTabButton(
                label = "利用者選択",
                selected = selectedTab == HomeBottomTab.USER_SELECT,
                onClick = onDataUpdateClick,
                icon = { UserSelectTabIcon(it) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun BottomTabButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable (Color) -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor = if (selected) BottomTabPrimary else Color.White
    val contentColor = if (selected) Color.White else BottomTabPrimary

    Surface(
        modifier = modifier
            .height(78.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = containerColor,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 7.dp, bottom = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            icon(contentColor)
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = label,
                color = contentColor,
                fontSize = 10.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun HomeTabIcon(color: Color) {
    TabIconCanvas { point, stroke ->
        val path = Path().apply {
            moveTo(point(4f, 12f).x, point(4f, 12f).y)
            lineTo(point(12f, 5f).x, point(12f, 5f).y)
            lineTo(point(20f, 12f).x, point(20f, 12f).y)
            lineTo(point(18f, 12f).x, point(18f, 12f).y)
            lineTo(point(18f, 20f).x, point(18f, 20f).y)
            lineTo(point(14f, 20f).x, point(14f, 20f).y)
            lineTo(point(14f, 16f).x, point(14f, 16f).y)
            lineTo(point(10f, 16f).x, point(10f, 16f).y)
            lineTo(point(10f, 20f).x, point(10f, 20f).y)
            lineTo(point(6f, 20f).x, point(6f, 20f).y)
            lineTo(point(6f, 12f).x, point(6f, 12f).y)
            close()
        }
        drawPath(path = path, color = color, style = stroke)
    }
}

@Composable
private fun AuditTabIcon(color: Color) {
    TabIconCanvas { point, stroke ->
        val board = Path().apply {
            moveTo(point(7f, 6f).x, point(7f, 6f).y)
            lineTo(point(17f, 6f).x, point(17f, 6f).y)
            lineTo(point(17f, 20f).x, point(17f, 20f).y)
            lineTo(point(7f, 20f).x, point(7f, 20f).y)
            close()
        }
        drawPath(path = board, color = color, style = stroke)
        drawLine(color, point(9f, 4f), point(15f, 4f), stroke.width, StrokeCap.Round)
        val check = Path().apply {
            moveTo(point(8.5f, 13f).x, point(8.5f, 13f).y)
            lineTo(point(11f, 15.5f).x, point(11f, 15.5f).y)
            lineTo(point(15.5f, 10f).x, point(15.5f, 10f).y)
        }
        drawPath(path = check, color = color, style = stroke)
    }
}

@Composable
private fun ReportTabIcon(color: Color) {
    TabIconCanvas { point, stroke ->
        val page = Path().apply {
            moveTo(point(7f, 4f).x, point(7f, 4f).y)
            lineTo(point(15f, 4f).x, point(15f, 4f).y)
            lineTo(point(19f, 8f).x, point(19f, 8f).y)
            lineTo(point(19f, 20f).x, point(19f, 20f).y)
            lineTo(point(7f, 20f).x, point(7f, 20f).y)
            close()
        }
        drawPath(path = page, color = color, style = stroke)
        drawLine(color, point(15f, 4f), point(15f, 8f), stroke.width, StrokeCap.Round)
        drawLine(color, point(15f, 8f), point(19f, 8f), stroke.width, StrokeCap.Round)
        drawLine(color, point(10f, 17f), point(10f, 13f), stroke.width, StrokeCap.Round)
        drawLine(color, point(13f, 17f), point(13f, 10f), stroke.width, StrokeCap.Round)
        drawLine(color, point(16f, 17f), point(16f, 14f), stroke.width, StrokeCap.Round)
    }
}

@Composable
private fun FillTabIcon(color: Color) {
    TabIconCanvas { point, stroke ->
        drawLine(color, point(9f, 4f), point(15f, 4f), stroke.width, StrokeCap.Round)
        val bottle = Path().apply {
            moveTo(point(8f, 6f).x, point(8f, 6f).y)
            lineTo(point(16f, 6f).x, point(16f, 6f).y)
            lineTo(point(16f, 9f).x, point(16f, 9f).y)
            quadraticBezierTo(point(19f, 11f).x, point(19f, 11f).y, point(19f, 15f).x, point(19f, 15f).y)
            lineTo(point(19f, 19f).x, point(19f, 19f).y)
            quadraticBezierTo(point(19f, 21f).x, point(19f, 21f).y, point(17f, 21f).x, point(17f, 21f).y)
            lineTo(point(7f, 21f).x, point(7f, 21f).y)
            quadraticBezierTo(point(5f, 21f).x, point(5f, 21f).y, point(5f, 19f).x, point(5f, 19f).y)
            lineTo(point(5f, 15f).x, point(5f, 15f).y)
            quadraticBezierTo(point(5f, 11f).x, point(5f, 11f).y, point(8f, 9f).x, point(8f, 9f).y)
            close()
        }
        drawPath(path = bottle, color = color, style = stroke)
        drawLine(color, point(7f, 11f), point(17f, 11f), stroke.width, StrokeCap.Round)
        drawLine(color, point(12f, 14f), point(12f, 19f), stroke.width, StrokeCap.Round)
        drawLine(color, point(9.5f, 16.5f), point(14.5f, 16.5f), stroke.width, StrokeCap.Round)
    }
}

@Composable
private fun UserSelectTabIcon(color: Color) {
    TabIconCanvas { point, stroke ->
        drawCircle(
            color = color,
            radius = 3.2f * (size.minDimension / 24f),
            center = point(12f, 8f),
            style = stroke,
        )
        val shoulders = Path().apply {
            moveTo(point(5.5f, 20f).x, point(5.5f, 20f).y)
            cubicTo(point(6.2f, 15.8f).x, point(6.2f, 15.8f).y, point(9f, 13.5f).x, point(9f, 13.5f).y, point(12f, 13.5f).x, point(12f, 13.5f).y)
            cubicTo(point(15f, 13.5f).x, point(15f, 13.5f).y, point(17.8f, 15.8f).x, point(17.8f, 15.8f).y, point(18.5f, 20f).x, point(18.5f, 20f).y)
        }
        drawPath(path = shoulders, color = color, style = stroke)
        val check = Path().apply {
            moveTo(point(15.5f, 7.5f).x, point(15.5f, 7.5f).y)
            lineTo(point(18f, 10f).x, point(18f, 10f).y)
            lineTo(point(22f, 5.5f).x, point(22f, 5.5f).y)
        }
        drawPath(path = check, color = color, style = stroke)
    }
}

@Composable
private fun TabIconCanvas(
    iconSize: Dp = 34.dp,
    drawIcon: androidx.compose.ui.graphics.drawscope.DrawScope.(point: (Float, Float) -> Offset, stroke: Stroke) -> Unit,
) {
    Canvas(modifier = Modifier.size(iconSize)) {
        val scale = min(size.width, size.height) / 24f
        val dx = (size.width - 24f * scale) / 2f
        val dy = (size.height - 24f * scale) / 2f
        val point = { x: Float, y: Float -> Offset(dx + x * scale, dy + y * scale) }
        val stroke = Stroke(
            width = 1.6f * scale,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        drawIcon(point, stroke)
    }
}

private val ModeCardColor = Color(0xFF062A66)
private val DispenseModeColor = ModeCardColor
private val AuditModeColor = ModeCardColor
private val ReadModeColor = ModeCardColor
private val BottomTabPrimary = Color(0xFF145F7C)
