package app.openbubbles.nativeapp.data

import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uniffi.rust_lib_bluebubbles.NativePushState
import uniffi.rust_lib_bluebubbles.UViableBottle
import uniffi.rust_lib_bluebubbles.savedLoginUsername
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.spec.MGF1ParameterSpec
import java.util.Base64
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource

private const val NATIVE_SETUP_PREFS = "native_setup"
private const val LEGACY_KEYCHAIN_RECOVERY_CODE = "keychain_recovery_code"
private const val SECURE_RECOVERY_PREFS = "icloud_keychain_recovery"
private const val KEY_RECOVERY_ACCOUNT_SCOPE = "account_scope"
private const val KEY_RECOVERY_CIPHERTEXT = "ciphertext"
private const val RECOVERY_KEY_ALIAS = "openbubbles.icloud.keychain.recovery.v1"
private const val RECOVERY_AUTH_VALIDITY_SECONDS = 30
private const val ANDROID_KEY_STORE = "AndroidKeyStore"
private val RECOVERY_CODE_FORMAT = Regex("[0-9]{6}")

/**
 * Joining this device to the Apple account's end-to-end encrypted trust
 * circle ("clique"), which is what lets Messages in iCloud history decrypt.
 *
 * Shared by the first-run onboarding step and the Settings iCloud section so
 * both paths generate, persist, and surface the same local recovery code.
 */
object ICloudKeychainEnrollment {

    /**
     * Nearby-device (BLE) approval is off: the proximity handshake does not
     * currently complete against Apple, and offering a path that always
     * fails reads as a broken app. The advertiser and its Rust pairing calls
     * stay in the tree behind this flag for when it is fixed.
     */
    const val NEARBY_APPROVAL_ENABLED = false

    /** Trusted-device escrow records this account can currently recover from. */
    suspend fun viableBottles(state: NativePushState): Result<List<UViableBottle>> =
        withContext(Dispatchers.IO) { runCatching { state.getViableBottles() } }

    /**
     * Imports [bottle] using that device's passcode. On success the freshly
     * generated local recovery code is persisted and returned.
     */
    suspend fun joinWithBottle(
        context: Context,
        state: NativePushState,
        bottle: UViableBottle,
        passcode: String,
    ): Result<String> {
        if (!unlockICloudKeychain(context)) {
            return Result.failure(
                IllegalStateException("iCloud Keychain unlock was cancelled or unavailable"),
            )
        }
        val recoveryCode = generateRecoveryCode()
        return withContext(Dispatchers.IO) {
            runCatching {
                // Public-key encryption needs no fresh prompt, but decrypting the
                // resulting ciphertext always requires a recently authenticated
                // device credential or strong biometric.
                val protectedCode = encryptRecoveryCode(context, recoveryCode)
                state.joinCliqueWithBottle(bottle.escrowData, passcode, recoveryCode)
                check(state.isInClique()) { "Apple did not confirm iCloud Keychain membership" }
                check(PushStateHolder.state === state) {
                    "The Apple account changed while iCloud Keychain was joining"
                }
                persistRecoveryCode(context, protectedCode)
                recoveryCode
            }
        }
    }

    /** Metadata-only check: never decrypt a recovery secret while composing UI. */
    fun hasSavedRecoveryCode(context: Context): Boolean {
        removeUnscopedLegacyRecoveryCode(context)
        val currentScope = currentRecoveryAccountScope(context) ?: return false
        val preferences = securePrefs(context)
        return recoveryCodeBelongsToAccount(
            currentAccountScope = currentScope,
            storedAccountScope = preferences.getString(KEY_RECOVERY_ACCOUNT_SCOPE, null),
            ciphertext = preferences.getString(KEY_RECOVERY_CIPHERTEXT, null),
        )
    }

    /** Call only after a successful fresh biometric/device-credential prompt. */
    fun savedRecoveryCode(context: Context): String? {
        removeUnscopedLegacyRecoveryCode(context)
        val currentScope = currentRecoveryAccountScope(context) ?: return null
        val preferences = securePrefs(context)
        val storedScope = preferences.getString(KEY_RECOVERY_ACCOUNT_SCOPE, null) ?: return null
        val encodedCiphertext = preferences.getString(KEY_RECOVERY_CIPHERTEXT, null) ?: return null
        if (!recoveryCodeBelongsToAccount(currentScope, storedScope, encodedCiphertext)) return null

        val privateKey = recoveryPrivateKey() ?: return null
        check(recoveryKeyRequiresAuthentication(privateKey)) {
            "The iCloud Keychain recovery key is not protected by device authentication"
        }
        val cipher = recoveryCipher().apply {
            init(Cipher.DECRYPT_MODE, privateKey, recoveryOaepParameters())
        }
        val payload = cipher.doFinal(Base64.getDecoder().decode(encodedCiphertext))
        return try {
            decodeAccountScopedRecoveryCode(currentScope, payload)
        } finally {
            payload.fill(0)
        }
    }

