package com.example.yakuzaiapp.ui.navigation

import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.yakuzaiapp.YakuzaiApplication
import com.example.yakuzaiapp.data.medis.MedisAutoUpdateState
import com.example.yakuzaiapp.domain.dispensing.ScanMatchResult
import com.example.yakuzaiapp.domain.scan.ScanMode
import com.example.yakuzaiapp.ui.audit.AuditResultScreen
import com.example.yakuzaiapp.ui.audit.AuditPtpScanScreen
import com.example.yakuzaiapp.ui.audit.AuditScanScreen
import com.example.yakuzaiapp.ui.audit.AuditScanViewModel
import com.example.yakuzaiapp.ui.checklist.ChecklistScreen
import com.example.yakuzaiapp.ui.common.PlaceholderScreen
import com.example.yakuzaiapp.ui.dispensing.DispensingCompleteScreen
import com.example.yakuzaiapp.ui.dispensing.DispensingPtpScanScreen
import com.example.yakuzaiapp.ui.dispensing.DispensingScreen
import com.example.yakuzaiapp.ui.dispensing.DispensingViewModel
import com.example.yakuzaiapp.ui.facility.FacilityRegistrationScreen
import com.example.yakuzaiapp.ui.fill.FillHistoryScreen
import com.example.yakuzaiapp.ui.home.HomeBottomTab
import com.example.yakuzaiapp.ui.home.HomeScreen
import com.example.yakuzaiapp.ui.fill.FillModeScreen
import com.example.yakuzaiapp.ui.medis.MedisImportScreen
import com.example.yakuzaiapp.ui.result.ResultScreen
import com.example.yakuzaiapp.ui.scan.ScanScreen
import com.example.yakuzaiapp.ui.search.DrugDetailScreen
import com.example.yakuzaiapp.ui.search.DrugSearchScreen
import com.example.yakuzaiapp.ui.staff.UserRegistrationScreen
import com.example.yakuzaiapp.ui.staff.UserSelectionScreen
import com.example.yakuzaiapp.util.SoundFeedback
import com.example.yakuzaiapp.util.VibrationFeedback
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "AppNavigation"
private const val CAMERA_RELEASE_NAVIGATION_DELAY_MS = 250L

object Routes {
    const val HOME = "home"
    const val DRUG_SEARCH = "drug_search"
    const val DRUG_DETAIL = "drug_detail"
    const val MEDIS_IMPORT = "medis_import"
    const val DISPENSING = "dispensing"
    const val DISPENSING_PTP_SCAN = "dispensing_ptp_scan"
    const val DISPENSING_COMPLETE = "dispensing_complete"
    const val AUDIT_SCAN = "audit_scan"
    const val AUDIT_RESULT = "audit_result"
    const val AUDIT_PTP_SCAN = "audit_ptp_scan"
    const val AUDIT_PTP_COMPLETE = "audit_ptp_complete"
    const val FILL_MODE = "fill_mode"
    const val FILL_LOG = "fill_log"
    const val SCANNER = "scanner/{mode}/{continuous}"
    const val FACILITY_REGISTRATION = "facility_registration"
    const val USER_REGISTRATION = "user_registration"
    const val USER_SELECTION = "user_selection"
    const val SETTINGS = "settings"
    const val CHECKLIST = "checklist"
    const val RESULT = "result"
    const val RESULT_WITH_GTIN = "result/{gtin}"

