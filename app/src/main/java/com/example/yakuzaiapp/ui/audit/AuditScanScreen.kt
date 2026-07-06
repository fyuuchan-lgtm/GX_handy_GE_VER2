package com.example.yakuzaiapp.ui.audit

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.yakuzaiapp.R
import com.example.yakuzaiapp.ui.home.HomeBottomTab
import com.example.yakuzaiapp.ui.home.HomeBottomTabBar
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.launch
import java.util.concurrent.Executor

private const val TAG = "AuditScanScreen"
private const val CAMERA_BIND_RETRY_DELAY_MS = 500L
private const val CAMERA_STREAM_WATCHDOG_MS = 2500L
private val PrimaryButtonBlue = Color(0xFF002466)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditScanScreen(
    onBack: () -> Unit,
    onOcrCompleted: () -> Unit,
    onHomeClick: () -> Unit = onBack,
    onAuditClick: () -> Unit = {},
    onReportClick: () -> Unit = onBack,
    onFillClick: () -> Unit = onBack,
    onDataUpdateClick: () -> Unit = onBack,
    viewModel: AuditScanViewModel = viewModel(factory = AuditScanViewModel.Factory)
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
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
    LaunchedEffect(viewModel) {
        viewModel.ocrCompletedEvents.collect {
            onOcrCompleted()
        }
    }
    LaunchedEffect(errorMessage) {
        val message = errorMessage ?: return@LaunchedEffect
        scope.launch {
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.audit_scan_title)) }
            )
        },
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
        if (!hasCameraPermission) {
            AuditPermissionDeniedContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                onRequestPermission = {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                }
            )
        } else {
            AuditCameraContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                viewModel = viewModel,
                onBack = onBack
            )
        }
    }
}

@Composable
private fun AuditPermissionDeniedContent(
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
private fun AuditCameraContent(
    modifier: Modifier,
    viewModel: AuditScanViewModel,
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
    val isProcessing by viewModel.isProcessing.collectAsStateWithLifecycle()
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var cameraBindRetry by remember(isProcessing) { mutableStateOf(0) }
    var previewStreaming by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner, previewView) {
        val streamStateObserver = Observer<PreviewView.StreamState> { state ->
            previewStreaming = state == PreviewView.StreamState.STREAMING
        }
        previewView.previewStreamState.observe(lifecycleOwner, streamStateObserver)
        onDispose {
            previewView.previewStreamState.removeObserver(streamStateObserver)
            previewStreaming = false
        }
    }

    Box(modifier = modifier) {
        if (isProcessing) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
                Text(
                    text = stringResource(R.string.audit_processing),
                    modifier = Modifier.padding(top = 16.dp),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            captureAndRunOcr(
                                imageCapture = imageCapture,
                                viewModel = viewModel,
                                executor = executor
                            )
                        },
                        enabled = imageCapture != null && previewStreaming,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryButtonBlue,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(2f)
                            .height(54.dp)
                    ) {
                        Text(
                            stringResource(R.string.audit_capture_button),
                            style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Button(
                        onClick = onBack,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryButtonBlue,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp)
                    ) {
                        Text(
                            stringResource(R.string.scan_back),
                            style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (cameraProvider == null || !previewStreaming) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }

    DisposableEffect(cameraProvider, previewStreaming, isProcessing) {
        if (isProcessing || cameraProvider == null || previewStreaming) {
            onDispose {
                // no-op
            }
        } else {
            val retryRunnable = Runnable {
                if (cameraProvider != null && !previewStreaming) {
                    Log.w(TAG, "Audit scan preview stream did not start; rebinding camera")
                    try {
                        cameraProvider?.unbindAll()
                    } catch (_: Throwable) {
                        // no-op
                    }
                    imageCapture = null
                    cameraProvider = null
                    cameraBindRetry += 1
                }
            }
            previewView.postDelayed(retryRunnable, CAMERA_STREAM_WATCHDOG_MS)
            onDispose {
                previewView.removeCallbacks(retryRunnable)
            }
        }
    }

    DisposableEffect(lifecycleOwner, isProcessing, cameraBindRetry) {
        if (isProcessing) {
            try {
                cameraProvider?.unbindAll()
            } catch (_: Throwable) {
                // no-op
            }
            imageCapture = null
            cameraProvider = null
            previewStreaming = false
            onDispose {
                // no-op
            }
        } else {
            var disposed = false
            previewStreaming = false
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            val listener = Runnable {
                if (disposed) {
                    return@Runnable
                }
                val provider = runCatching { cameraProviderFuture.get() }
                    .onFailure { e ->
                        Log.w(TAG, "Camera provider unavailable for audit scan", e)
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
                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                try {
                    if (disposed ||
                        !lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
                    ) {
                        imageCapture = null
                        previewStreaming = false
                        if (!disposed) {
                            previewView.postDelayed(
                                { if (!disposed) cameraBindRetry += 1 },
                                CAMERA_BIND_RETRY_DELAY_MS
                            )
                        }
                        return@Runnable
                    }
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        capture
                    )
                    cameraProvider = provider
                    imageCapture = capture
                } catch (e: Throwable) {
                    Log.w(TAG, "Camera binding failure for audit scan", e)
                    cameraProvider = null
                    imageCapture = null
                    previewStreaming = false
                    if (!disposed) {
                        previewView.postDelayed(
                            { if (!disposed) cameraBindRetry += 1 },
                            CAMERA_BIND_RETRY_DELAY_MS
                        )
                    }
                }
            }
            cameraProviderFuture.addListener(listener, executor)

            onDispose {
                disposed = true
                try {
                    cameraProvider?.unbindAll()
                } catch (_: Throwable) {
                    // no-op
                }
                imageCapture = null
                cameraProvider = null
                previewStreaming = false
            }
        }
    }
}

private fun captureAndRunOcr(
    imageCapture: ImageCapture?,
    viewModel: AuditScanViewModel,
    executor: Executor
) {
    val capture = imageCapture ?: return
    capture.takePicture(
        executor,
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val mediaImage = image.image
                if (mediaImage == null) {
                    image.close()
                    return
                }
                val inputImage = InputImage.fromMediaImage(
                    mediaImage,
                    image.imageInfo.rotationDegrees
                )
                viewModel.processImage(inputImage) {
                    image.close()
                }
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e(TAG, "Image capture failed", exception)
            }
        }
    )
}
