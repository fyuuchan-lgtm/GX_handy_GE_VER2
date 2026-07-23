package com.example.yakuzaiapp.ui.audit

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.yakuzaiapp.R
import com.example.yakuzaiapp.domain.dispensing.ScanMatchResult
import com.example.yakuzaiapp.domain.scan.ScanMode
import com.example.yakuzaiapp.ui.home.HomeBottomTab
import com.example.yakuzaiapp.ui.home.HomeBottomTabBar
import com.example.yakuzaiapp.util.BarcodeAnalyzer
import com.example.yakuzaiapp.util.SoundFeedback
import com.example.yakuzaiapp.util.VibrationFeedback
import com.example.yakuzaiapp.util.focusCameraOnPreviewCenter
import java.util.concurrent.Executors

private const val TAG = "AuditPtpScanScreen"
private const val PTP_ZOOM_RATIO = 2.0f
private val PrimaryButtonBlue = Color(0xFF002466)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditPtpScanScreen(
    onBack: () -> Unit,
    onComplete: () -> Unit,
    onCancel: () -> Unit,
    onHomeClick: () -> Unit,
    onAuditClick: () -> Unit,
    onReportClick: () -> Unit,
    onFillClick: () -> Unit,
    onDataUpdateClick: () -> Unit,
    auditViewModel: AuditScanViewModel,
    viewModel: AuditPtpScanViewModel = viewModel(factory = AuditPtpScanViewModel.Factory)
    ) {
    val context = LocalContext.current
    val auditResults by auditViewModel.matchResults.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val confirmedResults = remember(auditResults) {
        auditResults.filter { it.status == com.example.yakuzaiapp.domain.audit.MatchStatus.CONFIRMED }
    }
    val hasCameraPermission = remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission.value = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission.value) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    LaunchedEffect(confirmedResults) {
        if (confirmedResults.isNotEmpty()) {
            viewModel.initializeFromAudit(confirmedResults)
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.scanFeedback.collect { result ->
            when (result) {
                is ScanMatchResult.Success -> SoundFeedback.playSuccess()
                is ScanMatchResult.AlreadyConfirmed,
                is ScanMatchResult.PackingMachine -> Unit
                else -> {
                    SoundFeedback.playError()
                    if (shouldVibrateForFeedback(result)) {
                        VibrationFeedback.error(context)
                    }
                }
            }
            viewModel.clearMessage()
        }
    }

    Scaffold(
        bottomBar = {
            HomeBottomTabBar(
                selectedTab = HomeBottomTab.AUDIT,
                onHomeClick = onHomeClick,
                onAuditClick = onAuditClick,
                onReportClick = onReportClick,
                onFillClick = onFillClick,
                onDataUpdateClick = onDataUpdateClick
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.ptp_scan_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                TextButton(
                    onClick = onBack,
                    colors = ButtonDefaults.textButtonColors(contentColor = PrimaryButtonBlue)
                ) {
                    Text(stringResource(R.string.scan_back))
                }
            }

            if (!hasCameraPermission.value) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(stringResource(R.string.scan_permission_message))
                        Button(onClick = {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        }) {
                            Text(stringResource(R.string.scan_permission_button))
                        }
                    }
                }
            } else {
                PtpCameraAndList(
                    modifier = Modifier.fillMaxSize(),
                    rows = uiState.rows,
                    isComplete = uiState.isComplete,
                    onBarcodeDetected = viewModel::onBarcodeScanned,
                    onComplete = onComplete,
                    onCancel = onCancel
                )
            }
        }
    }
}

