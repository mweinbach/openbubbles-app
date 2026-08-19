package app.openbubbles.nativeapp.facetime

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.util.Log

/** Self-managed telecom account for FaceTime; registration is lazy and idempotent. */
internal object FaceTimePhoneAccount {
    private const val TAG = "FaceTimePhoneAccount"
    private const val ACCOUNT_ID = "openbubbles_facetime"

    fun handle(context: Context): PhoneAccountHandle =
        PhoneAccountHandle(
            ComponentName(context, FaceTimeConnectionService::class.java),
            ACCOUNT_ID,
        )

    /**
     * Registers (or re-registers) the account and returns a usable
     * [TelecomManager], or null when this device cannot host self-managed
     * FaceTime calls. Never throws; every failure means the notification-only
     * fallback.
     */
    @Suppress("DEPRECATION") // Required compatibility path for self-managed calls on API 26–35.
    fun register(context: Context): TelecomManager? {
        if (context.checkSelfPermission(Manifest.permission.MANAGE_OWN_CALLS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }
        val telecom = context.getSystemService(TelecomManager::class.java) ?: return null
        return runCatching {
            val account = PhoneAccount.builder(handle(context), "FaceTime")
                .setCapabilities(
                    PhoneAccount.CAPABILITY_SELF_MANAGED or
                        PhoneAccount.CAPABILITY_SUPPORTS_VIDEO_CALLING or
                        PhoneAccount.CAPABILITY_VIDEO_CALLING,
                )
                .setSupportedUriSchemes(listOf(PhoneAccount.SCHEME_TEL, FACETIME_URI_SCHEME))
                .setExtras(
                    Bundle().apply {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                            putBoolean(PhoneAccount.EXTRA_LOG_SELF_MANAGED_CALLS, true)
                        }
                    },
                )
                .build()
            telecom.registerPhoneAccount(account)
            telecom
        }.onFailure { Log.w(TAG, "self-managed phone account unavailable", it) }.getOrNull()
    }
}
