package com.example.yakuzaiapp.ui.navigation

import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
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
import com.example.yakuzaiapp.ui.home.HomeScreen
import com.example.yakuzaiapp.ui.fill.FillModeScreen
import com.example.yakuzaiapp.ui.medis.MedisImportScreen
import com.example.yakuzaiapp.ui.result.ResultScreen
import com.example.yakuzaiapp.ui.scan.ScanScreen
import com.example.yakuzaiapp.ui.search.DrugDetailScreen
import com.example.yakuzaiapp.ui.search.DrugSearchScreen
import com.example.yakuzaiapp.util.SoundFeedback
import com.example.yakuzaiapp.util.VibrationFeedback

private const val TAG = "AppNavigation"

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
    const val SCANNER = "scanner/{mode}/{continuous}"
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
    val dispensingViewModel: DispensingViewModel = viewModel(factory = DispensingViewModel.Factory)
    val auditScanViewModel: AuditScanViewModel = viewModel(factory = AuditScanViewModel.Factory)

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenDrugSearch = { navController.navigate(Routes.DRUG_SEARCH) },
                onOpenMedisImport = { navController.navigate(Routes.MEDIS_IMPORT) },
                onOpenFillMode = {
                    Log.d(TAG, "home -> fill_mode")
                    navController.navigate(Routes.FILL_MODE)
                },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
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
            MedisImportScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.AUDIT_SCAN) {
            AuditScanScreen(
                viewModel = auditScanViewModel,
                onBack = { navController.popBackStack() },
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
                    Log.d(TAG, "dispensing_complete -> back")
                    navController.popBackStack()
                },
            )
        }
        composable(Routes.FILL_MODE) {
            FillModeScreen(onBack = { navController.popBackStack() })
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
                }
            )
        }
        composable(Routes.DISPENSING_PTP_SCAN) {
            DispensingPtpScanScreen(
                viewModel = dispensingViewModel,
                onBack = { navController.popBackStack() },
                onCompleted = {
                    Log.d(TAG, "dispensing_ptp_scan -> dispensing_complete")
                    navController.navigate(Routes.DISPENSING_COMPLETE) {
                        popUpTo(Routes.DISPENSING_PTP_SCAN) { inclusive = true }
                    }
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
