package com.example.yakuzaiapp.ui.fill

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.yakuzaiapp.R
import com.example.yakuzaiapp.data.local.entity.StaffMaster
import com.example.yakuzaiapp.domain.scan.ScanMode
import com.example.yakuzaiapp.ui.home.HomeBottomTab
import com.example.yakuzaiapp.ui.home.HomeBottomTabBar
import com.example.yakuzaiapp.util.BarcodeAnalyzer
import com.example.yakuzaiapp.util.SoundFeedback
import com.example.yakuzaiapp.util.focusCameraOnPreviewCenter
import java.util.concurrent.Executors

private const val TAG = "FillModeScreen"
private const val ANALYSIS_WIDTH = 1280
private const val ANALYSIS_HEIGHT = 720
private const val VIEW_PORT_BIND_RETRY_LIMIT = 10
private const val VIEW_PORT_BIND_RETRY_DELAY_MS = 50L
private const val CAMERA_BIND_RETRY_DELAY_MS = 500L
private const val STAFF_REQUIRED_NOTICE = "\u5229\u7528\u8005\u767b\u9332\u304c\u5fc5\u8981\u3067\u3059"
private val FillModePanelBlue = Color(0xFF002466)

@Composable
fun FillModeScreen(
    onBack: () -> Unit,
    onHomeClick: () -> Unit = onBack,
    onAuditClick: () -> Unit = onBack,
    onReportClick: () -> Unit = onBack,
    onFillClick: () -> Unit = {},
    onDataUpdateClick: () -> Unit = onBack,
    viewModel: FillModeViewModel = viewModel(factory = FillModeViewModel.Factory)
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(uiState.phase, uiState.selectedDrugName, uiState.warningExpirationDate) {
        if ((uiState.phase == FillModeStage.SELECT_TARGET || uiState.phase == FillModeStage.COMPLETED) &&
            !uiState.selectedDrugName.isNullOrBlank()
        ) {
            playFillModeFeedback(
                context = context,
                isWarning = uiState.expirationWarningMessage != null
            )
        }
    }

    Scaffold(
        bottomBar = {
            HomeBottomTabBar(
                selectedTab = HomeBottomTab.FILL,
                onHomeClick = onHomeClick,
                onAuditClick = onAuditClick,
                onReportClick = onReportClick,
                onFillClick = onFillClick,
                onDataUpdateClick = onDataUpdateClick
            )
        }
    ) { padding ->
        if (hasCameraPermission) {
            FillModeCameraContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                uiState = uiState,
                onBarcodeDetected = viewModel::onBarcodeScanned,
                onDismissExpirationWarning = viewModel::dismissExpirationWarning,
                onReset = viewModel::reset
            )
        } else {
            FillModePermissionContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                onRequestPermission = {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                }
            )
        }
    }
}

private fun playFillModeFeedback(context: Context, isWarning: Boolean) {
    if (isWarning) {
        SoundFeedback.playError()
    } else {
        SoundFeedback.playSuccess()
    }
    runCatching {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(VibratorManager::class.java)
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } ?: return@runCatching

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createWaveform(
                    longArrayOf(0L, 80L, 60L, 80L),
                    -1
                )
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0L, 80L, 60L, 80L), -1)
        }
    }
}

@Composable
private fun FillModePermissionContent(
    modifier: Modifier,
    onRequestPermission: () -> Unit
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(stringResource(R.string.scan_permission_message))
            Button(onClick = onRequestPermission) {
                Text(stringResource(R.string.scan_permission_button))
            }
        }
    }
}

