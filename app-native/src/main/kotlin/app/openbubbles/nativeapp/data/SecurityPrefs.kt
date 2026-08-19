package app.openbubbles.nativeapp.data

import android.content.Context
import androidx.core.content.edit

/** App-lock preference. Authentication uses the device credential, not a second PIN. */
class SecurityPrefs(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var appLockEnabled: Boolean
        get() = prefs.getBoolean(KEY_APP_LOCK, false)
        set(value) {
            prefs.edit { putBoolean(KEY_APP_LOCK, value) }
        }

    private companion object {
        const val PREFS_NAME = "security_prefs"
        const val KEY_APP_LOCK = "app_lock"
    }
}
