package app.openbubbles.nativeapp.credentials


import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.CancellationSignal
import android.service.autofill.*
import android.util.Log
import android.widget.RemoteViews
import androidx.annotation.RequiresApi

import app.openbubbles.nativeapp.credentials.AutofillStructure.AutofillType
import java.util.regex.Pattern
import app.openbubbles.nativeapp.R
import app.openbubbles.core.passwords.VaultCredentialRequest
import app.openbubbles.core.passwords.VaultItemKind
import app.openbubbles.core.passwords.VaultLookupPlan
import app.openbubbles.core.passwords.planVaultLookup
import app.openbubbles.nativeapp.data.PushStateHolder
import app.openbubbles.nativeapp.data.APNClient
import app.openbubbles.nativeapp.data.APNService
import app.openbubbles.nativeapp.data.passwords.VaultCatalogStore
import app.openbubbles.nativeapp.data.passwords.VaultCatalogSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import uniffi.rust_lib_bluebubbles.InsertKeychainCallback
import uniffi.rust_lib_bluebubbles.SavedPassword
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@RequiresApi(Build.VERSION_CODES.O_MR1)
class OBAutofillService : AutofillService() {

    companion object {
        private const val TAG = "OBAutofillService"
        private val PASSWORD_KIND = setOf(VaultItemKind.Password)

        var pendingClaifyIntent: PendingIntent? = null
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val creditCards = listOf<AutofillDatasets.CreditCard>()

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        pendingClaifyIntent?.cancel()
        pendingClaifyIntent = null
    }

