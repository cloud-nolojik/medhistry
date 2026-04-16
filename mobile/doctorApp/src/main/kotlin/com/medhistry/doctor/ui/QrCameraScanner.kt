package com.medhistry.doctor.ui

import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * CameraX + ML Kit QR code scanner composable.
 *
 * Renders a live camera preview and scans for QR codes. Calls [onDetected]
 * exactly once when the first QR is decoded — the caller is responsible for
 * navigating away (or unmounting this composable) before more frames arrive.
 * We still guard with an [AtomicBoolean] so repeated calls are swallowed
 * even if the composable lingers for a tick after the first detection.
 *
 * The preview uses the back camera and is auto-bound to the current
 * [LocalLifecycleOwner]; CameraX releases everything on dispose.
 *
 * Caller is expected to have already obtained android.permission.CAMERA —
 * this composable does not request it. If the permission is missing CameraX
 * will throw at bind-time and we surface nothing useful; DoctorScanScreen
 * handles the permission gate before instantiating us.
 */
@OptIn(ExperimentalGetImage::class)
@Composable
fun QrCameraScanner(
    onDetected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // Single-fire guard: once a QR is decoded we stop forwarding further
    // detections upstream. Prevents the analyzer from firing onDetected
    // repeatedly in the brief window between detection and this composable
    // being removed by the caller.
    val detected = remember { AtomicBoolean(false) }
    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }
    val barcodeScanner: BarcodeScanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
        )
    }
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    DisposableEffect(lifecycleOwner, previewView) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        val listener = Runnable {
            val provider = try {
                providerFuture.get()
            } catch (e: Exception) {
                Log.e("QrCameraScanner", "Could not obtain camera provider", e)
                return@Runnable
            }

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { ia ->
                    ia.setAnalyzer(analyzerExecutor) { proxy ->
                        processImageProxy(proxy, barcodeScanner, detected, onDetected)
                    }
                }

            try {
                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis,
                )
            } catch (e: Exception) {
                Log.e("QrCameraScanner", "Camera bind failed", e)
            }
        }
        providerFuture.addListener(listener, ContextCompat.getMainExecutor(context))

        onDispose {
            try {
                providerFuture.get().unbindAll()
            } catch (_: Exception) {
                // provider may not be ready yet; unbind is safe to skip.
            }
            barcodeScanner.close()
            analyzerExecutor.shutdown()
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier.fillMaxSize(),
    )
}

@OptIn(ExperimentalGetImage::class)
private fun processImageProxy(
    proxy: ImageProxy,
    scanner: BarcodeScanner,
    detected: AtomicBoolean,
    onDetected: (String) -> Unit,
) {
    // If we already reported a detection, drain frames without spending
    // CPU on ML Kit decoding — just close the proxy so the pipeline keeps
    // flowing until CameraX is unbound.
    if (detected.get()) {
        proxy.close()
        return
    }

    val mediaImage = proxy.image
    if (mediaImage == null) {
        proxy.close()
        return
    }

    val input = InputImage.fromMediaImage(mediaImage, proxy.imageInfo.rotationDegrees)
    scanner.process(input)
        .addOnSuccessListener { barcodes ->
            val value = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue
            if (value != null && detected.compareAndSet(false, true)) {
                onDetected(value)
            }
        }
        .addOnFailureListener { e ->
            Log.w("QrCameraScanner", "Barcode decode failed", e)
        }
        .addOnCompleteListener {
            proxy.close()
        }
}
