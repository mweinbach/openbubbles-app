package app.openbubbles.nativeapp.sms

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony

/** Android default-SMS role helpers used by onboarding and Settings. */
object SmsRole {
    fun isHeld(context: Context): Boolean =
        Telephony.Sms.getDefaultSmsPackage(context) == context.packageName

    fun requestIntent(context: Context): Intent? = if (Build.VERSION.SDK_INT >= 29) {
        val roles = context.getSystemService(RoleManager::class.java)
        if (roles?.isRoleAvailable(RoleManager.ROLE_SMS) == true) {
            roles.createRequestRoleIntent(RoleManager.ROLE_SMS)
        } else {
            null
        }
    } else {
        Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).putExtra(
            Telephony.Sms.Intents.EXTRA_PACKAGE_NAME,
            context.packageName,
        )
    }
}