@Composable
private fun PtpCameraAndList(
    modifier: Modifier,
    rows: List<AuditPtpScanViewModel.PtpScanRow>,
    isComplete: Boolean,
    onBarcodeDetected: (String) -> Unit,
    onComplete: () -> Unit,
    onCancel: () -> Unit
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
    val analyzer = remember(context) {
        BarcodeAnalyzer(
            context = context,
            mode = ScanMode.PTP_GTIN,
            cooldownMs = 1000L,
            restrictPtpToCenter = true
        ) { detections ->
            detections.forEach { detection ->
                latestOnBarcodeDetected(detection.text)
            }
        }
    }

    LaunchedEffect(activeCamera) {
        runCatching {
            activeCamera?.cameraControl?.setZoomRatio(PTP_ZOOM_RATIO)
        }.onFailure { e ->
            Log.w(TAG, "Failed to apply PTP zoom", e)
        }
        focusCameraOnPreviewCenter(activeCamera, previewView, TAG)
    }

    Column(
        modifier = modifier.background(Color.White)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.315f)
        ) {
            if (isComplete) {
                Text(
                    text = stringResource(R.string.ptp_scan_all_complete),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = Color(0xFF2E7D32)
                )
            } else {
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
            }
            if (!isComplete && cameraProvider == null) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(
                R.string.ptp_scan_progress,
                rows.count { it.scanned },
                rows.size
            ),
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 16.dp, vertical = 4.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.40f)
                .background(Color.White),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
        ) {
            items(rows, key = { it.yjCode }) { row ->
                PtpScanRowCard(row = row)
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
                onClick = onComplete,
                enabled = isComplete,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryButtonBlue,
                    contentColor = Color.White
                ),
                modifier = Modifier.weight(2f)
            ) {
                Text(
                    text = "監査終了",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
            Button(
                onClick = onCancel,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryButtonBlue,
                    contentColor = Color.White
                ),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "中止",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
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

    DisposableEffect(lifecycleOwner, analyzer, isComplete) {
        if (isComplete) {
            try {
                cameraProvider?.unbindAll()
            } catch (_: Throwable) {
                // no-op
            }
            cameraProvider = null
            activeCamera = null
            onDispose {
                // no-op
            }
        } else {
            var disposed = false
            var analysisUseCase: ImageAnalysis? = null
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            val listener = Runnable {
                if (disposed) return@Runnable
                val provider = cameraProviderFuture.get()
                if (disposed) return@Runnable

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setTargetResolution(Size(1280, 720))
                    .build()
                    .also {
                        it.setAnalyzer(analysisExecutor, analyzer)
                    }
                analysisUseCase = analysis

                try {
                    if (disposed ||
                        !lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
                    ) {
                        analysis.clearAnalyzer()
                        return@Runnable
                    }
                    provider.unbindAll()
                    activeCamera = provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis
                    )
                    cameraProvider = provider
                    activeCamera?.cameraControl?.setZoomRatio(PTP_ZOOM_RATIO)
                    focusCameraOnPreviewCenter(activeCamera, previewView, TAG)
                } catch (e: Throwable) {
                    Log.w(TAG, "Camera binding failure for audit ptp scan", e)
                }
            }
            cameraProviderFuture.addListener(listener, mainExecutor)

            onDispose {
                disposed = true
                analysisUseCase?.clearAnalyzer()
                try {
                    cameraProvider?.unbindAll()
                } catch (_: Throwable) {
                    // no-op
                }
                cameraProvider = null
                activeCamera = null
            }
        }
    }
}

@Composable
private fun PtpScanRowCard(
    row: AuditPtpScanViewModel.PtpScanRow
) {
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    val badgeText = if (row.scanned) "済" else "未"
    val badgeColor = if (row.scanned) Color(0xFF2E7D32) else Color(0xFFD32F2F)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .border(1.dp, borderColor, RectangleShape)
            .background(Color.White)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.width(42.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(42.dp)
                    .height(28.dp)
                    .background(badgeColor, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = badgeText,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        Text(
            text = row.displayName,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = row.quantityDisplay.orEmpty(),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(min = 44.dp)
        )
    }
}

private fun shouldVibrateForFeedback(result: ScanMatchResult): Boolean {
    return result !is ScanMatchResult.Success &&
        result !is ScanMatchResult.AlreadyConfirmed &&
        result !is ScanMatchResult.PackingMachine
}
