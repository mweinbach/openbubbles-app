package app.openbubbles.nativeapp.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Space between titled preference cards. */
internal val SettingsGroupSpacing = 20.dp

/** Category rail on foldable / tablet. */
internal val SettingsListPaneWidth = 300.dp

/** Preference column — narrow enough to scan on an inner foldable display. */
internal val SettingsDetailMaxWidth = 520.dp

/** Single-column phone/compact cap. */
internal val SettingsSingleColumnMaxWidth = 600.dp

private val SettingsItemPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)

@Composable
private fun settingsItemColors(
    containerColor: Color = Color.Transparent,
    selectedContainerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
) = ListItemDefaults.colors(
    containerColor = containerColor,
    selectedContainerColor = selectedContainerColor,
)

/**
 * Rows inside a [SettingsGroup] card must not morph: the expressive default
 * animates corner radius on press/checked, which makes a row inside one
 * shared surface look like it is sliding or bulging. Flat shapes keep press
 * feedback to ripple + color, which is the correct state signal here.
 */
@Composable
private fun flatItemShapes() = ListItemDefaults.shapes(
    shape = RectangleShape,
    selectedShape = RectangleShape,
    pressedShape = RectangleShape,
    focusedShape = RectangleShape,
    hoveredShape = RectangleShape,
    draggedShape = RectangleShape,
)

/**
 * One Messages-style preference card: a single rounded surface, a section
 * label above it, and hairline dividers between rows so the group reads as
 * one object and tappable rows stay visually distinct from status rows.
 */
@Composable
internal fun SettingsGroup(
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            )
        }
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { isTraversalGroup = true },
        ) {
            Column(content = content)
        }
    }
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
    SettingsItemDivider(index)
    ListItem(
        modifier = Modifier.fillMaxWidth(),
        supportingContent = supportingContent(supporting, multiline),
        shapes = flatItemShapes(),
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
    SettingsItemDivider(index)
    ListItem(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled && !busy,
        supportingContent = supportingContent(supporting, multiline),
        trailingContent = {
            if (busy) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp),
                )
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = if (destructive) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        },
        shapes = flatItemShapes(),
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
    SettingsItemDivider(index)
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
        shapes = flatItemShapes(),
        colors = settingsItemColors(),
        contentPadding = SettingsItemPadding,
    ) {
        SettingsTitle(title)
    }
}

@Composable
internal fun SettingsCategoryItem(
    title: String,
    supporting: String,
    selected: Boolean,
    onClick: () -> Unit,
    index: Int,
    count: Int,
    showChevron: Boolean = false,
) {
    SettingsItemDivider(index)
    ListItem(
        selected = selected,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        supportingContent = {
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = if (showChevron) {
            {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            null
        },
        shapes = flatItemShapes(),
        colors = settingsItemColors(),
        contentPadding = SettingsItemPadding,
    ) {
        Text(
            text = title,
            style = if (selected) {
                MaterialTheme.typography.bodyLargeEmphasized
            } else {
                MaterialTheme.typography.bodyLarge
            },
        )
    }
}

@Composable
private fun SettingsItemDivider(index: Int) {
    if (index <= 0) return
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 20.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
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
