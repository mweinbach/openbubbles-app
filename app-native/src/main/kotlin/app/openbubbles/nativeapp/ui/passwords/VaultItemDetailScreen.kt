package app.openbubbles.nativeapp.ui.passwords

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.openbubbles.nativeapp.ui.login.QrScannerSheet
import app.openbubbles.nativeapp.ui.settings.SettingsActionItem
import app.openbubbles.nativeapp.ui.settings.SettingsDetailMaxWidth
import app.openbubbles.nativeapp.ui.settings.SettingsGroup
import app.openbubbles.nativeapp.ui.settings.SettingsGroupSpacing
import app.openbubbles.nativeapp.ui.settings.SettingsInfoItem
import app.openbubbles.nativeapp.ui.settings.SettingsRowTone
import app.openbubbles.nativeapp.ui.theme.OpenBubblesTheme
import app.openbubbles.nativeapp.ui.tooling.LightDarkPreviews
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.delay

/** Standard TOTP rollover period; drives the countdown ring only. */
private const val CODE_PERIOD_SECONDS = 30f

private val DetailRowPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)

/**
 * One vault item as its own page, echoing the iOS Passwords detail layout:
 * a monogram tile and title up top, then one grouped card of label/value
 * rows. Secrets stay concealed until the caller-supplied authentication in
 * [onRequestReveal] succeeds; verification codes then show a rollover
 * countdown and re-reveal without another prompt via [onRefreshCode].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultItemDetailScreen(
    uiState: VaultItemDetailUiState,
    onBack: () -> Unit,
    onRequestReveal: () -> Unit,
    onRefreshCode: () -> Unit,
    onCopy: (String) -> Unit,
    onDelete: () -> Unit,
    onAddTotp: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val item = uiState.item
    var confirmDelete by remember(item.id) { mutableStateOf(false) }
    var showTotpSetup by remember(item.id) { mutableStateOf(false) }
    var totpInput by remember(item.id) { mutableStateOf("") }
    var scanningTotp by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.deleted) {
        if (uiState.deleted) onBack()
    }
    if (scanningTotp) {
        QrScannerSheet(
            onResult = { bytes, text ->
                scanningTotp = false
                val setup = text ?: bytes?.toString(Charsets.UTF_8)
                if (!setup.isNullOrBlank()) totpInput = setup
            },
            onClose = { scanningTotp = false },
            instruction = "Scan a verification-code QR",
        )
        return
    }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = SettingsDetailMaxWidth)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                VaultMonogram(
                    title = item.title,
                    size = 72.dp,
                    textStyle = MaterialTheme.typography.headlineMediumEmphasized,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                Spacer(Modifier.height(24.dp))

                if (item.category == VaultCategory.Codes) {
                    VerificationCodeSection(
                        uiState = uiState,
                        onRequestReveal = onRequestReveal,
                        onRefreshCode = onRefreshCode,
                        onCopy = onCopy,
                    )
                    Spacer(Modifier.height(SettingsGroupSpacing))
                }

                DetailRowsCard(
                    uiState = uiState,
                    onRequestReveal = onRequestReveal,
                    onCopy = onCopy,
                )

                if (item.category == VaultCategory.Passkeys) {
                    Spacer(Modifier.height(SettingsGroupSpacing))
                    SettingsGroup {
                        SettingsInfoItem(
                            title = "Passkey",
                            supporting = "The private key for this passkey stays protected " +
                                "and can't be viewed or copied.",
                            index = 0,
                            count = 1,
                            icon = Icons.Filled.Key,
                            multiline = true,
                        )
                    }
                }

                if (item.category == VaultCategory.Passwords && !item.username.isNullOrBlank()) {
                    Spacer(Modifier.height(SettingsGroupSpacing))
                    SettingsGroup {
                        SettingsActionItem(
                            title = "Set Up Verification Code",
                            supporting = "Scan a QR code or paste a 2FA setup key for this account",
                            onClick = {
                                totpInput = ""
                                showTotpSetup = true
                            },
                            index = 0,
                            count = 1,
                            icon = Icons.Filled.Security,
                            enabled = !uiState.busy,
                        )
                    }
                }

                Spacer(Modifier.height(SettingsGroupSpacing))
                SettingsGroup {
                    SettingsActionItem(
                        title = "Delete ${item.category.singular.replaceFirstChar { it.uppercase() }}",
                        onClick = { confirmDelete = true },
                        index = 0,
                        count = 1,
                        icon = Icons.Filled.Delete,
                        destructive = true,
                        busy = uiState.busy,
                    )
                }

                uiState.error?.let { error ->
                    Spacer(Modifier.height(SettingsGroupSpacing))
                    SettingsGroup {
                        SettingsInfoItem(
                            title = "iCloud Passwords",
                            supporting = error,
                            index = 0,
                            count = 1,
                            icon = Icons.Filled.ErrorOutline,
                            tone = SettingsRowTone.Error,
                            multiline = true,
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (confirmDelete) {
        val noun = item.category.singular
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${item.title}?") },
            text = {
                Text("Delete this $noun? It is removed from iCloud and every device signed in to this account.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                    enabled = !uiState.busy,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
    if (showTotpSetup) {
        TotpSetupSheet(
            item = item,
            setup = totpInput,
            busy = uiState.busy,
            onSetupChange = { totpInput = it },
            onScan = { scanningTotp = true },
            onDismiss = {
                showTotpSetup = false
                totpInput = ""
            },
            onSubmit = {
                onAddTotp(totpInput)
                showTotpSetup = false
                totpInput = ""
            },
        )
    }
}

/**
 * Verification-code setup. A sheet, not a dialog: it is a scan plus a long key to
 * paste, and a dialog gave the field two cramped lines above the keyboard.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TotpSetupSheet(
    item: VaultItemUi,
    setup: String,
    busy: Boolean,
    onSetupChange: (String) -> Unit,
    onScan: () -> Unit,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Set up verification code", style = MaterialTheme.typography.headlineSmall)
            Text(
                text = listOfNotNull(item.title, item.username?.takeIf { it.isNotBlank() })
                    .joinToString(" • "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FilledTonalButton(onClick = onScan, enabled = !busy) {
                Icon(Icons.Filled.QrCodeScanner, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Scan QR code")
            }
            OutlinedTextField(
                value = setup,
                onValueChange = onSetupChange,
                label = { Text("Setup key or otpauth URI") },
                supportingText = { Text("Paste the Base32 setup key if scanning is unavailable.") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(
                    onClick = onSubmit,
                    enabled = !busy && setup.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) { Text("Save") }
            }
        }
    }
}

/** The label/value card mirroring the iOS Passwords single detail card. */
@Composable
private fun DetailRowsCard(
    uiState: VaultItemDetailUiState,
    onRequestReveal: () -> Unit,
    onCopy: (String) -> Unit,
) {
    val item = uiState.item
    val rows = mutableListOf<@Composable (index: Int, count: Int) -> Unit>()

    item.username?.let { username ->
        rows += { index, count ->
            VaultDetailRow(
                label = "User Name",
                value = username,
                index = index,
                count = count,
                onClick = { onCopy(username) },
                copyable = true,
            )
        }
    }
    when (item.category) {
        VaultCategory.Passwords, VaultCategory.Wifi -> rows += { index, count ->
            VaultDetailRow(
                label = "Password",
                value = uiState.secret ?: "••••••••",
                index = index,
                count = count,
                onClick = {
                    val secret = uiState.secret
                    if (secret == null) onRequestReveal() else onCopy(secret)
                },
                copyable = uiState.secret != null,
                busy = uiState.busy,
            )
        }
        VaultCategory.Passkeys, VaultCategory.Codes, VaultCategory.Groups -> Unit
    }
    when (item.category) {
        VaultCategory.Passwords, VaultCategory.Passkeys -> rows += { index, count ->
            VaultDetailRow(
                label = "Website",
                value = item.title,
                index = index,
                count = count,
                onClick = { onCopy(item.title) },
                copyable = true,
            )
        }
        VaultCategory.Wifi -> rows += { index, count ->
            VaultDetailRow(label = "Network", value = item.title, index = index, count = count)
        }
        VaultCategory.Codes, VaultCategory.Groups -> Unit
    }
    rows += { index, count ->
        VaultDetailRow(
            label = "Group",
            value = uiState.groupName ?: if (item.groupId != null) "Shared" else "Personal",
            index = index,
            count = count,
        )
    }
    item.modifiedAtMs?.let { modifiedAtMs ->
        rows += { index, count ->
            VaultDetailRow(
                label = "Modified",
                value = remember(modifiedAtMs) {
                    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(modifiedAtMs))
                },
                index = index,
                count = count,
            )
        }
    }

    SettingsGroup {
        rows.forEachIndexed { index, row -> row(index, rows.size) }
    }
}

