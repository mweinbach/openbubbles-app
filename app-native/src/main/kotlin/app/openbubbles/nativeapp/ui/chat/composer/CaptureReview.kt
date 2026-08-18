package app.openbubbles.nativeapp.ui.chat.composer

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.openbubbles.nativeapp.ui.common.rememberDecodedImage
import java.io.File

@Composable
fun CaptureReview(
    file: File,
    video: Boolean,
    onRetake: () -> Unit,
    onUse: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(if (video) "Review video" else "Review photo", style = MaterialTheme.typography.headlineSmall)
            Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                if (video) {
                    Icon(Icons.Filled.Videocam, contentDescription = null, modifier = Modifier.size(96.dp))
                    Text(file.name, modifier = Modifier.align(Alignment.BottomCenter))
                } else {
                    rememberDecodedImage(file, maxDimensionPx = 1800)?.image?.let { image ->
                        Image(image, contentDescription = "Captured photo", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.align(Alignment.End)) {
                OutlinedButton(onClick = onRetake) { Text("Retake") }
                Button(onClick = onUse) { Text("Use") }
            }
        }
    }
}
