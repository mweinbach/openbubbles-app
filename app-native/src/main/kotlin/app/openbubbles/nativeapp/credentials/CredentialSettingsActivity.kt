package app.openbubbles.nativeapp.credentials

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import app.openbubbles.nativeapp.NativeMainActivity
import app.openbubbles.nativeapp.ui.Routes

/**
 * Entry point for the "settings" affordance the system shows next to this app on
 * the Passwords/Autofill provider picker (declared as `settingsActivity` in
 * `res/xml/provider.xml` and `res/xml/autofill_service.xml`). It forwards to the
 * in-app Passwords screen via [NativeMainActivity.EXTRA_INITIAL_ROUTE].
 */
class CredentialSettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(
            Intent(this, NativeMainActivity::class.java).apply {
                putExtra(NativeMainActivity.EXTRA_INITIAL_ROUTE, Routes.PASSWORDS)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        )
        finish()
    }
}
