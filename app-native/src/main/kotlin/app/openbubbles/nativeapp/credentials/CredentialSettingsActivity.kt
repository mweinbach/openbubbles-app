package app.openbubbles.nativeapp.credentials

import android.app.assist.AssistStructure
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.autofill.AutofillManager
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import app.openbubbles.nativeapp.NativeMainActivity
import app.openbubbles.nativeapp.data.PushStateHolder
import app.openbubbles.nativeapp.data.passwords.VaultCatalogSync
import app.openbubbles.nativeapp.ui.Routes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Entry point for the "settings" affordance the system shows next to this app on
 * the Passwords/Autofill provider picker (declared as `settingsActivity` in
 * `res/xml/provider.xml` and `res/xml/autofill_service.xml`). It forwards to the
 * in-app Passwords screen via [NativeMainActivity.EXTRA_INITIAL_ROUTE].
 */
class CredentialSettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getBooleanExtra(EXTRA_AUTOFILL_AUTHENTICATION, false)) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
                cancelAutofill()
                return
            }
            authenticateAutofill()
            return
        }
        startActivity(
            Intent(this, NativeMainActivity::class.java).apply {
                putExtra(NativeMainActivity.EXTRA_INITIAL_ROUTE, Routes.PASSWORDS)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        )
        finish()
    }

    /** Existing declared provider activity; no extra exported component is needed. */
    @RequiresApi(Build.VERSION_CODES.O_MR1)
    private fun authenticateAutofill() {
        val site = intent.getStringExtra(CredentialIntentContract.EXTRA_SITE)
        val credentialId = intent.getStringExtra(CredentialIntentContract.EXTRA_CRED_ID)
        val expectedPackage = intent.getStringExtra(CredentialIntentContract.EXTRA_PACKAGE_NAME)
        val structure = verifiedAutofillStructure(expectedPackage, site)
        if (site == null || credentialId.isNullOrBlank() || structure == null) {
            cancelAutofill()
            return
        }

        CredentialUserAuth.authenticate(
            activity = this,
            title = "Fill iCloud password",
            subtitle = "Authenticate to use your password for $site",
            onSuccess = { completeAutofill(site, credentialId, expectedPackage) },
            onFailure = { cancelAutofill() },
        )
    }

    @RequiresApi(Build.VERSION_CODES.O_MR1)
    private fun completeAutofill(site: String, credentialId: String, expectedPackage: String?) {
        lifecycleScope.launch {
            try {
                // The platform fills this AssistStructure into our mutable
                // PendingIntent. Revalidate its real activity after the prompt.
                val structure = verifiedAutofillStructure(expectedPackage, site)
                if (structure == null) {
                    cancelAutofill()
                    return@launch
                }
                val generation = VaultCatalogSync.captureGeneration()
                val state = awaitPushState(applicationContext)
                if (state == null || VaultCatalogSync.captureGeneration() != generation) {
                    cancelAutofill()
                    return@launch
                }
                val password = state.awaitSiteConfig(site).passwords
                    .firstOrNull { it.credId == credentialId }
                if (password == null ||
                    VaultCatalogSync.captureGeneration() != generation ||
                    PushStateHolder.state !== state
                ) {
                    cancelAutofill()
                    return@launch
                }

                val dataset = AutofillDatasets.LoginInfo(
                    username = password.username,
                    password = password.password,
                    domain = site,
                    otp = password.otp?.toString(),
                ).fillFields(this@CredentialSettingsActivity, structure, inline = null)
                val result = Intent()
                    .putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, dataset)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    result.putExtra(
                        AutofillManager.EXTRA_AUTHENTICATION_RESULT_EPHEMERAL_DATASET,
                        true,
                    )
                }
                setResult(RESULT_OK, result)
                finish()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                Log.w(TAG, "autofill authentication failed (${failure.javaClass.simpleName})")
                cancelAutofill()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O_MR1)
    private fun verifiedAutofillStructure(expectedPackage: String?, site: String?): AutofillStructure? {
        if (expectedPackage.isNullOrBlank() || site.isNullOrBlank()) return null
        @Suppress("DEPRECATION")
        val assist = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(AutofillManager.EXTRA_ASSIST_STRUCTURE, AssistStructure::class.java)
        } else {
            intent.getParcelableExtra<AssistStructure>(AutofillManager.EXTRA_ASSIST_STRUCTURE)
        } ?: return null
        val structure = AutofillStructure(this, assist)
        return structure.takeIf {
            autofillSelectionMatches(expectedPackage, site, it.packageName, it.webDomain)
        }
    }

    private fun cancelAutofill() {
        setResult(RESULT_CANCELED)
        finish()
    }

    internal companion object {
        const val EXTRA_AUTOFILL_AUTHENTICATION = "credential.autofill_authentication"
        const val AUTOFILL_REQUEST_TYPE = "autofill"
        private const val TAG = "CredentialSettings"
    }
}
