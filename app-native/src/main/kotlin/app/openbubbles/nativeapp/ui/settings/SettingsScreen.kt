package app.openbubbles.nativeapp.ui.settings

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.ManageHistory
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.separatingVerticalHingeBounds
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.openbubbles.nativeapp.data.AppGraph
import app.openbubbles.nativeapp.data.PushStateHolder
import app.openbubbles.nativeapp.ui.adaptive.settingsTwoPaneSplit
import app.openbubbles.nativeapp.ui.theme.OpenBubblesTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One extracted settings area: `groups` emits its preference groups into the
 * settings column (honoring the two-pane section filter), `dialogs` mounts
 * its dialogs unconditionally at screen level. Both close over state owned
 * by the section's `remember*Section` composable, which the screen calls
 * unconditionally so state and running work survive rail-section switches
 * and pane-layout changes.
 */
internal class SettingsSectionSlice(
    val groups: @Composable (filter: SettingsSection?, showTitles: Boolean) -> Unit,
    val dialogs: @Composable () -> Unit,
)

internal enum class SettingsSection(
    val title: String,
    val supporting: String,
    val icon: ImageVector,
) {
    Account("Account", "Recovery, profile, sign out", Icons.Filled.AccountCircle),
    ICloud("iCloud", "History, Keychain, contacts", Icons.Filled.Cloud),
    Notifications("Notifications", "Previews, replies, reactions", Icons.Filled.Notifications),
    Messaging("Messaging", "Sending address, archived chats, SMS", Icons.AutoMirrored.Filled.Chat),
    Power("Power", "Battery saver", Icons.Filled.PowerSettingsNew),
    Appearance("Appearance", "Theme and color", Icons.Filled.Palette),
    Storage("Storage & backup", "Attachments and local backup", Icons.Filled.Storage),
    Diagnostics("Diagnostics", "Logs, troubleshoot, iMessage stats", Icons.Filled.ManageHistory),
    About("About", "App version", Icons.Filled.Info),
}