    fun handleFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback,
        passwords: List<SavedPassword>,
        structure: AutofillStructure
    ) {
        pendingClaifyIntent?.cancel()

        val intent = Intent(this, app.openbubbles.nativeapp.NativeMainActivity::class.java)
        intent.putExtra("chatGuid", "-55")
        pendingClaifyIntent = PendingIntent.getActivity(
            this, 1, intent,
            mutablePendingIntentFlags(),
        )

        val suggestions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            request.inlineSuggestionsRequest
        } else { null }

        val response = FillResponse.Builder()
        if (structure.hasCreditCards()) {
            for (card in creditCards) {
                response.addDataset(card.fillFields(this, structure, suggestions))
            }
            response.setSaveInfo(
                SaveInfo.Builder(SaveInfo.SAVE_DATA_TYPE_CREDIT_CARD, structure.getCreditCardsFields().toTypedArray())
                    .build()
            )
        }

        if (structure.hasEmails()) {
            for (password in passwords) {
                val domain = structure.webDomain ?: continue
                val authentication = PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, CredentialSettingsActivity::class.java)
                        .setAction(
                            credentialPendingIntentAction(
                                structure.packageName,
                                domain,
                                domain,
                                password.credId,
                                CredentialSettingsActivity.AUTOFILL_REQUEST_TYPE,
                            ),
                        )
                        .putExtra(CredentialSettingsActivity.EXTRA_AUTOFILL_AUTHENTICATION, true)
                        .putExtra(CredentialIntentContract.EXTRA_SITE, domain)
                        .putExtra(CredentialIntentContract.EXTRA_CRED_ID, password.credId)
                        .putExtra(CredentialIntentContract.EXTRA_PACKAGE_NAME, structure.packageName),
                    // Android appends EXTRA_ASSIST_STRUCTURE when launching the
                    // authentication activity, so this explicit intent must be mutable.
                    mutablePendingIntentFlags(),
                )
                response.addDataset(AutofillDatasets.LoginInfo(
                    password.username,
                    "",
                    domain,
                    password.otp?.let { "" },
                ).fillFields(this, structure, suggestions, authentication.intentSender))
            }
            val saveFields = structure.getEmailSaveFields()
            if (saveFields.isNotEmpty()) {
                val emailField = structure.fields.find { it.second == AutofillType.EMAIL }
                val passwordField = structure.fields.find { it.second == AutofillType.PASSWORD }

                val remoteViews = RemoteViews(packageName, R.layout.save_custom_description)
                val desc = CustomDescription.Builder(remoteViews)
                emailField?.first?.autofillId?.let {
                    desc.addChild(R.id.email_text, CharSequenceTransformation.Builder(it, Pattern.compile(".*"), "$0")
                        .build())
                }
                passwordField?.first?.autofillId?.let {
                    desc.addChild(R.id.password_text, CharSequenceTransformation.Builder(it, Pattern.compile("."), "•")
                        .build())
                }
                response.setSaveInfo(
                    SaveInfo.Builder(SaveInfo.SAVE_DATA_TYPE_PASSWORD or SaveInfo.SAVE_DATA_TYPE_USERNAME, saveFields.toTypedArray())
                        .setFlags(if (!structure.fields.any { it.second == AutofillType.PASSWORD &&
                                    it.first.htmlInfo?.attributes?.find { it.first == "visibility" }?.second != "invisible" } && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            SaveInfo.FLAG_DELAY_SAVE
                        } else {
                            0
                        })
                        .setCustomDescription(desc.build())
                        .build()
                )
            }
        }

        var finish: FillResponse? = null

        try {
            finish = response.build()

        } catch (e: IllegalStateException) {
            // Ignore (means we didn't add any views, null is fine)
        }

        // Return an empty response
        callback.onSuccess(finish)
    }

    private fun mutablePendingIntentFlags(): Int {
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags = flags or PendingIntent.FLAG_MUTABLE
        }
        return flags
    }


    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback
    ) {
        val currentContext = request.fillContexts.last().structure
        val structure = AutofillStructure(this, currentContext)
        val domain = structure.webDomain
        if (domain == null || !structure.hasEmails()) {
            callback.onSuccess(null)
            return
        }
        val context = applicationContext
        scope.launch {
            try {
                val generation = VaultCatalogSync.captureGeneration()
                val catalog = VaultCatalogStore.of(context)
                val snapshot = providerVaultSnapshot(catalog, domain, PASSWORD_KIND)
                val passwordRequest = VaultCredentialRequest(
                    site = domain,
                    wantsPasswords = true,
                    wantsPasskeys = false,
                )
                // The catalog answers whether this domain has anything at all
                // without waiting on Rust. Autofill still needs the actual
                // secret to build a dataset, so a match means "ask the backend",
                // and a warm miss means "answer now with nothing".
                val plan = planVaultLookup(snapshot, passwordRequest, PushStateHolder.state != null)
                if (plan is VaultLookupPlan.NoCredentials) {
                    callback.onSuccess(null)
                    return@launch
                }
                val state = awaitPushState(context)
                if (state == null || VaultCatalogSync.captureGeneration() != generation) {
                    // Signed out, locked, or a cold process the system started
                    // for this request. Answer with nothing rather than crash.
                    Log.i(TAG, "autofill skipped: Apple services are not connected")
                    callback.onSuccess(null)
                    return@launch
                }
                val config = state.awaitSiteConfig(domain)
                if (VaultCatalogSync.captureGeneration() != generation || PushStateHolder.state !== state) {
                    callback.onSuccess(null)
                    return@launch
                }
                VaultCatalogSync.refreshIfCurrent(context, state, generation)
                handleFillRequest(request, cancellationSignal, callback, config.passwords, structure)
            } catch (failure: Throwable) {
                Log.w(TAG, "autofill fill request failed (${failure.javaClass.simpleName})")
                callback.onSuccess(null)
            }
        }
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        data class PendingSave(val domain: String, val username: String, val password: String)

        val pending = request.fillContexts.mapNotNull { context ->
            val currentContext = context.structure
            val structure = AutofillStructure(this, currentContext)

            val data = AutofillDatasets.LoginInfo("Unknown", "", "", "")
            data.importFields(structure)

            val domain = structure.webDomain ?: return@mapNotNull null
            if (data.password.isEmpty()) return@mapNotNull null
            PendingSave(domain, data.username, data.password)
        }

        if (pending.isEmpty()) {
            callback.onFailure("No password fields were available to save")
            return
        }

        val remaining = AtomicInteger(pending.size)
        val completed = AtomicBoolean(false)
        fun finishOne(error: String?) {
            if (error != null && completed.compareAndSet(false, true)) {
                callback.onFailure(error)
                return
            }
            if (remaining.decrementAndGet() == 0 && completed.compareAndSet(false, true)) {
                callback.onSuccess()
            }
        }

        pending.forEach { save ->
            val generation = VaultCatalogSync.captureGeneration()
            val client = APNClient(this)
            client.bind { service: APNService ->
                val pushState = service.pushState
                if (pushState == null) {
                    client.destroy()
                    finishOne("Apple password storage is unavailable")
                    return@bind
                }
                pushState.keychainPasswordInsert(save.domain, save.username, save.password, object : InsertKeychainCallback {
                    override fun done(error: String?) {
                        client.destroy()
                        // A saved credential the catalog does not know about is
                        // invisible to the next fill request until the next
                        // listing, so re-read the vault on success.
                        if (error == null) {
                            VaultCatalogSync.refreshNowIfCurrent(
                                applicationContext,
                                pushState,
                                generation,
                            )
                        }
                        finishOne(error)
                    }
                }, null)
            }
        }
    }
}
