package app.openbubbles.nativeapp.credentials

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.credentials.provider.BeginGetCredentialResponse
import androidx.credentials.provider.PendingIntentHandler
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import app.openbubbles.nativeapp.NativeMainActivity
import app.openbubbles.nativeapp.ui.Routes
import kotlinx.coroutines.launch

/**
 * Target of the picker's "Unlock OpenBubbles" action.
 *
 * Reached when the durable catalog had nothing for the site and the Apple
 * backend was not running, which is the case the old provider answered with an
 * empty list — indistinguishable from having no credentials. Here the backend
 * gets a chance to start; if it does, the picker is answered with real entries,
 * and if it does not, OpenBubbles opens so the user can sign in or rejoin the
 * keychain instead of being told nothing exists.
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class CredentialUnlockActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val request = PendingIntentHandler.retrieveBeginGetCredentialRequest(intent)
        if (request == null) {
            openPasswordsAndFinish(answered = false)
            return
        }

        lifecycleScope.launch {
            val query = runCatching { CredentialEntries.parse(this@CredentialUnlockActivity, request) }
                .onFailure { Log.w(TAG, "unlock request parse failed (${it.javaClass.simpleName})") }
                .getOrNull()
            if (query == null) {
                answer(BeginGetCredentialResponse(emptyList()))
                openPasswordsAndFinish(answered = true)
                return@launch
            }

            // offerUnlock = false: the user already took the unlock action, so a
            // still-unreachable backend must not loop them back into it.
            val response = runCatching {
                CredentialEntries.respond(applicationContext, query, offerUnlock = false)
            }
                .onFailure { Log.w(TAG, "unlock lookup failed (${it.javaClass.simpleName})") }
                .getOrDefault(BeginGetCredentialResponse(emptyList()))

            answer(response)
            if (response.credentialEntries.isEmpty()) {
                openPasswordsAndFinish(answered = true)
            } else {
                finish()
            }
        }
    }

    private fun answer(response: BeginGetCredentialResponse) {
        val result = Intent()
        PendingIntentHandler.setBeginGetCredentialResponse(result, response)
        setResult(RESULT_OK, result)
    }

    private fun openPasswordsAndFinish(answered: Boolean) {
        if (!answered) answer(BeginGetCredentialResponse(emptyList()))
        runCatching {
            startActivity(
                Intent(this, NativeMainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra(NativeMainActivity.EXTRA_INITIAL_ROUTE, Routes.PASSWORDS),
            )
        }.onFailure { Log.w(TAG, "could not open Passwords (${it.javaClass.simpleName})") }
        finish()
    }

    private companion object {
        const val TAG = "CredentialUnlock"
    }
}
