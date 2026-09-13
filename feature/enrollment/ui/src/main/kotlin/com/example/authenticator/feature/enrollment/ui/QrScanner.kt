package com.example.authenticator.feature.enrollment.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** CameraX/ML Kit QR reader. Only the decoded text crosses the UI boundary. */
@Composable
fun QrScanner(onResult: (String) -> Unit, onUnavailable: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val delivered = remember { AtomicBoolean(false) }
    DisposableEffect(lifecycleOwner) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            onUnavailable()
            onDispose { executor.shutdown() }
        } else {
            val providerFuture = ProcessCameraProvider.getInstance(context)
            var cameraProvider: ProcessCameraProvider? = null
            var scanner: com.google.mlkit.vision.barcode.BarcodeScanner? = null
            providerFuture.addListener({
                runCatching {
                    val provider = providerFuture.get()
                    cameraProvider = provider
                    val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                    val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                    scanner = BarcodeScanning.getClient(
                        BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build(),
                    )
                    analysis.setAnalyzer(executor) { imageProxy ->
                        val mediaImage = imageProxy.image
                        if (mediaImage == null || delivered.get()) {
                            imageProxy.close()
                        } else {
                            scanner!!.process(InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees))
                                .addOnSuccessListener { codes ->
                                    val value = codes.firstOrNull()?.rawValue
                                    if (!value.isNullOrBlank() && delivered.compareAndSet(false, true)) {
                                        onResult(value)
                                    }
                                }
                                .addOnCompleteListener { imageProxy.close() }
                        }
                    }
                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                }.onFailure {
                    onUnavailable()
                }
            }, ContextCompat.getMainExecutor(context))
            onDispose {
                runCatching { cameraProvider?.unbindAll() }
                runCatching { scanner?.close() }
                executor.shutdown()
            }
        }
    }
    Box(modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
    }
}
