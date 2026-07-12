package com.example.yakuzaiapp.ui.scan

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import com.example.yakuzaiapp.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import com.example.yakuzaiapp.domain.jahis.AssembleResult
import com.example.yakuzaiapp.domain.jahis.DetectedQr
import com.example.yakuzaiapp.domain.scan.ScanMode
import com.example.yakuzaiapp.ui.home.HomeBottomTab
import com.example.yakuzaiapp.ui.home.HomeBottomTabBar
import com.example.yakuzaiapp.util.BarcodeAnalyzer
import com.example.yakuzaiapp.util.focusCameraOnPreviewCenter
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

private const val TAG = "ScanScreen"
private const val PTP_ANALYSIS_WIDTH = 1280
private const val PTP_ANALYSIS_HEIGHT = 720
private const val JAHIS_ANALYSIS_WIDTH = 1280
private const val JAHIS_ANALYSIS_HEIGHT = 720
private const val CAMERA_BIND_RETRY_DELAY_MS = 500L
private val PrimaryButtonBlue = Color(0xFF002466)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    mode: ScanMode = ScanMode.PTP_GTIN,
    continuousMode: Boolean = false,
    onResult: (String) -> Unit,
    onBack: () -> Unit,
    bottomTab: HomeBottomTab? = null,
    onHomeClick: () -> Unit = onBack,
    onAuditClick: () -> Unit = onBack,
    onReportClick: () -> Unit = onBack,
    onFillClick: () -> Unit = onBack,
    onDataUpdateClick: () -> Unit = onBack
) {
    val context = LocalContext.current
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
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(mode) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            bottomTab?.let {
                HomeBottomTabBar(
                    selectedTab = it,
                    onHomeClick = onHomeClick,
                    onAuditClick = onAuditClick,
                    onReportClick = onReportClick,
                    onFillClick = onFillClick,
                    onDataUpdateClick = onDataUpdateClick
                )
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (mode) {
                            ScanMode.PTP_GTIN -> stringResource(R.string.scan_ptp_title)
                            ScanMode.JAHIS_QR -> stringResource(R.string.scan_jahis_title)
                        },
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    if (mode == ScanMode.PTP_GTIN) {
                        TextButton(onClick = onBack) {
                            Text(stringResource(R.string.scan_back))
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (!hasCameraPermission) {
            PermissionDeniedContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                onRequestPermission = {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                }
            )
        } else {
            CameraScanContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                mode = mode,
                continuousMode = continuousMode,
                onResult = onResult,
                onBack = onBack
            )
        }
    }
}

