package app.openbubbles.nativeapp.credentials


import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.CancellationSignal
import android.service.autofill.*
import android.widget.RemoteViews
import androidx.annotation.RequiresApi

import app.openbubbles.nativeapp.credentials.AutofillStructure.AutofillType
import java.time.YearMonth
import java.util.regex.Pattern
import app.openbubbles.nativeapp.R
import app.openbubbles.nativeapp.data.PushStateHolder
import app.openbubbles.nativeapp.data.APNClient
import app.openbubbles.nativeapp.data.APNService
import uniffi.rust_lib_bluebubbles.InsertKeychainCallback
import uniffi.rust_lib_bluebubbles.RetrieveKeysCallback
import uniffi.rust_lib_bluebubbles.SavedPasskey
import uniffi.rust_lib_bluebubbles.SavedPassword
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@RequiresApi(Build.VERSION_CODES.O_MR1)
class OBAutofillService : AutofillService() {

    companion object {
        var pendingClaifyIntent: PendingIntent? = null
    }

    val creditCards = listOf<AutofillDatasets.CreditCard>()

    override fun onDestroy() {
        super.onDestroy()
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
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
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
                response.addDataset(AutofillDatasets.LoginInfo(
                    password.username,
                    password.password,
                    "",
                    password.otp?.toString()
                ).fillFields(this, structure, suggestions))
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


    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback
    ) {
        val currentContext = request.fillContexts.last().structure
        val structure = AutofillStructure(this, currentContext)
        if (structure.webDomain == null || !structure.hasEmails()) {
            callback.onSuccess(null)
            return
        }
        val client = APNClient(this)
        client.bind { service: APNService ->
            service.pushState!!.getSiteConfig(structure.webDomain!!, object : RetrieveKeysCallback {
                override fun keys(passwords: List<SavedPassword>, passkeys: List<SavedPasskey>) {
                    handleFillRequest(request, cancellationSignal, callback, passwords, structure)
                    client.destroy()
                }
            })
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
                        finishOne(error)
                    }
                }, null)
            }
        }
    }
}