    fun resultWithGtin(gtin: String) = "result/$gtin"
    fun scanner(mode: ScanMode, continuous: Boolean = false) = "scanner/${mode.name}/$continuous"
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val application = context.applicationContext as YakuzaiApplication
    val medisAutoUpdateCoordinator = application.medisAutoUpdateCoordinator
    val autoUpdateState by medisAutoUpdateCoordinator.state.collectAsState()
    val staffList by application.database.staffMasterDao().observeAll().collectAsState(initial = emptyList())
    val selectedStaffId by application.staffSelectionRepository.selectedStaffId.collectAsState()
    val selectedStaffName = staffList.firstOrNull { it.staffId == selectedStaffId }?.greetingName()
    val lifecycleOwner = LocalLifecycleOwner.current
    val navigationScope = rememberCoroutineScope()
    val dispensingViewModel: DispensingViewModel = viewModel(factory = DispensingViewModel.Factory)
    val auditScanViewModel: AuditScanViewModel = viewModel(factory = AuditScanViewModel.Factory)
    fun navigateToMedisUpdate() {
        medisAutoUpdateCoordinator.maybeStartAutoUpdate(force = true)
        navController.navigate(Routes.MEDIS_IMPORT) {
            popUpTo(Routes.HOME) { inclusive = false }
            launchSingleTop = true
        }
    }
    fun updateMedisOnHome() {
        medisAutoUpdateCoordinator.maybeStartAutoUpdate(force = true)
        navController.navigate(Routes.HOME) {
            popUpTo(Routes.HOME) { inclusive = false }
            launchSingleTop = true
        }
    }
    fun navigateToUserSelection() {
        navController.navigate(Routes.USER_SELECTION) {
            launchSingleTop = true
        }
    }
    fun navigateToAuditAfterCameraRelease(beforeNavigate: () -> Unit = {}) {
        beforeNavigate()
        navController.navigate(Routes.HOME) {
            popUpTo(Routes.HOME) { inclusive = false }
            launchSingleTop = true
        }
        navigationScope.launch {
            delay(CAMERA_RELEASE_NAVIGATION_DELAY_MS)
            navController.navigate(Routes.AUDIT_SCAN) {
                launchSingleTop = true
            }
        }
    }