/**
 * Settings: titled groups of segmented rows (one container per row, 2dp
 * gaps, tonal icon chips up front, chevrons on actions, switches on
 * toggles) so every setting is scannable and its affordance is visible.
 * Compact is a single column; medium+ is a category rail plus a narrow
 * detail column.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenFindMy: () -> Unit = {},
    onOpenArchived: () -> Unit = {},
    onOpenRecentlyDeleted: () -> Unit = {},
    onOpenPasswords: () -> Unit = {},
    onOpenSharedAlbums: () -> Unit = {},
    onOpenSignIn: () -> Unit = {},
    archivedCount: Int = 0,
    recentlyDeletedCount: Int = 0,
    showBackButton: Boolean = true,
    windowAdaptiveInfo: WindowAdaptiveInfo = currentWindowAdaptiveInfoV2(),
) {
    val pushState by PushStateHolder.stateFlow.collectAsStateWithLifecycle()
    val accountSection = rememberAccountSection(
        onOpenSignIn = onOpenSignIn,
        onBack = onBack,
    )

    var cliqueRefresh by remember { mutableIntStateOf(0) }
    var inClique by remember(pushState) { mutableStateOf<Boolean?>(null) }
    var cliqueError by remember(pushState) { mutableStateOf<String?>(null) }
    LaunchedEffect(pushState, cliqueRefresh) {
        val live = pushState
        if (live == null) {
            inClique = null
            cliqueError = null
        } else {
            val result = withContext(Dispatchers.IO) { runCatching { live.isInClique() } }
            result.onSuccess {
                inClique = it
                cliqueError = null
            }.onFailure {
                inClique = false
                cliqueError = it.message ?: "Unable to check iCloud Keychain"
            }
        }
    }

    val icloudSection = rememberICloudSection(
        inClique = inClique,
        cliqueError = cliqueError,
        onCliqueJoined = {
            inClique = true
            cliqueRefresh += 1
        },
        onOpenSignIn = onOpenSignIn,
        onOpenPasswords = onOpenPasswords,
        onOpenSharedAlbums = onOpenSharedAlbums,
    )

    var cacheBytes by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(Unit) {
        cacheBytes = withContext(Dispatchers.IO) { AppGraph.attachmentsCacheBytes() }
    }

    val appearanceStorageSection = rememberAppearanceStorageSection(
        cacheBytes = cacheBytes,
        onCacheBytes = { cacheBytes = it },
    )

    val diagnosticsAboutSection = rememberDiagnosticsAboutSection(
        inClique = inClique,
        cacheBytes = cacheBytes,
    )

    val messagingSection = rememberMessagingSection(
        archivedCount = archivedCount,
        recentlyDeletedCount = recentlyDeletedCount,
        onOpenArchived = onOpenArchived,
        onOpenRecentlyDeleted = onOpenRecentlyDeleted,
    )

    // Compact: collapsing headline. Medium+ (foldable inner, tablet): the
    // Messages pattern — title sits next to back so the list gets the height.
    val windowSizeClass = windowAdaptiveInfo.windowSizeClass
    val isMediumWidth = windowSizeClass.isWidthAtLeastBreakpoint(
        WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND,
    )
    val density = LocalDensity.current
    val hinge = windowAdaptiveInfo.windowPosture.separatingVerticalHingeBounds.firstOrNull()
    val twoPane = settingsTwoPaneSplit(
        hingeLeftDp = hinge?.let { with(density) { it.left.toDp().value } },
        hingeRightDp = hinge?.let { with(density) { it.right.toDp().value } },
        defaultListWidthDp = SettingsListPaneWidth.value,
    )
    val scrollBehavior = if (isMediumWidth) {
        TopAppBarDefaults.pinnedScrollBehavior()
    } else {
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    }
    val barColors = TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.surface,
        scrolledContainerColor = MaterialTheme.colorScheme.surface,
    )
    val navigationIcon: @Composable () -> Unit = {
        if (showBackButton) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        }
    }
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            if (isMediumWidth) {
                TopAppBar(
                    title = { Text("Settings") },
                    navigationIcon = navigationIcon,
                    colors = barColors,
                    scrollBehavior = scrollBehavior,
                )
            } else {
                MediumFlexibleTopAppBar(
                    title = { Text("Settings") },
                    scrollBehavior = scrollBehavior,
                    colors = barColors,
                    navigationIcon = navigationIcon,
                )
            }
        },
    ) { padding ->
        var selectedSectionName by rememberSaveable {
            mutableStateOf(SettingsSection.Account.name)
        }
        val selectedSection = SettingsSection.valueOf(selectedSectionName)

        @Composable
        fun SectionColumn(
            filter: SettingsSection?,
            showTitles: Boolean,
            modifier: Modifier = Modifier,
        ) {
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(SettingsGroupSpacing),
            ) {
                accountSection.groups(filter, showTitles)

                icloudSection.groups(filter, showTitles)

                messagingSection.groups(filter, showTitles)

                if (filter == null) SettingsGroup(
                    title = if (showTitles) "Location" else null,
                ) {
                    SettingsActionItem(
                        title = "Find My",
                        supporting = "Devices, friends and items",
                        onClick = onOpenFindMy,
                        index = 0,
                        count = 1,
                        icon = Icons.Filled.LocationOn,
                    )
                }

                appearanceStorageSection.groups(filter, showTitles)

                diagnosticsAboutSection.groups(filter, showTitles)
            }
        }

        if (isMediumWidth) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(twoPane.gutterDp.dp),
            ) {
                Column(
                    modifier = Modifier
                        .width(twoPane.listWidthDp.dp)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding(),
                ) {
                    SettingsGroup {
                        val sections = SettingsSection.entries
                        sections.forEachIndexed { index, section ->
                            SettingsCategoryItem(
                                title = section.title,
                                supporting = section.supporting,
                                selected = section == selectedSection,
                                onClick = { selectedSectionName = section.name },
                                index = index,
                                count = sections.size + 1,
                                icon = section.icon,
                            )
                        }
                        SettingsCategoryItem(
                            title = "Find My",
                            supporting = "Devices, friends and items",
                            selected = false,
                            onClick = onOpenFindMy,
                            index = sections.size,
                            count = sections.size + 1,
                            icon = Icons.Filled.LocationOn,
                            showChevron = true,
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .widthIn(max = SettingsDetailMaxWidth)
                        .fillMaxHeight()
                        .weight(1f, fill = false),
                ) {
                    Text(
                        text = selectedSection.title,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(start = 4.dp, bottom = 12.dp),
                    )
                    SectionColumn(
                        filter = selectedSection,
                        showTitles = false,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                SectionColumn(
                    filter = null,
                    showTitles = true,
                    modifier = Modifier
                        .widthIn(max = SettingsSingleColumnMaxWidth)
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }

    accountSection.dialogs()

    icloudSection.dialogs()

    messagingSection.dialogs()

    appearanceStorageSection.dialogs()

    diagnosticsAboutSection.dialogs()
}

// --------------------------------------------------------------------- previews

@Preview(name = "phone", device = Devices.PHONE, showBackground = true)
@Preview(name = "foldable", device = Devices.FOLDABLE, showBackground = true)
@Preview(name = "tablet", device = Devices.TABLET, showBackground = true)
@Preview(name = "dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SettingsScreenPreview() {
    OpenBubblesTheme(dynamicColor = false) {
        SettingsScreen(onBack = {})
    }
}
