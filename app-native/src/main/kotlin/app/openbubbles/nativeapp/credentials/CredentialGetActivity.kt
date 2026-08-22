package app.openbubbles.nativeapp.credentials

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Base64
import androidx.annotation.RequiresApi
import androidx.credentials.GetCredentialResponse
import androidx.credentials.PasswordCredential
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.provider.PendingIntentHandler
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import java.io.ByteArrayOutputStream
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import org.json.JSONObject
import uniffi.rust_lib_bluebubbles.NativePushState
import uniffi.rust_lib_bluebubbles.RetrieveKeysCallback
import uniffi.rust_lib_bluebubbles.SavedPasskey
import uniffi.rust_lib_bluebubbles.SavedPassword
import uniffi.rust_lib_bluebubbles.SpecialAppleAuthCallback
import androidx.core.net.toUri
import app.openbubbles.nativeapp.data.PushStateHolder
import app.openbubbles.nativeapp.data.APNClient
import app.openbubbles.nativeapp.data.APNService
import app.openbubbles.core.passwords.VaultItemKind
import app.openbubbles.nativeapp.data.passwords.VaultCatalogStore
import app.openbubbles.nativeapp.data.passwords.VaultCatalogSync
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class CredentialGetActivity : FragmentActivity() {

    private val client = APNClient(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val request = PendingIntentHandler.retrieveProviderGetCredentialRequest(intent)
        if (request == null) {
            finishAndRemoveTask()
            return
        }

        val selection = verifyCredentialSelection(this, intent, request)
        if (selection == null) {
            failWith("The credential request could not be verified")
            return
        }

        val continueFlow = {
            client.bind { service: APNService ->
                service.pushState?.let {
                    handleService(it)
                } ?: failWith("iCloud Keychain is not connected")
            }
        }

        if (selection.type == CredentialIntentContract.TYPE_PASSKEY) {
            CredentialUserAuth.authenticateForPasskey(
                this,
                onSuccess = continueFlow,
                onFailure = {
                    finish()
                }
            )
        } else {
            CredentialUserAuth.authenticate(
                activity = this,
                title = "Confirm your identity",
                subtitle = "Authenticate to use your iCloud password",
                onSuccess = continueFlow,
                onFailure = { finish() },
            )
        }
    }

    private fun handleService(service: NativePushState) {
        val request = PendingIntentHandler.retrieveProviderGetCredentialRequest(intent)
        if (request == null) {
            finishAndRemoveTask()
            return
        }

        // Recheck after authentication as well: a mutable system PendingIntent
        // must never let an earlier picker entry select another app's request.
        val selection = verifyCredentialSelection(this, intent, request)
        if (selection == null) {
            failWith("The credential request could not be verified")
            return
        }
        val site = selection.site
        val credId = selection.credentialId
        val type = selection.type
        val origin = selection.origin
        val packageName = selection.packageName
        val requestJson = selection.requestJson
        val clientDataHash = selection.clientDataHash
        val accountGeneration = VaultCatalogSync.captureGeneration()

        service.getSiteConfig(site, object : RetrieveKeysCallback {
            override fun keys(passwords: List<SavedPassword>, passkeys: List<SavedPasskey>) {
                try {
                    if (VaultCatalogSync.captureGeneration() != accountGeneration ||
                        PushStateHolder.state !== service
                    ) {
                        failWith("The credential account changed")
                        return
                    }
                    when (type) {
                        CredentialIntentContract.TYPE_PASSWORD -> {
                            val saved = passwords.firstOrNull { it.credId == credId }
                            if (saved == null) {
                                // The picker entry came from the durable catalog;
                                // the record can be gone (deleted on another
                                // device) by the time the user chooses it.
                                invalidateAndFail(
                                    VaultItemKind.Password,
                                    credId,
                                    accountGeneration,
                                    "That password is no longer in iCloud Keychain",
                                )
                                return
                            }
                            if (selection.allowedUserIds.isNotEmpty() &&
                                saved.username !in selection.allowedUserIds
                            ) {
                                failWith("The selected password is not allowed for this request")
                                return
                            }

                            val response = GetCredentialResponse(
                                PasswordCredential(saved.username, saved.password)
                            )
                            val result = Intent()
                            PendingIntentHandler.setGetCredentialResponse(result, response)
                            setResult(RESULT_OK, result)
                            finish()
                        }

                        CredentialIntentContract.TYPE_PASSKEY -> {
                            val saved = passkeys.firstOrNull { it.credId == credId }
                            if (saved == null || requestJson == null) {
                                if (saved == null) {
                                    invalidateAndFail(
                                        VaultItemKind.Passkey,
                                        credId,
                                        accountGeneration,
                                        "That passkey is no longer in iCloud Keychain",
                                    )
                                } else {
                                    failWith("The passkey request is no longer available")
                                }
                                return
                            }

                            val requestObj = JSONObject(requestJson)
                            val challenge = requestObj.optString("challenge", "")
                            val rpId = requestObj.optString("rpId", site)
                            if (rpId.isNotEmpty() && selection.browserOrigin &&
                                !originMatchesRpId(origin, rpId)
                            ) {
                                finish()
                                return
                            }

                            val clientDataJsonPlain = JSONObject()
                                .put("type", "webauthn.get")
                                .put("challenge", challenge)
                                .put("origin", origin)
                                .apply {
                                    if (origin.startsWith("android:apk-key-hash:")) {
                                        put("androidPackageName", packageName)
                                    }
                                }
                                .toString().replace("\\/", "/")
                            val clientDataJson = if (clientDataHash != null) {
                                "{}".toByteArray(Charsets.UTF_8)
                            } else {
                                clientDataJsonPlain.toByteArray(Charsets.UTF_8)
                            }

                            val dataHash = clientDataHash ?: sha256(clientDataJson)
                            val rpIdHash = sha256(rpId.toByteArray(Charsets.UTF_8))

                            val flags = (0x01 or 0x04 or 0x08 or 0x10).toByte() // UP
                            val signCount = byteArrayOf(0, 0, 0, 0)

                            val authData = ByteArrayOutputStream().apply {
                                    write(rpIdHash)
                                write(byteArrayOf(flags))
                                write(signCount)
                            }.toByteArray()

                            val privateKey = decodeEcPrivateKey(saved.key)
                            val signature = Signature.getInstance("SHA256withECDSA").apply {
                                initSign(privateKey)
                                update(authData)
                                update(dataHash)
                            }.sign()

                            val user = decodeUserTag(saved.tag)
                            val endTheThing = { clientExtensionResults: JSONObject ->
                                val responseObj = JSONObject()
                                    .put("id", base64UrlEncode(saved.id))
                                    .put("rawId", base64UrlEncode(saved.id))
                                    .put("type", "public-key")
                                    .put("clientExtensionResults", clientExtensionResults)
                                    .put(
                                        "response",
                                        JSONObject()
                                            .put("authenticatorData", base64UrlEncode(authData))
                                            .put("clientDataJSON", base64UrlEncode(clientDataJson))
                                            .put("signature", base64UrlEncode(signature))
                                            .apply {
                                                if (user.id != null) {
                                                    put("userHandle", base64UrlEncode(user.id))
                                                }
                                            }
                                    )

                                val response = GetCredentialResponse(
                                    PublicKeyCredential(responseObj.toString())
                                )
                                val result = Intent()
                                PendingIntentHandler.setGetCredentialResponse(result, response)
                                setResult(RESULT_OK, result)
                                finish()
                            }

                            val extensions = requestObj.optJSONObject("extensions")
                            val largeBlob = extensions?.optJSONObject("largeBlob")
                            if (largeBlob != null && rpId == "apple.com") {
                                service.doSpecialAppleAuth(Base64.encodeToString(dataHash, Base64.NO_WRAP or Base64.URL_SAFE), object : SpecialAppleAuthCallback {
                                    override fun gotVerification(
                                        token: Map<String, String>,
                                        error: String?
                                    ) {
                                        if (error != null) {
                                            finish()
                                            return
                                        }
                                        val clientExtensionResults = JSONObject(token)

                                        clientExtensionResults.put(
                                            "largeBlob",
                                            JSONObject().put(
                                                "blob",
                                                // yup, double base64, someone is *super* special!
                                                base64UrlEncode(Base64.encodeToString(clientExtensionResults.toString().toByteArray(Charsets.UTF_8), Base64.NO_WRAP).toByteArray(Charsets.UTF_8))
                                            )
                                        )
                                        endTheThing(clientExtensionResults)
                                    }
                                })
                            } else {
                                endTheThing(JSONObject())
                            }
                        }

                        else -> finish()
                    }
                } catch (error: Exception) {
                    throw error
                }
            }
        })
    }

    /**
     * Reports a real failure to the calling app. Finishing without a result
     * looks like the user dismissed the picker, which hides a missing record or
     * an unreachable keychain behind a silent cancel.
     */
    private fun failWith(message: String) {
        val result = Intent()
        PendingIntentHandler.setGetCredentialException(result, GetCredentialUnknownException(message))
        setResult(RESULT_OK, result)
        finish()
    }

    private fun invalidateAndFail(
        kind: VaultItemKind,
        credId: String,
        accountGeneration: Long,
        message: String,
    ) {
        lifecycleScope.launch {
            try {
                VaultCatalogSync.publishIfCurrent(accountGeneration) {
                    VaultCatalogStore.of(applicationContext).removeItem(kind, credId)
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                // The catalog is disposable. A failed eviction must not leave
                // Credential Manager waiting for the selection result.
            }
            failWith(message)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        client.destroy()
    }
}

private fun decodeEcPrivateKey(der: ByteArray): PrivateKey {
    val keyFactory = KeyFactory.getInstance("EC")
    return keyFactory.generatePrivate(PKCS8EncodedKeySpec(der))
}
