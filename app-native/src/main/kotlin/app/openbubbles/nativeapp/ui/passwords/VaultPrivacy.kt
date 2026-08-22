package app.openbubbles.nativeapp.ui.passwords

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.os.PersistableBundle
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal const val VAULT_CLIPBOARD_CLEAR_DELAY_MILLIS = 60_000L

// ClipDescription.EXTRA_IS_SENSITIVE is only available from API 33, but the
// backwards-compatible string is honored without touching that newer field.
internal const val VAULT_CLIPBOARD_SENSITIVE_EXTRA = "android.content.extra.IS_SENSITIVE"
private const val VAULT_CLIPBOARD_OWNER_EXTRA =
    "app.openbubbles.nativeapp.passwords.CLIPBOARD_OWNER"
private const val DEFAULT_VAULT_CLIPBOARD_LABEL = "iCloud Password"

/** Copy a secret briefly, without deleting a later user-owned clipboard item. */
fun copySensitiveVaultValue(
    clipboard: ClipboardManager,
    scope: CoroutineScope,
    value: String,
    label: String = DEFAULT_VAULT_CLIPBOARD_LABEL,
): Job = copySensitiveVaultValue(
    clipboard = AndroidVaultClipboardStore(clipboard),
    scope = scope,
    value = value,
    label = label,
)

internal data class VaultClipboardContent(
    val label: String,
    val value: String,
    val ownerToken: String,
    val sensitive: Boolean,
)

internal interface VaultClipboardStore {
    fun set(content: VaultClipboardContent)
    fun current(): VaultClipboardContent?
    fun clear()
}

internal fun copySensitiveVaultValue(
    clipboard: VaultClipboardStore,
    scope: CoroutineScope,
    value: String,
    label: String = DEFAULT_VAULT_CLIPBOARD_LABEL,
    ownerToken: String = UUID.randomUUID().toString(),
    clearDelayMillis: Long = VAULT_CLIPBOARD_CLEAR_DELAY_MILLIS,
): Job {
    val ownedContent = VaultClipboardContent(
        label = label,
        value = value,
        ownerToken = ownerToken,
        sensitive = true,
    )
    clipboard.set(ownedContent)
    return scope.launch {
        delay(clearDelayMillis)
        if (clipboard.current() == ownedContent) clipboard.clear()
    }
}

private class AndroidVaultClipboardStore(
    private val clipboard: ClipboardManager,
) : VaultClipboardStore {
    override fun set(content: VaultClipboardContent) {
        val clip = ClipData.newPlainText(content.label, content.value)
        clip.description.extras = PersistableBundle().apply {
            putBoolean(VAULT_CLIPBOARD_SENSITIVE_EXTRA, content.sensitive)
            putString(VAULT_CLIPBOARD_OWNER_EXTRA, content.ownerToken)
        }
        clipboard.setPrimaryClip(clip)
    }

    override fun current(): VaultClipboardContent? {
        val clip = try {
            clipboard.primaryClip
        } catch (_: SecurityException) {
            // Android can deny clipboard reads once the app loses focus.
            null
        } ?: return null
        if (clip.itemCount != 1) return null
        val extras = clip.description.extras ?: return null
        return VaultClipboardContent(
            label = clip.description.label?.toString().orEmpty(),
            value = clip.getItemAt(0).text?.toString() ?: return null,
            ownerToken = extras.getString(VAULT_CLIPBOARD_OWNER_EXTRA) ?: return null,
            sensitive = extras.getBoolean(VAULT_CLIPBOARD_SENSITIVE_EXTRA),
        )
    }

    override fun clear() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                clipboard.clearPrimaryClip()
            } else {
                clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
            }
        } catch (_: SecurityException) {
            // Losing clipboard access between the ownership check and clear
            // must not crash an activity that is already backgrounded.
        }
    }
}
