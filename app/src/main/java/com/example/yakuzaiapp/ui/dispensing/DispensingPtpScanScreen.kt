package com.example.yakuzaiapp.ui.dispensing

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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.yakuzaiapp.domain.dispensing.ExpectedDrugItem
import com.example.yakuzaiapp.domain.dispensing.ItemStatus
import com.example.yakuzaiapp.domain.dispensing.ScanMatchResult
import com.example.yakuzaiapp.domain.scan.ScanMode
import com.example.yakuzaiapp.util.BarcodeAnalyzer
import com.example.yakuzaiapp.util.SoundFeedback
import com.example.yakuzaiapp.util.VibrationFeedback

private const val TAG = "DispensingPtpScan"
private const val PTP_ANALYSIS_WIDTH = 1280
private const val PTP_ANALYSIS_HEIGHT = 720
private const val PTP_ZOOM_RATIO = 2.0f

@Composable
fun DispensingPtpScanScreen(
    viewModel: DispensingViewModel,
    onBack: () -> Unit,
    onCompleted: () -> Unit
) {
    val context = LocalContext.current
    val session by viewModel.session.collectAsStateWithLifecycle()
    val isAllChecked by viewModel.isAllChecked.collectAsStateWithLifecycle()
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
        viewModel.clearScanFeedback()
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.scanFeedback.collect { result ->
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
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.White)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "PTP確認",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = onBack) {
                    Text("戻る")
                }
            }

            when {
                !hasCameraPermission -> PermissionContent(
                    onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) }
                )

                session == null -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("処方リストがありません")
                }

                else -> PtpCameraAndDispensingList(
                    modifier = Modifier.fillMaxSize(),
                    items = session!!.items,
                    isAllChecked = isAllChecked,
                    onBarcodeDetected = viewModel::onPtpScanned,
                    onCompleted = onCompleted
                )
            }
        }
    }
}

@Composable
private fun PermissionContent(
    onRequestPermission: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("カメラ権限が必要です")
            Button(onClick = onRequestPermission) {
                Text("権限を許可する")
            }
        }
    }
}

@Composable
private fun PtpCameraAndDispensingList(
    modifier: Modifier,
    items: List<ExpectedDrugItem>,
    isAllChecked: Boolean,
    onBarcodeDetected: (String) -> Unit,
    onCompleted: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember(context) { ContextCompat.getMainExecutor(context) }
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
            cooldownMs = 500L
        ) { detections ->
            detections.firstOrNull()?.text?.let { latestOnBarcodeDetected(it) }
        }
    }

    LaunchedEffect(isAllChecked) {
        if (isAllChecked) {
            onCompleted()
        }
    }

    Column(modifier = modifier.background(Color.White)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.30f)
        ) {
            if (!isAllChecked) {
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (!isAllChecked && cameraProvider == null) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }

        Text(
            text = "${items.count { it.status != ItemStatus.UNCHECKED }} / ${items.size} 確認済み",
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 16.dp, vertical = 6.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.55f)
                .background(Color.White),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
        ) {
            items(items, key = { it.id }) { item ->
                DispensingPtpRow(item = item)
                HorizontalDivider()
            }
        }
    }

    DisposableEffect(analyzer) {
        onDispose {
            analyzer.close()
        }
    }

    LaunchedEffect(activeCamera) {
        runCatching {
            activeCamera?.cameraControl?.setZoomRatio(PTP_ZOOM_RATIO)
        }.onFailure { e ->
            Log.w(TAG, "Failed to apply PTP zoom", e)
        }
    }

    DisposableEffect(lifecycleOwner, analyzer, isAllChecked) {
        if (isAllChecked) {
            runCatching { cameraProvider?.unbindAll() }
            cameraProvider = null
            activeCamera = null
            onDispose {
                // no-op
            }
        } else {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            val listener = Runnable {
                val provider = cameraProviderFuture.get()
                cameraProvider = provider

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setTargetResolution(Size(PTP_ANALYSIS_WIDTH, PTP_ANALYSIS_HEIGHT))
                    .build()
                    .also {
                        it.setAnalyzer(executor, analyzer)
                    }

                try {
                    provider.unbindAll()
                    activeCamera = provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis
                    )
                    activeCamera?.cameraControl?.setZoomRatio(PTP_ZOOM_RATIO)
                } catch (e: Throwable) {
                    Log.w(TAG, "Camera binding failure for dispensing ptp scan", e)
                }
            }
            cameraProviderFuture.addListener(listener, executor)

            onDispose {
                runCatching { cameraProvider?.unbindAll() }
                cameraProvider = null
                activeCamera = null
            }
        }
    }
}

@Composable
private fun DispensingPtpRow(
    item: ExpectedDrugItem
) {
    val isExcluded = item.status == ItemStatus.PACKING_MACHINE
    val rowBackground = if (isExcluded) Color(0xFF808080) else Color.White
    val badgeText = when (item.status) {
        ItemStatus.UNCHECKED -> "未"
        ItemStatus.CONFIRMED -> "済"
        ItemStatus.PACKING_MACHINE -> "対象外"
    }
    val badgeColor = when (item.status) {
        ItemStatus.UNCHECKED -> Color(0xFFD32F2F)
        ItemStatus.CONFIRMED -> Color(0xFF2E7D32)
        ItemStatus.PACKING_MACHINE -> Color(0xFF1565C0)
    }
    val contentColor = if (isExcluded) Color.White else MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(rowBackground)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.width(64.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "RP${item.rpNumber}",
                color = contentColor,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(badgeColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = badgeText,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
        Text(
            text = item.drugName,
            color = contentColor,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun shouldVibrateForFeedback(result: ScanMatchResult): Boolean {
    return result !is ScanMatchResult.Success &&
        result !is ScanMatchResult.AlreadyConfirmed &&
        result !is ScanMatchResult.PackingMachine
}
