package app.openbubbles.nativeapp.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Space between preference groups — the Messages-style card gap. */
internal val SettingsGroupSpacing = 16.dp

/** Preference-row inset: a bit taller than the default so foldable rows breathe. */
private val SettingsItemPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp)

@Composable
internal fun settingsItemColors() = ListItemDefaults.colors(
    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
)

@Composable
internal fun settingsItemShapes(index: Int, count: Int) =
    ListItemDefaults.segmentedShapes(index, count)

/**
 * One Messages-style preference group: consecutive expressive [ListItem]s
 * with segmented corners so the group reads as a single object.
 */
@Composable
internal fun SettingsGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { isTraversalGroup = true },
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        content = content,
    )
}

@Composable
internal fun SettingsInfoItem(
    title: String,
    index: Int,
    count: Int,
    supporting: String? = null,
    multiline: Boolean = false,
    titleColor: Color = Color.Unspecified,
) {
    ListItem(
        modifier = Modifier.fillMaxWidth(),
        supportingContent = supportingContent(supporting, multiline),
        shapes = settingsItemShapes(index, count),
        colors = settingsItemColors(),
        contentPadding = SettingsItemPadding,
    ) {
        SettingsTitle(title, titleColor)
    }
}

@Composable
internal fun SettingsActionItem(
    title: String,
    onClick: () -> Unit,
    index: Int,
    count: Int,
    supporting: String? = null,
    enabled: Boolean = true,
    destructive: Boolean = false,
    busy: Boolean = false,
    multiline: Boolean = false,
) {
    val titleColor = if (destructive) MaterialTheme.colorScheme.error else Color.Unspecified
    ListItem(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled && !busy,
        supportingContent = supportingContent(supporting, multiline),
        trailingContent = if (busy) {
            {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp),
                )
            }
        } else {
            null
        },
        shapes = settingsItemShapes(index, count),
        colors = settingsItemColors(),
        contentPadding = SettingsItemPadding,
    ) {
        SettingsTitle(title, titleColor)
    }
}

@Composable
internal fun SettingsToggleItem(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    index: Int,
    count: Int,
    supporting: String? = null,
) {
    ListItem(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = Modifier.fillMaxWidth(),
        supportingContent = supportingContent(supporting, multiline = false),
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                thumbContent = if (checked) {
                    {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(SwitchDefaults.IconSize),
                        )
                    }
                } else {
                    null
                },
            )
        },
        shapes = settingsItemShapes(index, count),
        colors = settingsItemColors(),
        contentPadding = SettingsItemPadding,
    ) {
        SettingsTitle(title)
    }
}

@Composable
private fun SettingsTitle(title: String, color: Color = Color.Unspecified) {
    Text(
        text = title,
        style = MaterialTheme.typography.bodyLarge,
        color = color,
    )
}

private fun supportingContent(
    supporting: String?,
    multiline: Boolean,
): (@Composable () -> Unit)? {
    if (supporting.isNullOrEmpty()) return null
    return {
        Text(
            text = supporting,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (multiline) 6 else 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