/**
 * Hero card for a 2FA code: the current code full-size with a rollover ring,
 * like the iOS verification-code detail. Until authenticated it is a single
 * reveal action row.
 */
@Composable
private fun VerificationCodeSection(
    uiState: VaultItemDetailUiState,
    onRequestReveal: () -> Unit,
    onRefreshCode: () -> Unit,
    onCopy: (String) -> Unit,
) {
    val code = uiState.secret
    if (code == null) {
        SettingsGroup {
            SettingsActionItem(
                title = "Show verification code",
                supporting = "Authenticate to reveal the current code",
                onClick = onRequestReveal,
                index = 0,
                count = 1,
                icon = Icons.Filled.Security,
                busy = uiState.busy,
            )
        }
        return
    }

    val expiry = uiState.secretExpiresAtSeconds
    var nowSeconds by remember { mutableLongStateOf(System.currentTimeMillis() / 1000L) }
    if (expiry != null) {
        // Tick down once per second, then fetch the next code exactly once per
        // expiry; a successful refresh changes the key and restarts the loop.
        LaunchedEffect(expiry) {
            while (true) {
                nowSeconds = System.currentTimeMillis() / 1000L
                if (nowSeconds >= expiry) break
                delay(1_000L)
            }
            onRefreshCode()
        }
    }
    val remaining = expiry?.let { (it - nowSeconds).coerceAtLeast(0L).toInt() }

    SettingsGroup {
        ListItem(
            onClick = { onCopy(code) },
            modifier = Modifier.fillMaxWidth(),
            supportingContent = {
                Text(
                    text = "Tap to copy",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            trailingContent = remaining?.let { seconds ->
                {
                    Box(
                        modifier = Modifier.size(44.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularWavyProgressIndicator(
                            progress = { (seconds / CODE_PERIOD_SECONDS).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxSize(),
                        )
                        Text(
                            text = seconds.toString(),
                            style = MaterialTheme.typography.labelMediumEmphasized,
                        )
                    }
                }
            },
            shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
            colors = ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        ) {
            Text(
                text = formatVerificationCode(code),
                style = MaterialTheme.typography.displaySmallEmphasized,
                maxLines = 1,
            )
        }
    }
}

/** iOS-style row: field label leading, muted value trailing, tap to copy. */
@Composable
private fun VaultDetailRow(
    label: String,
    value: String,
    index: Int,
    count: Int,
    onClick: (() -> Unit)? = null,
    copyable: Boolean = false,
    busy: Boolean = false,
) {
    val valueContent: @Composable () -> Unit = {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 240.dp),
            )
            if (busy) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            } else if (copyable) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = "Copy",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
    val shapes = ListItemDefaults.segmentedShapes(index = index, count = count)
    val colors = ListItemDefaults.colors(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    )
    if (onClick == null) {
        ListItem(
            modifier = Modifier.fillMaxWidth(),
            trailingContent = valueContent,
            shapes = shapes,
            colors = colors,
            contentPadding = DetailRowPadding,
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
        }
    } else {
        ListItem(
            onClick = onClick,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            trailingContent = valueContent,
            shapes = shapes,
            colors = colors,
            contentPadding = DetailRowPadding,
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/**
 * The item's identity tile: first glyph of the title on a rounded square,
 * standing in for the site/app icon the way iOS renders vault entries.
 */
@Composable
internal fun VaultMonogram(
    title: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    textStyle: TextStyle = MaterialTheme.typography.titleMediumEmphasized,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = vaultMonogramGlyph(title),
            style = textStyle,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

internal fun vaultMonogramGlyph(title: String): String =
    title.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "#"

/** Space six/eight digit codes into their conventional halves. */
internal fun formatVerificationCode(code: String): String {
    val compact = code.filterNot { it.isWhitespace() }
    if (compact.length !in 6..9 || compact.any { !it.isDigit() }) return code
    val groupSize = if (compact.length % 3 == 0) 3 else 4
    return compact.chunked(groupSize).joinToString(" ")
}

@LightDarkPreviews
@Composable
private fun VaultPasswordDetailPreview() {
    OpenBubblesTheme(dynamicColor = false) {
        VaultItemDetailScreen(
            uiState = VaultItemDetailUiState(
                item = VaultItemUi(
                    id = "1",
                    category = VaultCategory.Passwords,
                    title = "example.com",
                    username = "person@example.com",
                    modifiedAtMs = 1_605_744_000_000L,
                ),
            ),
            onBack = {}, onRequestReveal = {}, onRefreshCode = {}, onCopy = {},
            onDelete = {}, onAddTotp = {},
        )
    }
}

@LightDarkPreviews
@Composable
private fun VaultCodeDetailPreview() {
    OpenBubblesTheme(dynamicColor = false) {
        VaultItemDetailScreen(
            uiState = VaultItemDetailUiState(
                item = VaultItemUi(
                    id = "2",
                    category = VaultCategory.Codes,
                    title = "Example Service",
                    username = "person@example.com",
                ),
                secret = "123456",
                secretExpiresAtSeconds = 4_102_444_830L,
            ),
            onBack = {}, onRequestReveal = {}, onRefreshCode = {}, onCopy = {},
            onDelete = {}, onAddTotp = {},
        )
    }
}