    /** Synchronously destroys both legacy plaintext and account-owned ciphertext/key material. */
    @SuppressLint("UseKtx") // Recovery secrets must not survive a failed synchronous clear.
    fun clearRecoveryCode(context: Context) {
        synchronized(recoveryLock) {
            val legacy = legacyPrefs(context)
            check(legacy.edit().remove(LEGACY_KEYCHAIN_RECOVERY_CODE).commit()) {
                "Could not clear the legacy iCloud Keychain recovery code"
            }
            check(securePrefs(context).edit().clear().commit()) {
                "Could not clear the iCloud Keychain recovery code"
            }
            val store = recoveryKeyStore()
            if (store.containsAlias(RECOVERY_KEY_ALIAS)) {
                store.deleteEntry(RECOVERY_KEY_ALIAS)
            }
        }
    }

    /** Human-facing text for an escrow lookup that returned nothing usable. */
    fun escrowRecoveryFailure(message: String?): String {
        val detail = message.orEmpty()
        return if (
            detail.contains("unimplemented escrow format 1", ignoreCase = true) ||
            detail.contains("legacy escrow", ignoreCase = true)
        ) {
            "Apple only returned an older recovery record that OpenGarden cannot read. " +
                "Nothing was reset — try a different trusted device."
        } else {
            detail.ifEmpty { "Unable to fetch trusted devices" }
        }
    }

    /** Copy for an account with no usable escrow record on any device. */
    fun noViableBottlesMessage(): String =
        "No current recovery record was found on your trusted Apple devices. " +
            "Nothing was reset — open Messages in iCloud on an iPhone or Mac, then try again."

    private fun generateRecoveryCode(): String =
        SecureRandom().nextInt(1_000_000).toString().padStart(6, '0')

    private data class ProtectedRecoveryCode(
        val accountScope: String,
        val ciphertext: String,
    )

    private val recoveryLock = Any()

    private fun encryptRecoveryCode(context: Context, code: String): ProtectedRecoveryCode =
        synchronized(recoveryLock) {
            removeUnscopedLegacyRecoveryCode(context)
            val accountScope = currentRecoveryAccountScope(context)
                ?: error("Cannot identify the current Apple account for iCloud Keychain recovery")
            val publicKey = recoveryPublicKey(context)
            val plaintext = encodeAccountScopedRecoveryCode(accountScope, code)
            val encrypted = try {
                recoveryCipher().run {
                    init(Cipher.ENCRYPT_MODE, publicKey, recoveryOaepParameters())
                    doFinal(plaintext)
                }
            } finally {
                plaintext.fill(0)
            }
            ProtectedRecoveryCode(accountScope, Base64.getEncoder().encodeToString(encrypted))
        }

    @SuppressLint("UseKtx") // Account scope and ciphertext must commit atomically with a checked result.
    private fun persistRecoveryCode(context: Context, protectedCode: ProtectedRecoveryCode) {
        synchronized(recoveryLock) {
            check(currentRecoveryAccountScope(context) == protectedCode.accountScope) {
                "The Apple account changed before its iCloud Keychain recovery code was saved"
            }
            check(
                securePrefs(context).edit()
                    .putString(KEY_RECOVERY_ACCOUNT_SCOPE, protectedCode.accountScope)
                    .putString(KEY_RECOVERY_CIPHERTEXT, protectedCode.ciphertext)
                    .commit(),
            ) { "Could not save the encrypted iCloud Keychain recovery code" }
            removeUnscopedLegacyRecoveryCode(context)
        }
    }

    @SuppressLint("UseKtx") // Legacy plaintext removal must be durable before another account proceeds.
    private fun removeUnscopedLegacyRecoveryCode(context: Context) {
        val legacy = legacyPrefs(context)
        if (!legacy.contains(LEGACY_KEYCHAIN_RECOVERY_CODE)) return
        // Older builds attached no account identity to this plaintext value.
        // Adopting it for whichever account is currently active would disclose
        // the previous user's recovery code after an account switch.
        check(legacy.edit().remove(LEGACY_KEYCHAIN_RECOVERY_CODE).commit()) {
            "Could not remove the unscoped iCloud Keychain recovery code"
        }
    }

