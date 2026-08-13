package com.bluebubbles.messaging.services.credentials


import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.CancellationSignal
import android.service.autofill.*
import android.util.Log
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import com.bluebubbles.messaging.MainActivity
import com.bluebubbles.messaging.services.credentials.AutofillStructure.AutofillType
import java.time.YearMonth
import java.util.regex.Pattern
import com.bluebubbles.messaging.R
import com.bluebubbles.messaging.services.rustpush.APNClient
import com.bluebubbles.messaging.services.rustpush.APNService
import uniffi.rust_lib_bluebubbles.InsertKeychainCallback
import uniffi.rust_lib_bluebubbles.RetrieveKeysCallback
import uniffi.rust_lib_bluebubbles.SavedPasskey
import uniffi.rust_lib_bluebubbles.SavedPassword

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

        val intent = Intent(this, MainActivity::class.java)
        intent.putExtra("chatGuid", "-55")
        pendingClaifyIntent = PendingIntent.getActivity(
            this, 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        Log.d("MyAutofillService", "onFillRequest")


        Log.i("Current domain", "${structure.webDomain}")

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
                            Log.i("FLAG_DELAY_SAVE", "true")
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
        Log.i("Really", "what ${structure.webDomain}")
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
        for (context in request.fillContexts) {
            val currentContext = context.structure
            val structure = AutofillStructure(this, currentContext)

            val data = AutofillDatasets.LoginInfo("Unknown", "", "", "")
            data.importFields(structure)

            if (data.password == "") continue

            val client = APNClient(this)
            client.bind { service: APNService ->
                service.pushState!!.keychainPasswordInsert(structure.webDomain!!, data.username, data.password, object : InsertKeychainCallback {
                    override fun done(error: String?) {
                        if (error != null) {
                            Log.e("Error", "error")
                        }
                        client.destroy()
                    }
                }, null)
            }
        }
        callback.onSuccess()
    }
}