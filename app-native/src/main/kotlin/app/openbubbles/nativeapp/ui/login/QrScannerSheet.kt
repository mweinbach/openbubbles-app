package app.openbubbles.nativeapp.ui.login

import android.Manifest
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Full-screen QR pairing scanner for the provisioning flow. Reports the
 * first detected QR exactly once — binary payloads (the `OABS…` Mac pairing
 * format) via [onResult]'s bytes, text payloads (relay URL/code, raw hex)
 * via the string; either may be null, never both.
 */
@Composable
fun QrScannerSheet(
    onResult: (bytes: ByteArray?, text: String?) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasPermission = granted }
    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    var torchOn by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    val delivered = remember { AtomicBoolean(false) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build(),
        )
    }
    DisposableEffect(Unit) {
        onDispose {
            runCatching { executor.shutdown() }
            scanner.close()
        }
    }

    val deliver: (ByteArray?, String?) -> Unit = { bytes, text ->
        if (bytes != null || text != null) {
            if (delivered.compareAndSet(false, true)) {
                runCatching {
                    context.getSystemService(Vibrator::class.java)?.vibrate(
                        VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE),
                    )
                }
                onResult(bytes, text)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (!hasPermission) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "Camera access is needed to scan the pairing code from your Mac.",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                )
                androidx.compose.foundation.layout.Spacer(Modifier.size(16.dp))
                TextButton(onClick = onClose) { Text("Go back") }
            }
        } else {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val providerFuture = ProcessCameraProvider.getInstance(ctx)
                    providerFuture.addListener({
                        val provider = providerFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }
                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                        analysis.setAnalyzer(executor) { proxy ->
                            processFrame(scanner, proxy, deliver)
                        }
                        runCatching {
                            provider.unbindAll()
                            camera = provider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                analysis,
                            )
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                },
            )

            ScanOverlay(
                torchOn = torchOn,
                onToggleTorch = {
                    torchOn = !torchOn
                    camera?.cameraControl?.enableTorch(torchOn)
                },
                onClose = onClose,
            )
        }
    }
}

@OptIn(ExperimentalGetImage::class)
private fun processFrame(
    scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    proxy: ImageProxy,
    onBarcode: (ByteArray?, String?) -> Unit,
) {
    val mediaImage = proxy.image
    if (mediaImage == null) {
        proxy.close()
        return
    }
    val input = InputImage.fromMediaImage(mediaImage, proxy.imageInfo.rotationDegrees)
    scanner.process(input)
        .addOnSuccessListener { barcodes ->
            barcodes.firstOrNull()?.let { code ->
                onBarcode(code.rawBytes, code.rawValue)
            }
        }
        .addOnCompleteListener { proxy.close() }
}

/** Scrim with a rounded-square cutout, corner brackets, torch + close controls. */
@Composable
private fun ScanOverlay(
    torchOn: Boolean,
    onToggleTorch: () -> Unit,
    onClose: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val side = size.width * 0.72f
            val hole = Rect(
                Offset((size.width - side) / 2f, (size.height - side) / 2f),
                androidx.compose.ui.geometry.Size(side, side),
            )
            val holePath = Path().apply { addRoundRect(RoundRect(hole, cornerRadius = androidx.compose.ui.geometry.CornerRadius(28f, 28f))) }
            val fillPath = Path().apply {
                addRect(Rect(Offset.Zero, androidx.compose.ui.geometry.Size(size.width, size.height)))
            }
            drawPath(
                path = Path.combine(PathOperation.Difference, fillPath, holePath),
                color = Color.Black.copy(alpha = 0.62f),
            )
        }
        CornerFrame(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.72f)
                .aspectRatio(1f),
        )
        Text(
            text = "Scan the pairing code on your Mac",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 72.dp),
        )
        IconButton(
            onClick = onClose,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = Color.Black.copy(alpha = 0.55f),
                contentColor = Color.White,
            ),
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(16.dp),
        ) {
            Icon(Icons.Filled.Close, contentDescription = "Close scanner")
        }
        IconButton(
            onClick = onToggleTorch,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = Color.Black.copy(alpha = 0.55f),
                contentColor = Color.White,
            ),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(16.dp),
        ) {
            Icon(
                if (torchOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                contentDescription = "Toggle torch",
            )
        }
    }
}

/** Rounded-square corner brackets marking the scan area. */
@Composable
private fun CornerFrame(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val bracket = 44f
        val stroke = 6f
        val w = size.width
        val h = size.height
        val i = 8f
        val color = Color.White
        val lines = listOf(
            Offset(i, i + bracket) to Offset(i, i),
            Offset(i, i) to Offset(i + bracket, i),
            Offset(w - i - bracket, i) to Offset(w - i, i),
            Offset(w - i, i) to Offset(w - i, i + bracket),
            Offset(w - i, h - i - bracket) to Offset(w - i, h - i),
            Offset(w - i, h - i) to Offset(w - i - bracket, h - i),
            Offset(i + bracket, h - i) to Offset(i, h - i),
            Offset(i, h - i) to Offset(i, h - i - bracket),
        )
        lines.forEach { (start, end) ->
            drawLine(color = color, start = start, end = end, strokeWidth = stroke, cap = StrokeCap.Round)
        }
    }
}
