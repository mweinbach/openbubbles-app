package app.openbubbles.nativeapp

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import app.openbubbles.nativeapp.ui.Routes

/**
 * The second launcher icon ("Passwords"). Builds a fresh intent instead of
 * forwarding the incoming one (exported activity, intent-redirection hygiene)
 * and hands off to the singleTask [NativeMainActivity], which seeds the
 * Passwords screen as its back-stack root.
 */
class PasswordsLauncherActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(
            Intent(this, NativeMainActivity::class.java).apply {
                putExtra(NativeMainActivity.EXTRA_INITIAL_ROUTE, Routes.PASSWORDS)
                putExtra(NativeMainActivity.EXTRA_STANDALONE_TASK, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        )
        finish()
    }
}