    DisposableEffect(lifecycleOwner, medisAutoUpdateCoordinator) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                medisAutoUpdateCoordinator.maybeStartAutoUpdate(force = false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    BackHandler(enabled = autoUpdateState is MedisAutoUpdateState.Running) {
        // Data updates must finish before the app can be used again.
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                selectedStaffName = selectedStaffName,
                onOpenDrugSearch = { navController.navigate(Routes.DRUG_SEARCH) },
                onOpenMedisImport = { updateMedisOnHome() },
                onOpenFacilityRegistration = { navController.navigate(Routes.FACILITY_REGISTRATION) },
                onOpenUserRegistration = { navController.navigate(Routes.USER_REGISTRATION) },
                onOpenFillHistory = { navController.navigate(Routes.FILL_LOG) },
                onOpenUserSelection = { navigateToUserSelection() },
                onOpenFillMode = {
                    Log.d(TAG, "home -> fill_mode")
                    navController.navigate(Routes.FILL_MODE)
                },
                onOpenDispensing = {
                    Log.d(TAG, "home -> scanner JAHIS_QR")
                    dispensingViewModel.clearSession()
                    navController.navigate(Routes.scanner(ScanMode.JAHIS_QR))
                },
                onOpenAudit = {
                    Log.d(TAG, "home -> audit_scan")
                    navController.navigate(Routes.AUDIT_SCAN)
                },
            )
        }
        composable(Routes.DRUG_SEARCH) {
            DrugSearchScreen(
                onBack = { navController.popBackStack() },
                onDrugClick = { gtin ->
                    navController.navigate("${Routes.DRUG_DETAIL}/$gtin")
                },
            )
        }
        composable(
            route = "${Routes.DRUG_DETAIL}/{gtin}",
            arguments = listOf(navArgument("gtin") { type = NavType.StringType }),
        ) { backStackEntry ->
            val gtin = backStackEntry.arguments?.getString("gtin") ?: return@composable
            DrugDetailScreen(
                gtin = gtin,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.MEDIS_IMPORT) {
            MedisImportScreen(
                onBack = { navController.popBackStack() },
                autoUpdateState = autoUpdateState,
                onManualUpdate = {
                    medisAutoUpdateCoordinator.maybeStartAutoUpdate(force = true)
                },
                onDismissAutoUpdateMessage = {
                    medisAutoUpdateCoordinator.clearTransientState()
                },
            )
        }
        composable(Routes.FACILITY_REGISTRATION) {
            FacilityRegistrationScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.USER_REGISTRATION) {
            UserRegistrationScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.USER_SELECTION) {
            UserSelectionScreen(
                onBack = { navController.popBackStack() },
                onSelected = { navController.popBackStack() },
                onHomeClick = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = false }
                        launchSingleTop = true
                    }
                },
                onAuditClick = {
                    navController.navigate(Routes.AUDIT_SCAN) {
                        popUpTo(Routes.HOME) { inclusive = false }
                        launchSingleTop = true
                    }
                },
                onReportClick = {
                    dispensingViewModel.clearSession()
                    navController.navigate(Routes.scanner(ScanMode.JAHIS_QR)) {
                        popUpTo(Routes.HOME) { inclusive = false }
                        launchSingleTop = true
                    }
                },
                onFillClick = {
                    navController.navigate(Routes.FILL_MODE) {
                        popUpTo(Routes.HOME) { inclusive = false }
                        launchSingleTop = true
                    }
                },
                onDataUpdateClick = {},
            )
        }
        composable(Routes.AUDIT_SCAN) {
            AuditScanScreen(
                viewModel = auditScanViewModel,
                onBack = { navController.popBackStack() },
                onHomeClick = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = false }
                        launchSingleTop = true
                    }
                },
                onAuditClick = {},
                onReportClick = {
                    dispensingViewModel.clearSession()
                    navController.navigate(Routes.scanner(ScanMode.JAHIS_QR)) {
                        popUpTo(Routes.HOME) { inclusive = false }
                        launchSingleTop = true
                    }
                },
                onFillClick = {
                    navController.navigate(Routes.FILL_MODE) {
                        popUpTo(Routes.HOME) { inclusive = false }
                        launchSingleTop = true
                    }
                },
                onDataUpdateClick = {
                    navigateToUserSelection()
                },
                onOcrCompleted = {
                    Log.d(TAG, "audit_scan -> audit_result")
                    navController.navigate(Routes.AUDIT_RESULT)
                }
            )
        }
        composable(Routes.AUDIT_RESULT) {
            AuditResultScreen(
                viewModel = auditScanViewModel,
                onBack = { navController.popBackStack() },
                onRetake = {
                    auditScanViewModel.clearResult()
                    navController.popBackStack(Routes.AUDIT_SCAN, inclusive = false)
                },
                onProceedPtp = {
                    Log.d(TAG, "audit_result -> audit_ptp_scan")
                    navController.navigate(Routes.AUDIT_PTP_SCAN)
                },
                onHomeClick = {
                    auditScanViewModel.clearResult()
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = false }
                        launchSingleTop = true
                    }
                },
                onAuditClick = {},
                onReportClick = {
                    auditScanViewModel.clearResult()
                    dispensingViewModel.clearSession()
                    navController.navigate(Routes.scanner(ScanMode.JAHIS_QR)) {
                        popUpTo(Routes.HOME) { inclusive = false }
                        launchSingleTop = true
                    }
                },
                onFillClick = {
                    auditScanViewModel.clearResult()
                    navController.navigate(Routes.FILL_MODE) {
                        popUpTo(Routes.HOME) { inclusive = false }
                        launchSingleTop = true
                    }
                },
                onDataUpdateClick = {
                    navigateToUserSelection()
                }
            )
        }
        composable(Routes.AUDIT_PTP_SCAN) {
            AuditPtpScanScreen(
                auditViewModel = auditScanViewModel,
                onBack = { navController.popBackStack() },
                onComplete = {
                    Log.d(TAG, "audit_ptp_scan -> home")
                    auditScanViewModel.clearResult()
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = false }
                        launchSingleTop = true
                    }
                },
                onCancel = {
                    Log.d(TAG, "audit_ptp_scan canceled -> home")
                    auditScanViewModel.clearResult()
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = false }
                        launchSingleTop = true
                    }
                },
                onHomeClick = {
                    auditScanViewModel.clearResult()
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = false }
                        launchSingleTop = true
                    }
                },
                onAuditClick = {},
                onReportClick = {
                    auditScanViewModel.clearResult()
                    dispensingViewModel.clearSession()
                    navController.navigate(Routes.scanner(ScanMode.JAHIS_QR)) {
                        popUpTo(Routes.HOME) { inclusive = false }
                        launchSingleTop = true
                    }
                },
                onFillClick = {
                    auditScanViewModel.clearResult()
                    navController.navigate(Routes.FILL_MODE) {
                        popUpTo(Routes.HOME) { inclusive = false }
                        launchSingleTop = true
                    }
                },
                onDataUpdateClick = {
                    navigateToUserSelection()
                }
            )
        }
        composable(Routes.AUDIT_PTP_COMPLETE) {
            PlaceholderScreen(
                title = "監査完了",
                message = "ここに監査完了処理を実装する。",
                onBack = { navController.popBackStack(Routes.HOME, inclusive = false) },
            )
        }
        composable(Routes.DISPENSING_COMPLETE) {
            DispensingCompleteScreen(
                viewModel = dispensingViewModel,
                onDone = {
                    Log.d(TAG, "dispensing_complete -> home")
                    dispensingViewModel.clearSession()
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = false }
                        launchSingleTop = true
                    }
                },
                onBack = {
                    Log.d(TAG, "dispensing_complete back -> home")
                    dispensingViewModel.clearScanFeedback()
                    dispensingViewModel.clearSession()
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = false }
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(Routes.FILL_MODE) {
            FillModeScreen(
                onBack = { navController.popBackStack() },
                onHomeClick = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = false }
                        launchSingleTop = true
                    }
                },
                onAuditClick = {
                    navigateToAuditAfterCameraRelease()
                },
                onReportClick = {
                    dispensingViewModel.clearSession()
                    navController.navigate(Routes.scanner(ScanMode.JAHIS_QR)) {
                        popUpTo(Routes.HOME) { inclusive = false }
                        launchSingleTop = true
                    }
                },
                onFillClick = {},
                onDataUpdateClick = {
                    navigateToUserSelection()
                }
            )
        }
        composable(Routes.FILL_LOG) {
            FillHistoryScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.DISPENSING) {
            DispensingScreen(
                viewModel = dispensingViewModel,
                onStartScan = {
                    Log.d(TAG, "dispensing -> scanner JAHIS_QR (start scan)")
                    dispensingViewModel.clearSession()
                    navController.navigate(Routes.scanner(ScanMode.JAHIS_QR)) {
                        popUpTo(Routes.HOME) { inclusive = false }
                        launchSingleTop = true
                    }
                },
                onReloadQr = {
                    Log.d(TAG, "dispensing -> scanner JAHIS_QR (reload)")
                    dispensingViewModel.clearSession()
                    navController.navigate(Routes.scanner(ScanMode.JAHIS_QR)) {
                        popUpTo(Routes.HOME) { inclusive = false }
                        launchSingleTop = true
                    }
                },
                onStartPtpScan = {
                    Log.d(TAG, "dispensing -> dispensing_ptp_scan")
                    navController.navigate(Routes.DISPENSING_PTP_SCAN)
                },
                onCompleted = {
                    Log.d(TAG, "dispensing -> dispensing_complete")
                    navController.navigate(Routes.DISPENSING_COMPLETE)
                },
                onHomeClick = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = false }
                        launchSingleTop = true
                    }
                },
                onAuditClick = {
                    navigateToAuditAfterCameraRelease()
                },
                onReportClick = {},
                onFillClick = {
                    navController.navigate(Routes.FILL_MODE) {
                        popUpTo(Routes.HOME) { inclusive = false }
                        launchSingleTop = true
                    }
                },
                onDataUpdateClick = {
                    navigateToUserSelection()
                },
            )
        }
        composable(Routes.DISPENSING_PTP_SCAN) {
            DispensingPtpScanScreen(
                viewModel = dispensingViewModel,
                onBack = {
                    dispensingViewModel.clearScanFeedback()
                    navController.popBackStack()
                },
                onCompleted = {
                    Log.d(TAG, "dispensing_ptp_scan -> dispensing_complete")
                    dispensingViewModel.clearScanFeedback()
                    navController.navigate(Routes.DISPENSING_COMPLETE) {
                        popUpTo(Routes.DISPENSING_PTP_SCAN) { inclusive = true }
                    }
                },
                onHomeClick = {
                    dispensingViewModel.clearSession()
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = false }
                        launchSingleTop = true
                    }
                },
                onAuditClick = {
                    dispensingViewModel.clearSession()
                    navigateToAuditAfterCameraRelease()
                },
                onReportClick = {},
                onFillClick = {
                    dispensingViewModel.clearSession()
                    navController.navigate(Routes.FILL_MODE) {
                        popUpTo(Routes.HOME) { inclusive = false }
                        launchSingleTop = true
                    }
                },
                onDataUpdateClick = {
                    navigateToUserSelection()
                }
            )
        }
        composable(
            route = Routes.SCANNER,
            arguments = listOf(
                navArgument("mode") { type = NavType.StringType },
                navArgument("continuous") {
                    type = NavType.BoolType
                    defaultValue = false
                }
            )
        ) { backStackEntry ->
            val mode = ScanMode.fromRouteValue(backStackEntry.arguments?.getString("mode"))
            val continuousMode = backStackEntry.arguments?.getBoolean("continuous") ?: false
            Log.d(TAG, "scanner route entered mode=$mode continuous=$continuousMode")

            if (continuousMode && mode == ScanMode.PTP_GTIN) {
                LaunchedEffect(dispensingViewModel) {
                    dispensingViewModel.clearScanFeedback()
                }
                LaunchedEffect(dispensingViewModel) {
                    dispensingViewModel.scanFeedback.collect { result ->
                        Log.d(TAG, "scanFeedback=$result")
                        Toast.makeText(context, feedbackMessage(result), Toast.LENGTH_SHORT).show()
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
                LaunchedEffect(dispensingViewModel) {
                    dispensingViewModel.ptpAllCheckedEvent.collect {
                        Log.d(TAG, "scanner -> dispensing_complete after all PTP checked")
                        navController.navigate(Routes.DISPENSING_COMPLETE) {
                            popUpTo(Routes.HOME) { inclusive = false }
                            launchSingleTop = true
                        }
                    }
                }
            }

            ScanScreen(
                mode = mode,
                continuousMode = continuousMode,
                bottomTab = if (mode == ScanMode.JAHIS_QR && !continuousMode) {
                    HomeBottomTab.REPORT
                } else {
                    null
                },
                onHomeClick = {
                    dispensingViewModel.clearSession()
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = false }
                        launchSingleTop = true
                    }
                },
                onAuditClick = {
                    dispensingViewModel.clearSession()
                    navigateToAuditAfterCameraRelease()
                },
                onReportClick = {},
                onFillClick = {
                    dispensingViewModel.clearSession()
                    navController.navigate(Routes.FILL_MODE) {
                        popUpTo(Routes.HOME) { inclusive = false }
                        launchSingleTop = true
                    }
                },
                onDataUpdateClick = {
                    navigateToUserSelection()
                },
                onBack = {
                    Log.d(TAG, "scanner -> back")
                    navController.popBackStack()
                },
                onResult = { rawText ->
                    Log.d(TAG, "scanner onResult mode=$mode continuous=$continuousMode")
                    when (mode) {
                        ScanMode.PTP_GTIN -> {
                            if (continuousMode) {
                                Log.d(TAG, "scanner -> dispensingViewModel.onPtpScanned()")
                                dispensingViewModel.onPtpScanned(rawText)
                            } else {
                                Log.d(TAG, "scanner -> result route")
                                navController.navigate(Routes.resultWithGtin(rawText))
                            }
                        }
                        ScanMode.JAHIS_QR -> {
                            Log.d(TAG, "scanner -> dispensingViewModel.onQrScanned()")
                            dispensingViewModel.onQrScanned(rawText)
                            Log.d(TAG, "scanner -> dispensing route")
                            navController.navigate(Routes.DISPENSING) {
                                popUpTo(Routes.HOME) { inclusive = false }
                                launchSingleTop = true
                            }
                        }
                    }
                }
            )
        }
        composable(Routes.SETTINGS) {
            PlaceholderScreen(
                title = "設定",
                message = "ここに設定画面を実装する。",
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.CHECKLIST) {
            ChecklistScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.RESULT) {
            ResultScreen(gtin = null, onBack = { navController.popBackStack() })
        }
        composable(
            route = Routes.RESULT_WITH_GTIN,
            arguments = listOf(navArgument("gtin") { type = NavType.StringType })
        ) { backStackEntry ->
            ResultScreen(
                gtin = backStackEntry.arguments?.getString("gtin"),
                onBack = { navController.popBackStack() }
            )
        }
    }
        AutoUpdateOverlay(
            state = autoUpdateState,
            onDismiss = { medisAutoUpdateCoordinator.clearTransientState() },
        )
    }
}

@Composable
private fun AutoUpdateOverlay(
    state: MedisAutoUpdateState,
    onDismiss: () -> Unit,
) {
    when (state) {
        is MedisAutoUpdateState.Running -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "データ更新中",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                        )
                        if (state.totalCount > 0) {
                            val percent = (state.progressFraction * 100).toInt().coerceIn(0, 100)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(32.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                LinearProgressIndicator(
                                    progress = { state.progressFraction },
                                    modifier = Modifier.fillMaxSize(),
                                )
                                Text(
                                    text = "$percent%",
                                    style = MaterialTheme.typography.titleMedium,
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
                        Text(
                            text = "更新が終わるまで操作できません。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        is MedisAutoUpdateState.Error -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "データ更新に失敗しました",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Text(text = state.message)
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("閉じる")
                        }
                    }
                }
            }
        }
        is MedisAutoUpdateState.Completed -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "データ更新完了しました",
                            fontWeight = FontWeight.Bold,
                        )
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("閉じる")
                        }
                    }
                }
            }
        }
        MedisAutoUpdateState.Idle -> Unit
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
