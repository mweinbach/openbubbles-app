package app.openbubbles.nativeapp.ui.chatcreator

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.openbubbles.core.contacts.RawContact
import app.openbubbles.core.model.MessageMapper
import app.openbubbles.nativeapp.data.CoreGraph
import app.openbubbles.nativeapp.data.DeviceContacts
import app.openbubbles.nativeapp.ui.common.ChatAvatar
import app.openbubbles.nativeapp.ui.common.avatarColorFor
import app.openbubbles.nativeapp.ui.theme.OpenBubblesTheme
import app.openbubbles.nativeapp.ui.theme.ServiceColorPair
import app.openbubbles.nativeapp.ui.theme.iMessageServiceColors
import app.openbubbles.nativeapp.ui.theme.serviceColors
import app.openbubbles.nativeapp.ui.theme.smsServiceColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------------------
// Recipient model + address parsing
// ---------------------------------------------------------------------------

/** One committed recipient. [display] is the normalized address; [key] dedupes. */
private data class RecipientChip(val key: String, val display: String, val isEmail: Boolean)

/** A parsed, valid recipient address (email or phone with junk stripped). */
private data class ParsedAddress(val display: String, val isEmail: Boolean)

private val EMAIL_REGEX = Regex("^[^\\s@,;]+@[^\\s@,;]+\\.[A-Za-z]{2,}$")
private val PHONE_REGEX = Regex("^\\+?\\d{7,15}$")
private val PHONE_JUNK = Regex("[\\s\\-().]")

/**
 * Parses raw user / provider input into a valid recipient address: emails
 * pass through trimmed, phones are stripped of spaces, dashes, dots and
 * parentheses and must contain 7-15 digits. Null when neither shape matches.
 */
private fun parseAddress(raw: String): ParsedAddress? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    if (EMAIL_REGEX.matches(trimmed)) return ParsedAddress(trimmed, isEmail = true)
    val phone = trimmed.replace(PHONE_JUNK, "")
    if (PHONE_REGEX.matches(phone)) return ParsedAddress(phone, isEmail = false)
    return null
}

/** Case-insensitive dedupe key (emails only — phones are digit-normalized). */
private fun keyOf(address: ParsedAddress): String =
    if (address.isEmail) address.display.lowercase() else address.display

/** Flattened, filtered + sorted contact row model for the picker list. */
private data class ContactRowUi(
    val contactId: String,
    val name: String,
    val primaryRaw: String,
    val primaryKey: String,
    val subtitle: String,
    val avatarPath: String?,
)

private fun buildRows(contacts: List<RawContact>, query: String): List<ContactRowUi> {
    val q = query.trim()
    return contacts.mapNotNull { contact ->
        val primary = contact.addresses.firstOrNull() ?: return@mapNotNull null
        val parsed = parseAddress(primary)
        val name = contact.displayName?.trim().takeUnless { it.isNullOrEmpty() }
        if (q.isNotEmpty()) {
            val nameHit = name?.contains(q, ignoreCase = true) == true
            val addressHit = contact.addresses.any { it.contains(q, ignoreCase = true) }
            if (!nameHit && !addressHit) return@mapNotNull null
        }
        val matched = if (q.isEmpty()) {
            primary
        } else {
            contact.addresses.firstOrNull { it.contains(q, ignoreCase = true) } ?: primary
        }
        ContactRowUi(
            contactId = contact.id,
            name = name ?: parsed?.display ?: primary,
            primaryRaw = primary,
            primaryKey = parsed?.let(::keyOf) ?: primary.lowercase(),
            subtitle = matched,
            avatarPath = contact.avatarPath,
        )
    }.sortedWith(compareBy({ it.name.isBlank() }, { it.name.lowercase() }))
}

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

