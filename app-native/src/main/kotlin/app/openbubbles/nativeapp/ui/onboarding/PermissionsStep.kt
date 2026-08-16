package app.openbubbles.nativeapp.ui.onboarding

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.openbubbles.nativeapp.data.CoreGraph
import app.openbubbles.nativeapp.data.DeviceContacts
import app.openbubbles.nativeapp.sms.SmsRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Lifecycle of the one-shot device-contact import after READ_CONTACTS grant. */
private enum class ContactSyncState { Idle, Running, Done }

/** The SIM relay trio — optional, requested together as one opt-in. */
private val SmsPermissionSet = listOf(
    Manifest.permission.RECEIVE_SMS,
    Manifest.permission.SEND_SMS,
    Manifest.permission.READ_SMS,
)

private fun Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

private fun Context.hasSmsPermissions(): Boolean =
    SmsPermissionSet.all { hasPermission(it) }

/**
 * Step 3 — permission priming. Each card explains why before the system
 * dialog appears; grant state is shown inline and everything can be
 * re-requested later (settings or the chat-list sign-in path). "Continue"
 * is always enabled — nothing here blocks sign-in.
 */
@Composable
internal fun PermissionsStep(
    onContinue: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    // Bumped after every permission callback so derived grant states refresh.
    var revision by remember { mutableIntStateOf(0) }
    var contactSync by rememberSaveable { mutableStateOf(ContactSyncState.Idle) }
    var notifDenied by rememberSaveable { mutableStateOf(false) }
    var smsOptIn by rememberSaveable { mutableStateOf(false) }
    var smsDenied by rememberSaveable { mutableStateOf(false) }

    val notificationsGranted = remember(context, revision) {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.hasPermission(Manifest.permission.POST_NOTIFICATIONS)
    }
    val contactsGranted = remember(context, revision) {
        context.hasPermission(Manifest.permission.READ_CONTACTS)
    }
    val smsPermissionsGranted = remember(context, revision) { context.hasSmsPermissions() }
    val smsRoleHeld = remember(context, revision) { SmsRole.isHeld(context) }
    val smsGranted = smsPermissionsGranted && smsRoleHeld

    // One contact sync as soon as (and only once after) access is granted;
    // re-runs if it was interrupted mid-flight by leaving the step.
    LaunchedEffect(contactsGranted) {
        if (contactsGranted && contactSync != ContactSyncState.Done) {
            contactSync = ContactSyncState.Running
            val raw = DeviceContacts.read(context)
            withContext(Dispatchers.IO) { runCatching { CoreGraph.syncContacts(raw) } }
            contactSync = ContactSyncState.Done
        }
    }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notifDenied = !granted
        revision++
    }
    val contactsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ -> revision++ }
    val smsRoleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { _ -> revision++ }
    val smsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants.values.any { !it }) {
            smsDenied = true
            smsOptIn = false
        } else {
            smsDenied = false
            SmsRole.requestIntent(context)?.let(smsRoleLauncher::launch)
        }
        revision++
    }

    Column(modifier = modifier.fillMaxSize()) {
        OnboardingTopBar(onBack = onBack, activeSegment = 1)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = OnboardingPadding),
        ) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "A few permissions",
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Grant what you're comfortable with — everything is " +
                    "re-requestable later from system settings.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))

            PermissionCard(
                icon = Icons.Filled.Notifications,
                title = "Notifications",
                tag = "Required",
                rationale = "Receive messages in real time.",
                granted = notificationsGranted,
                onRequest = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                subtitle = when {
                    notificationsGranted && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ->
                        "Granted automatically on this Android version."
                    notifDenied -> "Denied — enable notifications in Settings to be notified."
                    else -> null
                },
            )
            Spacer(Modifier.height(12.dp))
            PermissionCard(
                icon = Icons.Filled.Contacts,
                title = "Contacts",
                tag = "Recommended",
                rationale = "Show names and photos in your chats.",
                granted = contactsGranted,
                onRequest = { contactsLauncher.launch(Manifest.permission.READ_CONTACTS) },
                subtitle = when (contactSync) {
                    ContactSyncState.Running -> "Syncing contacts…"
                    ContactSyncState.Done -> "Contacts synced"
                    ContactSyncState.Idle -> null
                },
            )
            Spacer(Modifier.height(12.dp))
            PermissionCard(
                icon = Icons.Filled.Sms,
                title = "SMS relay",
                tag = "Optional",
                rationale = "Send and receive green-bubble SMS through this phone's SIM.",
                granted = smsGranted,
                onRequest = {
                    if (smsPermissionsGranted) {
                        SmsRole.requestIntent(context)?.let(smsRoleLauncher::launch)
                    } else {
                        smsLauncher.launch(SmsPermissionSet.toTypedArray())
                    }
                },
                subtitle = when {
                    smsGranted -> null
                    smsPermissionsGranted && !smsRoleHeld ->
                        "Choose OpenBubbles as the default SMS app to receive MMS and group media."
                    smsDenied -> "Not enabled — you can turn it on later in system Settings."
                    else -> null
                },
                trailing = if (smsGranted) {
                    null // Default trailing shows the granted checkmark.
                } else {
                    {
                        Switch(
                            checked = smsOptIn,
                            onCheckedChange = { want ->
                                smsOptIn = want
                                if (want) {
                                    if (smsPermissionsGranted) {
                                        SmsRole.requestIntent(context)?.let(smsRoleLauncher::launch)
                                    } else {
                                        smsLauncher.launch(SmsPermissionSet.toTypedArray())
                                    }
                                } else {
                                    smsDenied = false
                                }
                            },
                            modifier = Modifier.semantics {
                                contentDescription = "Enable SMS relay"
                            },
                        )
                    }
                },
            )

            Spacer(Modifier.height(28.dp))
            Button(
                onClick = onContinue,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
            ) {
                Text(text = "Continue", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Skipping any of these is fine — sign-in works regardless.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ---------------------------------------------------------------------- card

/**
 * One permission rationale card: tonal icon, title + priority tag, reason,
 * optional status line, and a trailing action (Allow button / checkmark by
 * default, overridable — e.g. the SMS opt-in switch).
 */
@Composable
private fun PermissionCard(
    icon: ImageVector,
    title: String,
    tag: String,
    rationale: String,
    granted: Boolean,
    onRequest: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.largeIncreased,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = title, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(8.dp))
                    Tag(text = tag)
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = rationale,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (subtitle != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            val action: @Composable () -> Unit = trailing ?: {
                if (granted) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Granted",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    FilledTonalButton(onClick = onRequest, shapes = ButtonDefaults.shapes()) { Text("Allow") }
                }
            }
            action()
        }
    }
}

/** Small priority label chip ("Required" / "Recommended" / "Optional"). */
@Composable
private fun Tag(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
