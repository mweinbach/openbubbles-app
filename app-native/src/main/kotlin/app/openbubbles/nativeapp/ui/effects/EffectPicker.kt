package app.openbubbles.nativeapp.ui.effects

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.openbubbles.nativeapp.ui.theme.OpenBubblesTheme

/**
 * Bottom sheet listing the iMessage send effects (long-press the send button
 * to open). Picking one stages it as the "pending effect" for the next send.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendEffectPickerSheet(
    onPick: (SendEffectOption) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = "Send with effect",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
        )
        SendEffectCatalog.options.chunked(2).forEach { rowOptions ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowOptions.forEach { option ->
                    EffectOptionCard(
                        option = option,
                        onClick = { onPick(option) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (rowOptions.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(24.dp)) // bottom sheet inset
    }
}

@Composable
private fun EffectOptionCard(
    option: SendEffectOption,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(text = option.icon, fontSize = 20.sp)
            Text(text = option.label, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * Chip shown above the input while an effect is staged for the next send;
 * tapping clears it.
 */
@Composable
fun PendingEffectChip(
    option: SendEffectOption,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = modifier.clickable(onClick = onClear),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(text = option.icon, fontSize = 15.sp)
            Text(
                text = option.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.width(2.dp))
            Text(
                text = "✕",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

// --------------------------------------------------------------------- previews

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PendingEffectChipPreview() {
    OpenBubblesTheme {
        Row(modifier = Modifier.padding(16.dp)) {
            PendingEffectChip(
                option = SendEffectCatalog.byId("com.apple.messages.effect.CKConfettiEffect")
                    ?: SendEffectCatalog.options.first(),
                onClear = {},
            )
        }
    }
}