@Composable
private fun StaffSelector(
    staffList: List<StaffMaster>,
    selectedStaffId: String?,
    selectedStaffName: String?,
    selectedStaffKana: String?,
    onStaffSelected: (StaffMaster) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedText = when {
        selectedStaffName.isNullOrBlank() -> "実施者を選択"
        selectedStaffKana.isNullOrBlank() -> selectedStaffName
        else -> "$selectedStaffName（$selectedStaffKana）"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "実施者",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Box(modifier = Modifier.weight(1f)) {
            Button(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = staffList.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF0D47A1),
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = if (staffList.isEmpty()) "利用者登録してください" else selectedText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                staffList.forEach { staff ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = listOfNotNull(
                                    staff.displayName(),
                                    staff.staffKana?.let { "（$it）" }
                                ).joinToString("")
                            )
                        },
                        onClick = {
                            expanded = false
                            onStaffSelected(staff)
                        },
                        enabled = staff.staffId != selectedStaffId
                    )
                }
            }
        }
    }
}

@Composable
private fun FillModeCameraContent(
    modifier: Modifier,
    uiState: FillModeUiState,
    onBarcodeDetected: (String) -> Unit,
    onDismissExpirationWarning: () -> Unit,
    onReset: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember(context) {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val latestOnBarcodeDetected by rememberUpdatedState(onBarcodeDetected)
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var activeCamera by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }
    var cameraBindRetry by remember(uiState.isComplete) { mutableStateOf(0) }
    val cameraEnabled = !uiState.isComplete && !uiState.selectedStaffId.isNullOrBlank()
    val analyzer = remember(context) {
        BarcodeAnalyzer(
            context = context,
            mode = ScanMode.PTP_GTIN,
            cooldownMs = 1000L,
            useMlKitFallback = true,
            useTextExpirationFallback = true,
            restrictPtpToCenter = true
        ) { detections ->
            detections.forEach { detection ->
                latestOnBarcodeDetected(detection.text)
            }
        }
    }

    DisposableEffect(analysisExecutor) {
        onDispose {
            analysisExecutor.shutdown()
        }
    }

    DisposableEffect(analyzer) {
        onDispose {
            analyzer.close()
        }
    }

    Column(modifier = modifier.background(Color.White)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .background(Color.White)
                .zIndex(1f),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = stringResource(R.string.fill_mode_title),
                modifier = Modifier.padding(horizontal = 20.dp),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (uiState.isComplete || !cameraEnabled) 96.dp else 221.dp)
                .background(if (uiState.isComplete || !cameraEnabled) Color.White else Color(0xFF202020))
        ) {
            if (cameraEnabled) {
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.78f)
                        .fillMaxHeight(0.84f)
                        .align(Alignment.Center)
                        .border(
                            width = 2.dp,
                            color = Color.White.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(8.dp)
                        )
                )
                if (cameraProvider == null) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
            } else if (!uiState.isComplete) {
                Text(
                    text = STAFF_REQUIRED_NOTICE,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 20.dp),
                    color = Color(0xFF6B4E00),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.White)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 10.dp)
        ) {
            FillModeStatusPanel(
                fillDrugName = uiState.selectedDrugName ?: "未読取",
                fillExpirationDate = uiState.selectedSourceExpirationDate,
                cassetteDrugName = if (uiState.isComplete) {
                    uiState.selectedDrugName ?: "未読取"
                } else {
                    "未読取"
                }
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onReset,
                enabled = uiState.isComplete,
                modifier = Modifier
                    .weight(1.55f)
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF0D47A1),
                    contentColor = Color.White,
                    disabledContainerColor = Color(0xFFE0E0E0),
                    disabledContentColor = Color(0xFF777777)
                )
            ) {
                Text(
                    text = stringResource(R.string.fill_mode_reset),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
            Button(
                onClick = onReset,
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF0D47A1),
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = "やり直し",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    uiState.expirationWarningMessage?.let { message ->
        ExpirationWarningDialog(
            message = message,
            onDismiss = onDismissExpirationWarning
        )
    }

    DisposableEffect(lifecycleOwner, analyzer, cameraEnabled, cameraBindRetry) {
        if (!cameraEnabled) {
            runCatching { cameraProvider?.unbindAll() }
            cameraProvider = null
            activeCamera = null
            onDispose {}
        } else {
            var disposed = false
            var analysisUseCase: ImageAnalysis? = null
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            val listener = Runnable {
                if (disposed) {
                    return@Runnable
                }
                val provider = runCatching { cameraProviderFuture.get() }
                    .onFailure { e ->
                        Log.w(TAG, "Camera provider unavailable for fill mode", e)
                        if (!disposed) {
                            previewView.postDelayed(
                                { if (!disposed) cameraBindRetry += 1 },
                                CAMERA_BIND_RETRY_DELAY_MS
                            )
                        }
                    }
                    .getOrNull() ?: return@Runnable
                if (disposed) {
                    return@Runnable
                }

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setTargetResolution(Size(ANALYSIS_WIDTH, ANALYSIS_HEIGHT))
                    .build()
                    .also {
                        it.setAnalyzer(analysisExecutor, analyzer)
                    }
                analysisUseCase = analysis

                try {
                    if (disposed) {
                        analysis.clearAnalyzer()
                        return@Runnable
                    }
                    provider.unbindAll()
                    fun bindCroppedUseCases(retryCount: Int = 0) {
                        if (disposed) {
                            return
                        }
                        val viewPort = previewView.viewPort
                        if (viewPort == null) {
                            if (retryCount < VIEW_PORT_BIND_RETRY_LIMIT) {
                                previewView.postDelayed(
                                    {
                                        if (!disposed) {
                                            bindCroppedUseCases(retryCount + 1)
                                        }
                                    },
                                    VIEW_PORT_BIND_RETRY_DELAY_MS
                                )
                            } else {
                                Log.w(TAG, "Fill mode camera viewport unavailable after retries")
                                activeCamera = provider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    analysis
                                )
                                cameraProvider = provider
                                focusCameraOnPreviewCenter(activeCamera, previewView, TAG)
                            }
                            return
                        }
                        if (disposed ||
                            !lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
                        ) {
                            return
                        }
                        val useCaseGroup = UseCaseGroup.Builder()
                            .setViewPort(viewPort)
                            .addUseCase(preview)
                            .addUseCase(analysis)
                            .build()
                        activeCamera = provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            useCaseGroup
                        )
                        cameraProvider = provider
                        focusCameraOnPreviewCenter(activeCamera, previewView, TAG)
                    }

                    bindCroppedUseCases()
                } catch (e: Throwable) {
                    Log.w(TAG, "Camera binding failure for fill mode", e)
                    cameraProvider = null
                    activeCamera = null
                    analysis.clearAnalyzer()
                    if (!disposed) {
                        previewView.postDelayed(
                            { if (!disposed) cameraBindRetry += 1 },
                            CAMERA_BIND_RETRY_DELAY_MS
                        )
                    }
                }
            }
            cameraProviderFuture.addListener(listener, mainExecutor)

            onDispose {
                disposed = true
                analysisUseCase?.clearAnalyzer()
                runCatching { cameraProvider?.unbindAll() }
                cameraProvider = null
                activeCamera = null
            }
        }
    }
}

@Composable
private fun ExpirationWarningDialog(
    message: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFFD50000),
        shape = RoundedCornerShape(8.dp),
        title = {
            Text(
                text = "警告",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = message,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = "確認",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    )
}

@Composable
private fun FillModeStatusPanel(
    fillDrugName: String,
    fillExpirationDate: String?,
    cassetteDrugName: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = FillModePanelBlue,
                shape = RoundedCornerShape(12.dp)
            )
            .border(
                width = 2.dp,
                color = FillModePanelBlue,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.Start
    ) {
        FillModeSectionLabel("充填薬")
        FillModeDrugNameText(fillDrugName)
        FillModeValueText("使用期限: ${fillExpirationDate ?: "未取得"}")
        Spacer(modifier = Modifier.height(12.dp))
        FillModeSectionLabel("カセット")
        FillModeDrugNameText(cassetteDrugName)
    }
}

@Composable
private fun FillModeSectionLabel(text: String) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun FillModeValueText(text: String) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Normal,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun FillModeDrugNameText(text: String) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Normal,
        minLines = 2,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth()
    )
}
