package app.openbubbles.nativeapp.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uniffi.rust_lib_bluebubbles.NativePushState
import uniffi.rust_lib_bluebubbles.UViableBottle
import java.security.SecureRandom

private const val NATIVE_SETUP_PREFS = "native_setup"
private const val KEY_KEYCHAIN_RECOVERY_CODE = "keychain_recovery_code"

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
                state.joinCliqueWithBottle(bottle.escrowData, passcode, recoveryCode)
                check(state.isInClique()) { "Apple did not confirm iCloud Keychain membership" }
                recoveryCode
            }
        }.onSuccess { saveRecoveryCode(context, it) }
    }

    fun savedRecoveryCode(context: Context): String? =
        prefs(context).getString(KEY_KEYCHAIN_RECOVERY_CODE, null)

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

    private fun saveRecoveryCode(context: Context, code: String) {
        prefs(context).edit { putString(KEY_KEYCHAIN_RECOVERY_CODE, code) }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(NATIVE_SETUP_PREFS, Context.MODE_PRIVATE)
}
