package app.openbubbles.nativeapp.ui.settings

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.openbubbles.nativeapp.data.AppGraph
import app.openbubbles.nativeapp.data.PushStateHolder
import app.openbubbles.nativeapp.ui.common.formatBytes
import app.openbubbles.nativeapp.ui.theme.OpenBubblesTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uniffi.rust_lib_bluebubbles.URegisterState

/** One-shot connection snapshot for the Connection section. */
private data class ConnectionInfo(
    val regstate: String,
    val handles: List<String>,
)

private fun describeRegstate(state: URegisterState): String = when (state) {
    is URegisterState.Registered ->
        if (state.nextS > 0) "Registered — next check-in in ${state.nextS}s" else "Registered"
    URegisterState.Registering -> "Registering…"
    is URegisterState.Failed -> "Failed: ${state.error}"
}

/**
 * Settings (M2 "lite"): connection health (registration state + handles via
 * the live Rust push state), an appearance placeholder, attachment storage
 * maintenance, and an about row. Sign out is a stub pending the M3 teardown
 * flow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pushState by PushStateHolder.stateFlow.collectAsStateWithLifecycle()

    // Connection details come from the live Rust state (blocking calls; IO).
    val connection by produceState<ConnectionInfo?>(initialValue = null, pushState) {
        val live = pushState ?: return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching {
                ConnectionInfo(describeRegstate(live.getRegstate()), live.getHandles())
            }.getOrNull()
        }
    }

    var cacheBytes by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(Unit) {
        cacheBytes = withContext(Dispatchers.IO) { AppGraph.attachmentsCacheBytes() }
    }

    val versionName = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull()
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionCard(title = "Connection") {
                if (pushState == null) {
                    SettingRow(
                        title = "Not connected",
                        supporting = "Sign in from the banner on the chat list",
                    )
                } else {
                    SettingRow(
                        title = "Registration",
                        supporting = connection?.regstate ?: "Checking…",
                    )
                    SettingRow(
                        title = "Handles",
                        supporting = connection?.handles?.joinToString("\n") ?: "Checking…",
                        multiline = true,
                    )
                    TextButton(
                        // TODO(M3): tear down the Rust state + keystore and
                        // route back to the login flow.
                        onClick = {},
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        Text("Sign out")
                    }
                }
            }

            SectionCard(title = "Appearance") {
                SettingRow(
                    title = "Theme",
                    supporting = "Coming soon — M3 theming milestone",
                )
            }

            SectionCard(title = "Storage") {
                SettingRow(
                    title = "Attachments on disk",
                    supporting = cacheBytes
                        ?.let { formatBytes(it).ifEmpty { "Empty" } }
                        ?: "Calculating…",
                )
                Button(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) { AppGraph.clearAttachmentCache() }
                            cacheBytes = withContext(Dispatchers.IO) { AppGraph.attachmentsCacheBytes() }
                        }
                    },
                    enabled = (cacheBytes ?: 0L) > 0L,
                ) {
                    Text("Clear attachment cache")
                }
            }

            SectionCard(title = "About") {
                SettingRow(
                    title = "OpenBubbles native",
                    supporting = "Version ${versionName ?: "unknown"}",
                )
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
        )
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                content()
            }
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    supporting: String,
    multiline: Boolean = false,
) {
    Column {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = supporting,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (multiline) 6 else 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// --------------------------------------------------------------------- previews

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SettingsScreenPreview() {
    OpenBubblesTheme {
        SettingsScreen(onBack = {})
    }
}
