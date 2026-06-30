package com.example.yakuzaiapp.ui.fill

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
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
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.yakuzaiapp.R
import com.example.yakuzaiapp.domain.scan.ScanMode
import com.example.yakuzaiapp.util.BarcodeAnalyzer

private const val TAG = "FillModeScreen"
private const val ANALYSIS_WIDTH = 1280
private const val ANALYSIS_HEIGHT = 720

@Composable
fun FillModeScreen(
    onBack: () -> Unit,
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

    LaunchedEffect(uiState.phase, uiState.selectedDrugName) {
        if ((uiState.phase == FillModeStage.SELECT_TARGET || uiState.phase == FillModeStage.COMPLETED) &&
            !uiState.selectedDrugName.isNullOrBlank()
        ) {
            playFillModeFeedback(context)
        }
    }

    Scaffold { padding ->
        if (hasCameraPermission) {
            FillModeCameraContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                uiState = uiState,
                onBarcodeDetected = viewModel::onBarcodeScanned,
                onReset = viewModel::reset,
                onBack = onBack
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

private fun playFillModeFeedback(context: Context) {
    runCatching {
        val toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
        toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP2, 160)
        Handler(Looper.getMainLooper()).postDelayed({ toneGenerator.release() }, 250L)
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
private fun FillModeCameraContent(
    modifier: Modifier,
    uiState: FillModeUiState,
    onBarcodeDetected: (String) -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit
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
    val analyzer = remember(context) {
        BarcodeAnalyzer(
            context = context,
            mode = ScanMode.PTP_GTIN,
            cooldownMs = 1000L
        ) { detections ->
            detections.forEach { detection ->
                latestOnBarcodeDetected(detection.text)
            }
        }
    }

    Column(modifier = modifier.background(Color.White)) {
        Text(
            text = stringResource(R.string.fill_mode_title),
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 20.dp, vertical = 18.dp),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0xFF202020))
        ) {
            if (uiState.isComplete) {
                Text(
                    text = "カメラ画面",
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            } else {
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier.fillMaxSize()
                )
                if (cameraProvider == null) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            FillModeDrugRow(
                label = "充填薬",
                drugName = uiState.selectedDrugName ?: "未読取"
            )
            FillModeDrugRow(
                label = "カセット",
                drugName = if (uiState.isComplete) {
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
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onReset,
                enabled = uiState.isComplete,
                modifier = Modifier
                    .weight(2f)
                    .height(58.dp),
                shape = RectangleShape,
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
                onClick = onBack,
                modifier = Modifier
                    .weight(1f)
                    .height(58.dp),
                shape = RectangleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF0D47A1),
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = stringResource(R.string.scan_back),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    DisposableEffect(lifecycleOwner, analyzer, uiState.isComplete) {
        if (uiState.isComplete) {
            runCatching { cameraProvider?.unbindAll() }
            cameraProvider = null
            onDispose {
                analyzer.close()
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
                    .setTargetResolution(Size(ANALYSIS_WIDTH, ANALYSIS_HEIGHT))
                    .build()
                    .also {
                        it.setAnalyzer(executor, analyzer)
                    }

                try {
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis
                    )
                } catch (e: Throwable) {
                    Log.w(TAG, "Camera binding failure for fill mode", e)
                }
            }
            cameraProviderFuture.addListener(listener, executor)

            onDispose {
                runCatching { cameraProvider?.unbindAll() }
                cameraProvider = null
                analyzer.close()
            }
        }
    }
}

@Composable
private fun FillModeDrugRow(
    label: String,
    drugName: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(0.9f)
                .height(56.dp)
                .background(Color(0xFF0B3D16)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            modifier = Modifier
                .weight(2.1f)
                .height(56.dp)
                .background(Color(0xFFD50000)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = drugName,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }
    }
}
