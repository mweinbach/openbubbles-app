package app.openbubbles.nativeapp.ui.login

import android.Manifest
import android.content.pm.PackageManager
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Full-screen QR pairing scanner for the provisioning flow. Reports the
 * first detected QR exactly once — binary payloads (the `OABS…` Mac pairing
 * format) via [onResult]'s bytes, text payloads (relay URL/code, raw hex)
 * via the string; either may be null, never both.
 *
 * Uses CameraX [LifecycleCameraController] + [MlKitAnalyzer], the official
 * CameraX ↔ ML Kit integration, so CameraX owns frame delivery, backpressure,
 * and analyzer lifecycle instead of a hand-rolled [ImageAnalysis] loop.
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
    val delivered = remember { AtomicBoolean(false) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build(),
        )
    }
    val cameraController = remember {
        LifecycleCameraController(context).apply {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
            imageAnalysisBackpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
        }
    }

    val deliver: (ByteArray?, String?) -> Unit = { bytes, text ->
        qrScanPayload(bytes, text)?.let { (payloadBytes, payloadText) ->
            if (delivered.compareAndSet(false, true)) {
                runCatching {
                    context.getSystemService(Vibrator::class.java)?.vibrate(
                        VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE),
                    )
                }
                onResult(payloadBytes, payloadText)
            }
        }
    }

    DisposableEffect(hasPermission, lifecycleOwner) {
        if (hasPermission) {
            cameraController.setImageAnalysisAnalyzer(
                executor,
                qrMlKitAnalyzer(scanner, executor, deliver),
            )
            cameraController.bindToLifecycle(lifecycleOwner)
        }
        onDispose {
            cameraController.clearImageAnalysisAnalyzer()
            cameraController.unbind()
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            runCatching { executor.shutdown() }
            scanner.close()
        }
    }

    LaunchedEffect(torchOn, hasPermission) {
        if (hasPermission) {
            cameraController.enableTorch(torchOn)
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
                Spacer(Modifier.size(16.dp))
                TextButton(onClick = onClose) { Text("Go back") }
            }
        } else {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        controller = cameraController
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }
                },
            )

            ScanOverlay(
                torchOn = torchOn,
                onToggleTorch = { torchOn = !torchOn },
                onClose = onClose,
            )
        }
    }
}

internal fun qrMlKitAnalyzer(
    scanner: BarcodeScanner,
    executor: java.util.concurrent.Executor,
    onBarcode: (ByteArray?, String?) -> Unit,
): MlKitAnalyzer = MlKitAnalyzer(
    listOf(scanner),
    CameraController.COORDINATE_SYSTEM_VIEW_REFERENCED,
    executor,
) { result ->
    val barcodes = result?.getValue(scanner).orEmpty()
    barcodes.firstOrNull()?.let { code ->
        onBarcode(code.rawBytes, code.rawValue)
    }
}

/** First non-empty QR payload. Bytes and text may both be present. */
internal fun qrScanPayload(
    rawBytes: ByteArray?,
    rawValue: String?,
): Pair<ByteArray?, String?>? =
    if (rawBytes != null || rawValue != null) rawBytes to rawValue else null

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
                Size(side, side),
            )
            val holePath = Path().apply {
                addRoundRect(RoundRect(hole, cornerRadius = CornerRadius(28f, 28f)))
            }
            val fillPath = Path().apply {
                addRect(Rect(Offset.Zero, Size(size.width, size.height)))
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
