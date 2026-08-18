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
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.separatingHorizontalHingeBounds
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import app.openbubbles.nativeapp.ui.adaptive.qrTabletopSplit
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
    var cameraAvailable by remember { mutableStateOf(true) }
    val delivered = remember { AtomicBoolean(false) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val mainExecutor = remember { ContextCompat.getMainExecutor(context) }
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
            // Preview stays enabled with PreviewView; keep the default
            // IMAGE_CAPTURE | IMAGE_ANALYSIS set so the finder is not a
            // black surface. Analysis is the only use case we consume.
            imageAnalysisBackpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
        }
    }

    val deliver: (ByteArray?, String?) -> Unit = { bytes, text ->
        qrScanPayload(bytes, text)?.let { (payloadBytes, payloadText) ->
            if (delivered.compareAndSet(false, true)) {
                mainExecutor.execute {
                    runCatching {
                        context.getSystemService(Vibrator::class.java)?.vibrate(
                            VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE),
                        )
                    }
                    onResult(payloadBytes, payloadText)
                }
            }
        }
    }

    DisposableEffect(hasPermission, lifecycleOwner) {
        if (hasPermission) {
            cameraController.setImageAnalysisAnalyzer(
                executor,
                qrMlKitAnalyzer(scanner, executor, deliver),
            )
            cameraAvailable = runCatching {
                cameraController.bindToLifecycle(lifecycleOwner)
            }.isSuccess
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

    LaunchedEffect(torchOn, hasPermission, cameraAvailable) {
        if (hasPermission && cameraAvailable) {
            runCatching { cameraController.enableTorch(torchOn) }
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
        } else if (!cameraAvailable) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "The camera could not be started. Close this screen and try again, or enter the pairing code another way.",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.size(16.dp))
                TextButton(onClick = onClose) { Text("Go back") }
            }
        } else {
            val posture = currentWindowAdaptiveInfoV2().windowPosture
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val density = LocalDensity.current
                val windowHeightPx = with(density) { maxHeight.roundToPx() }
                val hinge = posture.separatingHorizontalHingeBounds.firstOrNull()
                val split = if (posture.isTabletop && hinge != null) {
                    qrTabletopSplit(
                        windowHeightPx = windowHeightPx,
                        hingeTopPx = hinge.top.toInt(),
                        hingeBottomPx = hinge.bottom.toInt(),
                    )
                } else {
                    null
                }
                val preview: @Composable (Modifier) -> Unit = { previewModifier ->
                    AndroidView(
                        modifier = previewModifier,
                        factory = { ctx ->
                            PreviewView(ctx).apply {
                                controller = cameraController
                                scaleType = PreviewView.ScaleType.FILL_CENTER
                            }
                        },
                    )
                }
                if (split == null) {
                    preview(Modifier.fillMaxSize())
                    ScanOverlay(
                        torchOn = torchOn,
                        onToggleTorch = { torchOn = !torchOn },
                        onClose = onClose,
                    )
                } else {
                    Column(Modifier.fillMaxSize()) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(with(density) { split.viewfinderHeightPx.toDp() }),
                        ) {
                            preview(Modifier.fillMaxSize())
                            ScanFinder()
                        }
                        Spacer(
                            Modifier.height(with(density) { split.hingeHeightPx.toDp() }),
                        )
                        ScanControls(
                            torchOn = torchOn,
                            onToggleTorch = { torchOn = !torchOn },
                            onClose = onClose,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        )
                    }
                }
            }
        }
    }
}

internal fun qrMlKitAnalyzer(
    scanner: BarcodeScanner,
    executor: java.util.concurrent.Executor,
    onBarcode: (ByteArray?, String?) -> Unit,
): MlKitAnalyzer = MlKitAnalyzer(
    listOf(scanner),
    ImageAnalysis.COORDINATE_SYSTEM_ORIGINAL,
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
        ScanFinder()
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

/** Viewfinder scrim + corner brackets. Lives above a tabletop hinge. */
@Composable
private fun ScanFinder(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize()) {
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
    }
}

/** Close, torch, and caption — below a tabletop hinge. */
@Composable
private fun ScanControls(
    torchOn: Boolean,
    onToggleTorch: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Text(
            text = "Scan the pairing code on your Mac",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onClose,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = Color.Black.copy(alpha = 0.55f),
                    contentColor = Color.White,
                ),
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Close scanner")
            }
            IconButton(
                onClick = onToggleTorch,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = Color.Black.copy(alpha = 0.55f),
                    contentColor = Color.White,
                ),
            ) {
                Icon(
                    if (torchOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                    contentDescription = "Toggle torch",
                )
            }
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
