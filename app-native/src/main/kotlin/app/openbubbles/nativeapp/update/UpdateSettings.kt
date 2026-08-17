package app.openbubbles.nativeapp.update

import android.content.Context
import android.util.Base64
import androidx.core.content.edit
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Update-subsystem preferences: check bookkeeping, deferral, rollback floor,
 * and the GitHub access token (AES-GCM encrypted with an Android Keystore key
 * before it ever touches disk). The app has `allowBackup=false`, so none of
 * this leaves the device.
 */
object UpdateSettings {
    private const val PREFS = "native_update"
    private const val KEY_LAST_CHECK = "last_check_ms"
    private const val KEY_DEFERRED = "deferred_version_code"
    private const val KEY_HIGHEST_SEEN = "highest_seen_version_code"
    private const val KEY_TOKEN_CT = "token_ciphertext"
    private const val KEY_TOKEN_IV = "token_iv"
    private const val KEY_PENDING_CODE = "pending_version_code"
    private const val KEY_PENDING_NAME = "pending_version_name"
    private const val KEY_PENDING_NOTES = "pending_version_notes"
    private const val KEYSTORE_ALIAS = "openbubbles_update_token"

    fun lastCheckMs(context: Context): Long =
        prefs(context).getLong(KEY_LAST_CHECK, 0L)

    fun recordCheck(context: Context, atMs: Long = System.currentTimeMillis()) {
        prefs(context).edit { putLong(KEY_LAST_CHECK, atMs) }
    }

    fun deferredVersionCode(context: Context): Long =
        prefs(context).getLong(KEY_DEFERRED, 0L)

    fun deferVersionCode(context: Context, versionCode: Long) {
        prefs(context).edit { putLong(KEY_DEFERRED, versionCode) }
    }

    /** Local rollback floor: the highest versionCode this device has been offered. */
    fun highestSeenVersionCode(context: Context): Long =
        prefs(context).getLong(KEY_HIGHEST_SEEN, 0L)

    fun recordSeenVersionCode(context: Context, versionCode: Long) {
        prefs(context).edit {
            putLong(KEY_HIGHEST_SEEN, maxOf(versionCode, highestSeenVersionCode(context)))
        }
    }

    fun clearDeferred(context: Context) {
        prefs(context).edit { remove(KEY_DEFERRED) }
    }

    // ------------------------------------------------------------------
    // Pending (downloaded, verified, not yet installed) update
    // ------------------------------------------------------------------

    fun recordPending(context: Context, manifest: UpdateManifest) {
        prefs(context).edit {
            putLong(KEY_PENDING_CODE, manifest.versionCode)
            putString(KEY_PENDING_NAME, manifest.versionName)
            putString(KEY_PENDING_NOTES, manifest.notes)
        }
    }

    fun clearPending(context: Context) {
        prefs(context).edit {
            remove(KEY_PENDING_CODE)
            remove(KEY_PENDING_NAME)
            remove(KEY_PENDING_NOTES)
        }
    }

    /** The last downloaded-and-verified update, or null when none recorded. */
    fun pendingVersionCode(context: Context): Long =
        prefs(context).getLong(KEY_PENDING_CODE, 0L)

    fun pendingVersionName(context: Context): String? =
        prefs(context).getString(KEY_PENDING_NAME, null)

    fun pendingNotes(context: Context): String? =
        prefs(context).getString(KEY_PENDING_NOTES, null)

    // ------------------------------------------------------------------
    // GitHub token — encrypted at rest
    // ------------------------------------------------------------------

    /** @return the stored token, or null when absent/unreadable (re-prompt). */
    fun githubToken(context: Context): String? {
        val p = prefs(context)
        val ct = p.getString(KEY_TOKEN_CT, null) ?: return null
        val iv = p.getString(KEY_TOKEN_IV, null) ?: run { clearToken(context); return null }
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, decode(iv)))
            String(cipher.doFinal(decode(ct)), Charsets.UTF_8)
        } catch (_: Exception) {
            // Key invalidated or data corrupted: forget it and ask again.
            clearToken(context)
            null
        }
    }

    fun storeGithubToken(context: Context, token: String) {
        require(token.isNotBlank()) { "empty token" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val ct = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        prefs(context).edit {
            putString(KEY_TOKEN_CT, encode(ct))
            putString(KEY_TOKEN_IV, encode(cipher.iv))
        }
    }

    fun clearToken(context: Context) {
        prefs(context).edit {
            remove(KEY_TOKEN_CT)
            remove(KEY_TOKEN_IV)
        }
    }

    fun hasToken(context: Context): Boolean =
        prefs(context).getString(KEY_TOKEN_CT, null) != null

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun encode(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun decode(text: String): ByteArray =
        Base64.decode(text, Base64.NO_WRAP)

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(KEYSTORE_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance("AES", "AndroidKeyStore")
        generator.init(
            android.security.keystore.KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                    android.security.keystore.KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }
}