    private fun currentRecoveryAccountScope(context: Context): String? {
        if (PushStateHolder.state == null) return null
        val username = runCatching {
            savedLoginUsername(context.applicationContext.filesDir.absolutePath)
        }.getOrNull()
        return recoveryCodeAccountScope(username)
    }

    @SuppressLint("UseKtx") // Reject an unprotected wrapping key if its old ciphertext cannot be cleared.
    @Suppress("DEPRECATION")
    private fun recoveryPublicKey(context: Context): java.security.PublicKey {
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        check(keyguard?.isDeviceSecure == true) {
            "Set a screen lock before protecting an iCloud Keychain recovery code"
        }

        val store = recoveryKeyStore()
        val existing = store.getEntry(RECOVERY_KEY_ALIAS, null) as? KeyStore.PrivateKeyEntry
        if (existing != null) {
            if (recoveryKeyRequiresAuthentication(existing.privateKey)) {
                return existing.certificate.publicKey
            }
            // Never keep a downgraded wrapping key around after an upgrade.
            store.deleteEntry(RECOVERY_KEY_ALIAS)
            check(securePrefs(context).edit().clear().commit()) {
                "Could not discard an unprotected iCloud Keychain recovery key"
            }
        }

        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, ANDROID_KEY_STORE)
        val spec = KeyGenParameterSpec.Builder(
            RECOVERY_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setKeySize(2048)
            .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA1)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
            .setUserAuthenticationRequired(true)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setUserAuthenticationParameters(
                        RECOVERY_AUTH_VALIDITY_SECONDS,
                        KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL,
                    )
                } else {
                    setUserAuthenticationValidityDurationSeconds(RECOVERY_AUTH_VALIDITY_SECONDS)
                }
            }
            .build()
        generator.initialize(spec)
        return generator.generateKeyPair().public
    }

    private fun recoveryPrivateKey(): PrivateKey? =
        (recoveryKeyStore().getEntry(RECOVERY_KEY_ALIAS, null) as? KeyStore.PrivateKeyEntry)?.privateKey

    private fun recoveryKeyRequiresAuthentication(key: PrivateKey): Boolean =
        KeyFactory.getInstance(key.algorithm, ANDROID_KEY_STORE)
            .getKeySpec(key, KeyInfo::class.java)
            .isUserAuthenticationRequired

    private fun recoveryKeyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }

    private fun recoveryCipher(): Cipher = Cipher.getInstance("RSA/ECB/OAEPPadding")

    private fun recoveryOaepParameters(): OAEPParameterSpec = OAEPParameterSpec(
        KeyProperties.DIGEST_SHA256,
        "MGF1",
        MGF1ParameterSpec.SHA1,
        PSource.PSpecified.DEFAULT,
    )

    private fun legacyPrefs(context: Context) =
        context.applicationContext.getSharedPreferences(NATIVE_SETUP_PREFS, Context.MODE_PRIVATE)

    private fun securePrefs(context: Context) =
        context.applicationContext.getSharedPreferences(SECURE_RECOVERY_PREFS, Context.MODE_PRIVATE)
}

/** Account identifiers are normalized and hashed; no Apple ID is persisted with the ciphertext. */
internal fun recoveryCodeAccountScope(username: String?): String? {
    val normalized = username?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() } ?: return null
    return MessageDigest.getInstance("SHA-256")
        .digest(normalized.toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
}

internal fun recoveryCodeBelongsToAccount(
    currentAccountScope: String?,
    storedAccountScope: String?,
    ciphertext: String?,
): Boolean {
    if (currentAccountScope.isNullOrBlank() || storedAccountScope.isNullOrBlank() || ciphertext.isNullOrBlank()) {
        return false
    }
    return MessageDigest.isEqual(
        currentAccountScope.toByteArray(Charsets.UTF_8),
        storedAccountScope.toByteArray(Charsets.UTF_8),
    )
}

internal fun encodeAccountScopedRecoveryCode(accountScope: String, code: String): ByteArray {
    require(accountScope.isNotBlank()) { "An Apple account scope is required" }
    require(RECOVERY_CODE_FORMAT.matches(code)) { "An iCloud Keychain recovery code must contain six digits" }
    return "$accountScope:$code".toByteArray(Charsets.UTF_8)
}

internal fun decodeAccountScopedRecoveryCode(accountScope: String, payload: ByteArray): String? {
    val text = payload.toString(Charsets.UTF_8)
    val separator = text.lastIndexOf(':')
    if (separator <= 0) return null
    val storedScope = text.substring(0, separator)
    val code = text.substring(separator + 1)
    if (!RECOVERY_CODE_FORMAT.matches(code)) return null
    return if (recoveryCodeBelongsToAccount(accountScope, storedScope, ciphertext = "present")) code else null
}
