package com.lfcreative.lfscan.ui.screen

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

// ── Camera Preview Composable ─────────────────────────────────────────────────

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    torchEnabled: Boolean = false,
    onBarcodeDetected: (String) -> Unit,
    onDetecting: (Boolean) -> Unit = {},
    onCameraReady: (Camera) -> Unit = {}
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }
    val cameraRef = remember { mutableStateOf<Camera?>(null) }
    val onBarcodeDetectedState = rememberUpdatedState(onBarcodeDetected)
    val onDetectingState = rememberUpdatedState(onDetecting)
    val onCameraReadyState = rememberUpdatedState(onCameraReady)

    DisposableEffect(Unit) {
        onDispose { analyzerExecutor.shutdown() }
    }

    // Apply torch state whenever torchEnabled changes OR the camera first becomes available
    LaunchedEffect(torchEnabled, cameraRef.value) {
        cameraRef.value?.cameraControl?.enableTorch(torchEnabled)
    }

    // factory (not update) so bindCamera runs once — torch toggles don't restart the camera
    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                // COMPATIBLE forces TextureView instead of SurfaceView so Compose
                // clip modifiers are respected
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }
            bindCamera(
                context           = ctx,
                lifecycleOwner    = lifecycleOwner,
                previewView       = previewView,
                analyzerExecutor  = analyzerExecutor,
                onDetecting       = { detecting -> onDetectingState.value(detecting) },
                onBarcodeDetected = { code -> onBarcodeDetectedState.value(code) },
                onCameraReady     = { cam ->
                    cameraRef.value = cam
                    onCameraReadyState.value(cam)
                }
            )
            previewView
        },
        modifier = modifier
    )
}

private fun bindCamera(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    previewView: PreviewView,
    analyzerExecutor: java.util.concurrent.Executor,
    onDetecting: (Boolean) -> Unit,
    onBarcodeDetected: (String) -> Unit,
    onCameraReady: (Camera) -> Unit
) {
    val future = ProcessCameraProvider.getInstance(context)
    future.addListener({
        val provider = future.get()

        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
            .build()
        val scanner = BarcodeScanning.getClient(options)

        // 500 ms cooldown — prevents the same frame being processed ~30× per second
        val isProcessing = AtomicBoolean(false)

        val analysis = ImageAnalysis.Builder()
            .setTargetResolution(Size(1280, 720))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build()
        analysis.setAnalyzer(analyzerExecutor) { proxy ->
            analyzeImage(proxy, scanner, onDetecting) { code ->
                if (!isProcessing.getAndSet(true)) {
                    onBarcodeDetected(code)
                    Handler(Looper.getMainLooper()).postDelayed({
                        isProcessing.set(false)
                    }, 500)
                }
            }
        }

        try {
            provider.unbindAll()
            val camera = provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analysis
            )

            // Continuous autofocus centred on the frame, re-triggered every 2 s
            val meteringPoint = previewView.meteringPointFactory.createPoint(0.5f, 0.5f)
            camera.cameraControl.startFocusAndMetering(
                FocusMeteringAction.Builder(meteringPoint, FocusMeteringAction.FLAG_AF)
                    .setAutoCancelDuration(2, TimeUnit.SECONDS)
                    .build()
            )

            onCameraReady(camera)
        } catch (e: Exception) {
            Log.e("CameraPreview", "Bind failed", e)
        }
    }, ContextCompat.getMainExecutor(context))
}

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
private fun analyzeImage(
    proxy: ImageProxy,
    scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    onDetecting: (Boolean) -> Unit,
    onResult: (String) -> Unit
) {
    val image = proxy.image ?: run { proxy.close(); return }
    val input = InputImage.fromMediaImage(image, proxy.imageInfo.rotationDegrees)
    scanner.process(input)
        .addOnSuccessListener { barcodes ->
            onDetecting(barcodes.isNotEmpty())
            barcodes.firstOrNull { it.rawValue != null }?.rawValue?.let(onResult)
        }
        .addOnFailureListener { onDetecting(false) }
        .addOnCompleteListener { proxy.close() }
}
