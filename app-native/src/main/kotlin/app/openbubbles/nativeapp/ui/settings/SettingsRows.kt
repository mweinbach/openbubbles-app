package app.openbubbles.nativeapp.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.openbubbles.nativeapp.ui.theme.defaultEffectsSpec

/** Space between titled preference groups. */
internal val SettingsGroupSpacing = 22.dp

/**
 * One row of a [SettingsGroup], invoked with its segmented position. Groups
 * build a list of these and derive `count` from the list size, so a row added
 * or removed can never leave a stale hardcoded count behind.
 */
internal typealias SettingsRowContent = @Composable (index: Int, count: Int) -> Unit

/** Category rail on foldable / tablet. */
internal val SettingsListPaneWidth = 300.dp

/** Preference column — narrow enough to scan on an inner foldable display. */
internal val SettingsDetailMaxWidth = 520.dp

/** Single-column phone/compact cap. */
internal val SettingsSingleColumnMaxWidth = 600.dp

private val SettingsItemPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)

/**
 * What the leading icon chip of a settings row communicates. Neutral is the
 * default wayfinding chip; Active marks an enabled or healthy state; Error
 * marks a problem or a destructive action. Meaning comes from the theme role,
 * never a hardcoded hue, so it survives dynamic color.
 */
internal enum class SettingsRowTone { Neutral, Active, Error }

@Composable
private fun SettingsRowIcon(icon: ImageVector, tone: SettingsRowTone) {
    val (targetContainer, targetContent) = when (tone) {
        SettingsRowTone.Neutral ->
            MaterialTheme.colorScheme.secondaryContainer to
                MaterialTheme.colorScheme.onSecondaryContainer
        SettingsRowTone.Active ->
            MaterialTheme.colorScheme.primaryContainer to
                MaterialTheme.colorScheme.onPrimaryContainer
        SettingsRowTone.Error ->
            MaterialTheme.colorScheme.errorContainer to
                MaterialTheme.colorScheme.onErrorContainer
    }
    val container by animateColorAsState(
        targetValue = targetContainer,
        animationSpec = defaultEffectsSpec(),
        label = "settingsIconContainer",
    )
    val content by animateColorAsState(
        targetValue = targetContent,
        animationSpec = defaultEffectsSpec(),
        label = "settingsIconContent",
    )
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(MaterialTheme.shapes.large)
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(21.dp),
        )
    }
}

@Composable
private fun settingsItemColors(
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    selectedContainerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
) = ListItemDefaults.segmentedColors(
    containerColor = containerColor,
    selectedContainerColor = selectedContainerColor,
    disabledContainerColor = containerColor,
)

@Composable
private fun settingsItemShapes(index: Int, count: Int, preserveSelectedShape: Boolean = false) =
    ListItemDefaults.segmentedShapes(
        index = index,
        count = count,
        defaultShapes = ListItemDefaults.shapes(
            shape = MaterialTheme.shapes.extraSmall,
            selectedShape = if (preserveSelectedShape) {
                MaterialTheme.shapes.extraSmall
            } else {
                MaterialTheme.shapes.extraLargeIncreased
            },
            pressedShape = MaterialTheme.shapes.extraLargeIncreased,
            focusedShape = MaterialTheme.shapes.large,
            hoveredShape = MaterialTheme.shapes.large,
            draggedShape = MaterialTheme.shapes.extraLargeIncreased,
        ),
    )

/**
 * One preference group: a section label above a segmented list. Each row is
 * its own container with rounded outer corners, near-square inner corners,
 * and the canonical 2dp gap, so the group reads as one object while every
 * row stays a visibly distinct tappable target. Press state morphs the row's
 * corners — correct here because rows no longer share one surface — and the
 * theme's motion scheme collapses that morph when the user removed
 * animations.
 */
@Composable
internal fun SettingsGroup(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { isTraversalGroup = true },
    ) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(start = 18.dp, end = 16.dp, bottom = 9.dp)
                    .semantics { heading() },
            )
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            content = content,
        )
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
    icon: ImageVector? = null,
    tone: SettingsRowTone = SettingsRowTone.Neutral,
) {
    ListItem(
        modifier = Modifier.fillMaxWidth(),
        leadingContent = leadingContent(icon, tone),
        supportingContent = supportingContent(supporting, multiline),
        shapes = settingsItemShapes(index = index, count = count),
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
    icon: ImageVector? = null,
    iconTone: SettingsRowTone? = null,
) {
    val tone = iconTone ?: if (destructive) SettingsRowTone.Error else SettingsRowTone.Neutral
    val titleColor = if (destructive) MaterialTheme.colorScheme.error else Color.Unspecified
    ListItem(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled && !busy,
        leadingContent = leadingContent(icon, tone),
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
                    modifier = Modifier.size(20.dp),
                )
            }
        },
        shapes = settingsItemShapes(index = index, count = count),
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
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    ListItem(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        leadingContent = leadingContent(
            icon,
            if (checked) SettingsRowTone.Active else SettingsRowTone.Neutral,
        ),
        supportingContent = supportingContent(supporting, multiline = false),
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled,
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
        // The switch is the state carrier, so the row itself must not change
        // when checked: selectedShape mirrors the resting shape (no swell)
        // and the selected container matches every other row. Without this a
        // checked row highlights secondaryContainer, which also camouflages
        // the neutral icon chip painted on top of it.
        shapes = settingsItemShapes(
            index = index,
            count = count,
            preserveSelectedShape = true,
        ),
        colors = settingsItemColors(
            selectedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        contentPadding = SettingsItemPadding,
    ) {
        SettingsTitle(title)
    }
}

@Composable
internal fun SettingsChoiceItem(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    index: Int,
    count: Int,
    supporting: String? = null,
) {
    ListItem(
        selected = selected,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        supportingContent = supportingContent(supporting, multiline = false),
        trailingContent = if (selected) {
            {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        } else {
            null
        },
        shapes = settingsItemShapes(index = index, count = count),
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
internal fun SettingsCategoryItem(
    title: String,
    supporting: String,
    selected: Boolean,
    onClick: () -> Unit,
    index: Int,
    count: Int,
    icon: ImageVector? = null,
    showChevron: Boolean = false,
) {
    ListItem(
        selected = selected,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        leadingContent = icon?.let { imageVector ->
            {
                SettingsRowIcon(
                    icon = imageVector,
                    tone = if (selected) SettingsRowTone.Active else SettingsRowTone.Neutral,
                )
            }
        },
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
        shapes = settingsItemShapes(index = index, count = count),
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
private fun SettingsTitle(title: String, color: Color = Color.Unspecified) {
    Text(
        text = title,
        style = MaterialTheme.typography.bodyLarge,
        color = color,
    )
}

private fun leadingContent(
    icon: ImageVector?,
    tone: SettingsRowTone,
): (@Composable () -> Unit)? {
    if (icon == null) return null
    return { SettingsRowIcon(icon, tone) }
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