@Composable
private fun PermissionDeniedContent(
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
private fun CameraScanContent(
    modifier: Modifier,
    mode: ScanMode,
    continuousMode: Boolean,
    onResult: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnResult by rememberUpdatedState(onResult)
    val previewView = remember(context) {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var activeCamera by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }
    var cameraBindRetry by remember(mode, continuousMode) { mutableStateOf(0) }
    var ptpZoomRatio by remember(mode) { mutableStateOf(2.0f) }
    val jahisScanViewModel: JahisQrScanViewModel = viewModel()
    val jahisFragments by jahisScanViewModel.fragments.collectAsStateWithLifecycle()
    val autoAssembleEvent by jahisScanViewModel.autoAssembleEvent.collectAsStateWithLifecycle()
    var jahisDelivered by remember(mode) { mutableStateOf(false) }
    val latestJahisDelivered by rememberUpdatedState(jahisDelivered)

    DisposableEffect(analysisExecutor) {
        onDispose {
            analysisExecutor.shutdown()
        }
    }

    val analyzer = remember(context, mode, continuousMode) {
        BarcodeAnalyzer(
            context = context,
            mode = mode,
            cooldownMs = when (mode) {
                ScanMode.PTP_GTIN -> if (continuousMode) 500L else 2000L
                ScanMode.JAHIS_QR -> 1000L
            },
            restrictPtpToCenter = mode == ScanMode.PTP_GTIN
        ) { detections ->
            when (mode) {
                ScanMode.PTP_GTIN -> handlePtpDetections(
                    detections = detections,
                    onResult = latestOnResult
                )

                ScanMode.JAHIS_QR -> handleJahisDetections(
                    detections = detections,
                    jahisScanViewModel = jahisScanViewModel
                )
            }
        }
    }

    DisposableEffect(analyzer) {
        onDispose {
            analyzer.close()
        }
    }

    if (mode == ScanMode.JAHIS_QR) {
        LaunchedEffect(autoAssembleEvent, latestJahisDelivered) {
            val event = autoAssembleEvent
            if (event != null && !latestJahisDelivered) {
                onResult(event.fullText)
                jahisDelivered = true
                jahisScanViewModel.consumeAutoAssembleEvent()
            }
        }
    }

    LaunchedEffect(mode, activeCamera, ptpZoomRatio) {
        if (mode == ScanMode.PTP_GTIN) {
            runCatching {
                activeCamera?.cameraControl?.setZoomRatio(ptpZoomRatio)
            }.onFailure { e ->
                Log.w(TAG, "Failed to apply PTP zoom ratio=$ptpZoomRatio", e)
            }
            focusCameraOnPreviewCenter(activeCamera, previewView, TAG)
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        if (mode == ScanMode.PTP_GTIN) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.scan_ptp_hint),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { ptpZoomRatio = 1.0f },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (ptpZoomRatio == 1.0f) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.secondary
                            }
                        )
                    ) {
                        Text("箱 1x")
                    }
                    Button(
                        onClick = { ptpZoomRatio = 2.0f },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (ptpZoomRatio == 2.0f) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.secondary
                            }
                        )
                    ) {
                        Text("PTP 2x")
                    }
                }
            }
        }

        if (mode == ScanMode.JAHIS_QR) {
            val saInfo = jahisFragments.firstOrNull { it.saTotal != null }
            val countText = if (jahisFragments.isNotEmpty()) {
                if (saInfo?.saTotal != null) {
                    stringResource(
                        R.string.scan_jahis_detected_fragments_with_total,
                        jahisFragments.size,
                        saInfo.saTotal
                    )
                } else {
                    stringResource(
                        R.string.scan_jahis_detected_fragments,
                        jahisFragments.size
                    )
                }
            } else {
                stringResource(R.string.scan_jahis_aim_hint)
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Color.White)
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.scan_jahis_hint),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium
                )
                if (jahisFragments.isNotEmpty()) {
                    Text(
                        text = countText,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val result = attemptJahisParse(
                                jahisScanViewModel = jahisScanViewModel,
                                onResult = latestOnResult
                            )
                            when (result) {
                                is AssembleResult.Success -> {
                                    jahisDelivered = true
                                }

                                is AssembleResult.Incomplete -> {
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            result.detailMessage
                                                ?: context.getString(
                                                    R.string.scan_jahis_incomplete_snackbar,
                                                    result.fragmentCount
                                                )
                                        )
                                    }
                                }

                                AssembleResult.NoHeader -> {
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            context.getString(R.string.scan_jahis_no_fragments_snackbar)
                                        )
                                    }
                                }

                                is AssembleResult.ParseFailed -> {
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            context.getString(
                                                R.string.scan_jahis_parse_failed_snackbar,
                                                result.message
                                            )
                                        )
                                    }
                                }
                            }
                        },
                        enabled = jahisFragments.isNotEmpty(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryButtonBlue,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(2f)
                            .height(58.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.scan_jahis_parse_button),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Button(
                        onClick = {
                            jahisScanViewModel.clear()
                            jahisDelivered = false
                            onBack()
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryButtonBlue,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(58.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.scan_back),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                        }
        }

        if (cameraProvider == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }

    DisposableEffect(mode, continuousMode, lifecycleOwner, analyzer, cameraBindRetry) {
        var disposed = false
        var analysisUseCase: ImageAnalysis? = null
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val listener = Runnable {
            if (disposed) {
                return@Runnable
            }
            val provider = runCatching { cameraProviderFuture.get() }
                .onFailure { e ->
                    Log.w(TAG, "Camera provider unavailable for mode=$mode", e)
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
            val analysisBuilder = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            when (mode) {
                ScanMode.PTP_GTIN -> {
                    analysisBuilder.setTargetResolution(Size(PTP_ANALYSIS_WIDTH, PTP_ANALYSIS_HEIGHT))
                }
                ScanMode.JAHIS_QR -> {
                    analysisBuilder.setTargetResolution(Size(JAHIS_ANALYSIS_WIDTH, JAHIS_ANALYSIS_HEIGHT))
                }
            }
            val analysis = analysisBuilder.build().also {
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
                if (mode == ScanMode.PTP_GTIN) {
                    focusCameraOnPreviewCenter(activeCamera, previewView, TAG)
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Camera binding failure for mode=$mode", e)
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
            try {
                cameraProvider?.unbindAll()
            } catch (_: Throwable) {
                // no-op
            }
            activeCamera = null
            cameraProvider = null
        }
    }
}

private fun handlePtpDetections(
    detections: List<DetectedQr>,
    onResult: (String) -> Unit
) {
    detections.firstOrNull()?.text?.let { text ->
        onResult(text)
    }
}

private fun handleJahisDetections(
    detections: List<DetectedQr>,
    jahisScanViewModel: JahisQrScanViewModel
) {
    jahisScanViewModel.recordDetections(detections)
}

private fun attemptJahisParse(
    jahisScanViewModel: JahisQrScanViewModel,
    onResult: (String) -> Unit
): AssembleResult {
    val result = jahisScanViewModel.assemble(source = "manual")
    when (result) {
        is AssembleResult.Success -> {
            onResult(result.fullText)
        }

        is AssembleResult.Incomplete -> Unit

        AssembleResult.NoHeader -> Unit

        is AssembleResult.ParseFailed -> Unit
    }
    return result
}
