package app.openbubbles.nativeapp.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/**
 * Runtime-permission helpers for the SIM SMS/MMS feature.
 *
 * RECEIVE_SMS and SEND_SMS are dangerous permissions — they must be requested
 * from the UI (the same `RequestMultiplePermissions` launcher
 * `NativeMainActivity` already uses for POST_NOTIFICATIONS/READ_CONTACTS).
 * RECEIVE_MMS is a normal install-time permission (no prompt).
 *
 * Integrator note: add `Manifest.permission.RECEIVE_SMS`, `SEND_SMS` and
 * (for MMS ingest + telephony thread tracking) `READ_SMS` to the `wanted`
 * list in `NativeMainActivity.onCreate` — see the task report.
 */
object SmsPermissions {

    fun has(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** Gates SMS_RECEIVED delivery + on-device sending. */
    fun canSendSms(context: Context): Boolean = has(context, Manifest.permission.SEND_SMS)

    fun canReceiveSms(context: Context): Boolean = has(context, Manifest.permission.RECEIVE_SMS)

    /**
     * Gates telephony-provider reads: MMS content ingest and best-effort
     * thread-id (Chat.telephonyId) resolution. Optional for plain SMS.
     */
    fun canReadTelephony(context: Context): Boolean = has(context, Manifest.permission.READ_SMS)
}
