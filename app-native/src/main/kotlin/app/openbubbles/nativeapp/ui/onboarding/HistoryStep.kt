package app.openbubbles.nativeapp.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.openbubbles.nativeapp.data.HistorySyncWindow

/**
 * Step 5 — how much history to bring down, and the warning that comes with
 * it. Choosing here rather than in Settings matters: the first backfill is
 * the single heaviest thing the app ever does, and the amount the user picks
 * is what decides how long the device runs hot with the app locked.
 *
 * When iCloud data was not unlocked ([canDownload] false) there is nothing
 * to download yet, so the step degrades to a plain finish.
 */
@Composable
internal fun HistoryStep(
    canDownload: Boolean,
    initialWindow: HistorySyncWindow,
    onWindowChosen: (HistorySyncWindow) -> Unit,
    onStartDownload: () -> Unit,
    onSkip: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selected by remember { mutableStateOf(initialWindow) }

    Column(modifier = modifier.fillMaxSize()) {
        OnboardingTopBar(onBack = onBack, activeSegment = 4)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = OnboardingPadding),
        ) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (canDownload) "Bring your history over" else "You're all set",
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (canDownload) {
                    "Choose how far back to download. More history takes longer " +
                        "and uses more storage; you can always download more later."
                } else {
                    "New messages will arrive right away. To bring your past " +
                        "conversations over, unlock your iCloud data from " +
                        "Settings → iCloud whenever you're ready."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))

            if (canDownload) {
                HistorySyncWindow.entries.forEach { option ->
                    HistoryOption(
                        option = option,
                        selected = selected == option,
                        onSelect = { selected = option },
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Spacer(Modifier.height(12.dp))
                ExpectationRow(
                    icon = Icons.Filled.DeviceThermostat,
                    text = "Your phone may get warm and use battery while it downloads. " +
                        "Keep it plugged in if you can.",
                )
                Spacer(Modifier.height(10.dp))
                ExpectationRow(
                    icon = Icons.Filled.NotificationsOff,
                    text = "OpenGarden stays locked and quiet until it finishes, then " +
                        "sends you one notification.",
                )
                Spacer(Modifier.height(28.dp))
                Button(
                    onClick = {
                        onWindowChosen(selected)
                        onStartDownload()
                    },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                ) {
                    Text(
                        text = "Download my messages",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = onSkip,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Not now") }
            } else {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onSkip,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                ) {
                    Text(text = "Start messaging", style = MaterialTheme.typography.titleMedium)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun HistoryOption(
    option: HistorySyncWindow,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
        shape = MaterialTheme.shapes.largeIncreased,
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = null)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(text = option.title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = option.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ExpectationRow(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
    }
}