/**
 * New-conversation screen: recipient entry (typed addresses + contact
 * picker), iMessage/SMS service selection, then opens (or creates) the chat.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewChatScreen(
    onChatOpened: (chatId: Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    initialRecipients: List<String> = emptyList(),
    initialUseSms: Boolean = false,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val chips = remember { mutableStateListOf<RecipientChip>() }
    var input by rememberSaveable { mutableStateOf("") }
    var showInvalid by remember { mutableStateOf(false) }
    var useSms by rememberSaveable(initialUseSms) { mutableStateOf(initialUseSms) }
    var creating by remember { mutableStateOf(false) }
    var contactsPermission by remember { mutableStateOf(DeviceContacts.hasPermission(context)) }
    var permissionRequested by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionRequested = true
        contactsPermission = granted
    }

    val contacts by produceState<List<RawContact>?>(initialValue = null, contactsPermission) {
        value = if (contactsPermission) DeviceContacts.read(context) else emptyList()
    }

    // Push freshly-read contacts into the core sync so the created chat (and
    // the chat list behind it) resolves participant names right away.
    LaunchedEffect(contacts) {
        val loaded = contacts ?: return@LaunchedEffect
        if (loaded.isNotEmpty()) {
            withContext(Dispatchers.IO) { runCatching { CoreGraph.syncContacts(loaded) } }
        }
    }

    fun addChip(raw: String): Boolean {
        val parsed = parseAddress(raw) ?: return false
        val key = keyOf(parsed)
        if (chips.none { it.key == key }) {
            chips += RecipientChip(key, parsed.display, parsed.isEmail)
        }
        return true
    }

    LaunchedEffect(initialRecipients) {
        initialRecipients.forEach(::addChip)
    }

    fun commitInput() {
        val token = input.trim()
        if (token.isEmpty()) {
            showInvalid = false
            return
        }
        if (addChip(token)) {
            input = ""
            showInvalid = false
        } else {
            showInvalid = true
        }
    }

    // Comma/semicolon always commit the token before them (valid -> chip,
    // invalid -> gentle error). A trailing space commits only when the token
    // so far already parses, so spaced phone entry keeps working.
    fun onInputChange(newValue: String) {
        showInvalid = false
        var rest = newValue
        while (true) {
            val separator = rest.indexOfFirst { it == ',' || it == ';' }
            if (separator < 0) break
            val token = rest.substring(0, separator).trim()
            rest = rest.substring(separator + 1)
            if (token.isNotEmpty() && !addChip(token)) showInvalid = true
        }
        val trimmed = rest.trim()
        if (trimmed.isNotEmpty() && rest != trimmed && addChip(trimmed)) rest = ""
        input = rest
    }

    fun createChat() {
        if (creating || chips.isEmpty()) return
        creating = true
        scope.launch {
            val handles = chips.map { MessageMapper.toRustHandle(it.display) }
            val chatId = withContext(Dispatchers.IO) {
                runCatching { CoreGraph.findOrCreateChat(handles, useSms) }.getOrNull()
            }
            creating = false
            if (chatId != null) {
                onChatOpened(chatId)
            } else {
                snackbarHostState.showSnackbar("Couldn't create the chat. Please try again.")
            }
        }
    }

    val query = input.trim()
    val rows = remember(contacts, query) { buildRows(contacts.orEmpty(), query) }
    val addedKeys = remember(chips) { chips.map { it.key }.toSet() }
    val serviceColors = serviceColors(useSms)

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("New Message") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item(key = "recipients") {
                RecipientField(
                    chips = chips,
                    input = input,
                    showInvalid = showInvalid,
                    onInputChange = { onInputChange(it) },
                    onCommit = { commitInput() },
                    onRemoveChip = { chip -> chips.remove(chip) },
                )
            }
            item(key = "service") {
                ServiceSelector(
                    useSms = useSms,
                    onUseSmsChange = { useSms = it },
                )
            }
            item(key = "create") {
                CreateChatButton(
                    enabled = chips.isNotEmpty(),
                    creating = creating,
                    useSms = useSms,
                    accent = serviceColors,
                    onClick = { createChat() },
                )
            }
            if (query.isNotEmpty()) {
                item(key = "typed-address") {
                    TypedAddressRow(text = query, onAdd = { commitInput() })
                }
            }
            when {
                !contactsPermission -> item(key = "permission") {
                    PermissionCard(
                        requested = permissionRequested,
                        onGrant = { permissionLauncher.launch(Manifest.permission.READ_CONTACTS) },
                    )
                }
                contacts == null -> item(key = "loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        LoadingIndicator()
                    }
                }
                rows.isEmpty() -> item(key = "empty") {
                    EmptyContacts(query = query)
                }
                else -> {
                    item(key = "contacts-header") {
                        SectionLabel(if (query.isEmpty()) "Contacts" else "Matches")
                    }
                    items(rows, key = { "${it.contactId}|${it.primaryKey}" }) { row ->
                        ContactRowView(
                            row = row,
                            added = row.primaryKey in addedKeys,
                            onClick = { addChip(row.primaryRaw) },
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Recipient entry
// ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun RecipientField(
    chips: List<RecipientChip>,
    input: String,
    showInvalid: Boolean,
    onInputChange: (String) -> Unit,
    onCommit: () -> Unit,
    onRemoveChip: (RecipientChip) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Swap the keyboard once the buffer clearly looks like a phone number.
    val phoneish = input.isNotEmpty() &&
        input.all { it.isDigit() || it in "+()-. " }
    Column(modifier = modifier) {
        Surface(
            shape = MaterialTheme.shapes.largeIncreased,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FlowRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    chips.forEach { chip ->
                        InputChip(
                            selected = false,
                            onClick = { onRemoveChip(chip) },
                            label = { Text(chip.display) },
                            trailingIcon = {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Remove ${chip.display}",
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                        )
                    }
                    BasicTextField(
                        value = input,
                        onValueChange = onInputChange,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (phoneish) KeyboardType.Phone else KeyboardType.Email,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { onCommit() }),
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .defaultMinSize(minWidth = 96.dp, minHeight = 32.dp)
                            .semantics { contentDescription = "Recipient address" },
                        decorationBox = { innerTextField ->
                            Box(
                                contentAlignment = Alignment.CenterStart,
                                modifier = Modifier.defaultMinSize(minHeight = 32.dp),
                            ) {
                                if (input.isEmpty() && chips.isEmpty()) {
                                    Text(
                                        text = "To:",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                innerTextField()
                            }
                        },
                    )
                }
                if (input.isNotBlank()) {
                    IconButton(onClick = onCommit) {
                        Icon(Icons.Filled.Add, contentDescription = "Add recipient")
                    }
                }
            }
        }
        if (showInvalid && input.isNotBlank()) {
            Text(
                text = "Enter a valid email or phone number",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 12.dp, top = 6.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Service (iMessage / SMS) + create button
// ---------------------------------------------------------------------------

@Composable
private fun ServiceSelector(
    useSms: Boolean,
    onUseSmsChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    // An exclusive either/or, so the pills are a connected toggle group: the
    // checked service morphs to a full pill and carries real toggle semantics.
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        ServiceToggle(
            checked = !useSms,
            onSelect = { onUseSmsChange(false) },
            label = "iMessage",
            colors = iMessageServiceColors(),
            shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
            icon = {
                Icon(
                    Icons.AutoMirrored.Filled.Chat,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            },
            modifier = Modifier.weight(1f),
        )
        ServiceToggle(
            checked = useSms,
            onSelect = { onUseSmsChange(true) },
            label = "SMS",
            colors = smsServiceColors(),
            shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
            icon = {
                Icon(
                    Icons.Filled.Sms,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ServiceToggle(
    checked: Boolean,
    onSelect: () -> Unit,
    label: String,
    colors: ServiceColorPair,
    shapes: ToggleButtonShapes,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    ToggleButton(
        checked = checked,
        onCheckedChange = { onSelect() },
        shapes = shapes,
        colors = ToggleButtonDefaults.toggleButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            checkedContainerColor = colors.container,
            checkedContentColor = colors.content,
        ),
        modifier = modifier.defaultMinSize(minHeight = 48.dp),
    ) {
        icon()
        Spacer(Modifier.width(6.dp))
        Text(text = label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun CreateChatButton(
    enabled: Boolean,
    creating: Boolean,
    useSms: Boolean,
    accent: ServiceColorPair,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        // Expressive overload: the resting silhouette stays, and the button
        // morphs its corners on press instead of pinning a static shape.
        shapes = ButtonDefaults.shapes(shape = MaterialTheme.shapes.large),
        enabled = enabled && !creating,
        colors = ButtonDefaults.buttonColors(
            containerColor = accent.container,
            contentColor = accent.content,
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .height(52.dp),
    ) {
        if (creating) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = accent.content,
            )
        } else {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = when {
                creating -> "Creating…"
                useSms -> "Create SMS Chat"
                else -> "Create Chat"
            },
        )
    }
}

// ---------------------------------------------------------------------------
// Contact list
// ---------------------------------------------------------------------------

@Composable
private fun SectionLabel(label: String, modifier: Modifier = Modifier) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 12.dp, top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun TypedAddressRow(text: String, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.largeIncreased)
            .clickable(onClick = onAdd)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Column {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Send to this address",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ContactRowView(
    row: ContactRowUi,
    added: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.largeIncreased)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ChatAvatar(
            title = row.name,
            avatarColor = avatarColorFor(row.primaryKey),
            size = 40.dp,
            avatarPath = row.avatarPath,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = row.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (added) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "Added",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun EmptyContacts(query: String, modifier: Modifier = Modifier) {
    Text(
        text = if (query.isEmpty()) {
            "No contacts found"
        } else {
            "No contacts match \"$query\""
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(48.dp),
    )
}

// ---------------------------------------------------------------------------
// Permission empty state
// ---------------------------------------------------------------------------

@Composable
private fun PermissionCard(
    requested: Boolean,
    onGrant: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.largeIncreased,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Filled.Contacts,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp),
            )
            Text(
                text = "Contacts access is off",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Grant access to pick recipients from your contacts. " +
                    "You can always type an address above instead.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onGrant) {
                Text(if (requested) "Grant Contacts" else "Allow Contacts Access")
            }
            if (requested) {
                Text(
                    text = "Permission was denied — type an address above to continue.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

@Preview(showBackground = true)
@Composable
private fun NewChatScreenPreview() {
    OpenBubblesTheme {
        NewChatScreen(onChatOpened = {}, onBack = {})
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun NewChatScreenDarkPreview() {
    OpenBubblesTheme {
        NewChatScreen(onChatOpened = {}, onBack = {})
    }
}
