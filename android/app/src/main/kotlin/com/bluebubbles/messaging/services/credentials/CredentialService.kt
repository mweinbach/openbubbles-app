package com.bluebubbles.messaging.services.credentials

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.OutcomeReceiver
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.CreateCredentialUnknownException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.provider.BeginGetPasswordOption
import androidx.credentials.provider.BeginGetPublicKeyCredentialOption
import androidx.credentials.provider.BeginCreateCredentialRequest
import androidx.credentials.provider.BeginCreateCredentialResponse
import androidx.credentials.provider.BeginGetCredentialRequest
import androidx.credentials.provider.BeginGetCredentialResponse
import androidx.credentials.provider.CallingAppInfo
import androidx.credentials.provider.CreateEntry
import androidx.credentials.provider.CredentialEntry
import androidx.credentials.provider.CredentialProviderService
import androidx.credentials.provider.PasswordCredentialEntry
import androidx.credentials.provider.PublicKeyCredentialEntry
import androidx.credentials.provider.ProviderClearCredentialStateRequest
import com.bluebubbles.messaging.MainActivity
import com.bluebubbles.messaging.services.rustpush.APNClient
import com.bluebubbles.messaging.services.rustpush.APNService
import org.json.JSONObject
import uniffi.rust_lib_bluebubbles.AvailableGroupsCallback
import uniffi.rust_lib_bluebubbles.RetrieveKeysCallback
import uniffi.rust_lib_bluebubbles.SavedPassword
import uniffi.rust_lib_bluebubbles.SavedPasskey
import java.security.MessageDigest
import java.time.Instant

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class CredentialService : CredentialProviderService() {

    override fun onBeginCreateCredentialRequest(
        request: BeginCreateCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginCreateCredentialResponse, CreateCredentialException>
    ) {
        CredentialWebAuthnUtils.ensurePrivilegedAllowlistFresh(this) { error ->
            if (error != null) {
                callback.onError(
                    CreateCredentialUnknownException("Failed to refresh privileged app allowlist: ${error.message}")
                )
                return@ensurePrivilegedAllowlistFresh
            }

            val prefs = getSharedPreferences("credential_usage_stats", Context.MODE_PRIVATE)

            val intent = Intent(this, CredentialCreateActivity::class.java)
            val pending = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_MUTABLE
                    or PendingIntent.FLAG_UPDATE_CURRENT)

            val client = APNClient(this)
            client.bind { service: APNService ->
                val push = service.pushState

                // 1 to always put this first by default
                val lastUsedNull = prefs.getLong("usage_last_null", 1)
                val createEntries = mutableListOf(
                    CreateEntry(
                        accountName = "Not shared",
                        pendingIntent = pending,
                        description = null,
                        lastUsedTime = if (lastUsedNull > 0) Instant.ofEpochMilli(lastUsedNull) else null
                    )
                )

                if (push == null) {
                    client.destroy()
                    callback.onResult(BeginCreateCredentialResponse(createEntries))
                    return@bind
                }

                push.getAvailableGroups(object : AvailableGroupsCallback {
                    override fun groups(groups: Map<String, String>) {
                        createEntries.addAll(groups.asSequence().mapIndexed { idx, group ->
                            val intent = Intent(this@CredentialService, CredentialCreateActivity::class.java)
                            intent.putExtra("group_id", group.value)
                            val pending = PendingIntent.getActivity(this@CredentialService, idx + 1, intent, PendingIntent.FLAG_MUTABLE
                                    or PendingIntent.FLAG_UPDATE_CURRENT)
                            
                            val lastUsed = prefs.getLong("usage_last_group_${group.value}", 0)
                            CreateEntry(
                                accountName = group.key,
                                pendingIntent = pending,
                                description = "Saving to ${group.key}",
                                lastUsedTime = if (lastUsed > 0) Instant.ofEpochMilli(lastUsed) else null
                            )
                        })
                        callback.onResult(BeginCreateCredentialResponse(createEntries))
                    }
                })
            }

        }
    }

    override fun onBeginGetCredentialRequest(
        request: BeginGetCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginGetCredentialResponse, GetCredentialException>
    ) {
        CredentialWebAuthnUtils.ensurePrivilegedAllowlistFresh(this) { error ->
            if (error != null) {
                callback.onError(
                    GetCredentialUnknownException("Failed to refresh privileged app allowlist: ${error.message}")
                )
                return@ensurePrivilegedAllowlistFresh
            }
            handleBeginGetCredentialRequest(request, callback)
        }
    }

    private fun handleBeginGetCredentialRequest(
        request: BeginGetCredentialRequest,
        callback: OutcomeReceiver<BeginGetCredentialResponse, GetCredentialException>
    ) {
        val callingAppInfo = request.callingAppInfo
            ?: throw IllegalStateException("CallingAppInfo is required for WebAuthn operations")
        val callingOrigin = appInfoToOrigin(this, callingAppInfo)

        var site = ""
        var passkeyRequestJson: String? = null
        var passkeyOption: BeginGetPublicKeyCredentialOption? = null
        var passwordOption: BeginGetPasswordOption? = null
        var allowedCredentialIds: Set<String>? = null
        var clientDataHash: ByteArray? = null
        for (option in request.beginGetCredentialOptions) {
            when (option) {
                is BeginGetPublicKeyCredentialOption -> {
                    passkeyOption = option
                    passkeyRequestJson = option.requestJson
                    clientDataHash = option.clientDataHash
                    val parsed = JSONObject(option.requestJson)
                    site = parsed.optString("rpId", site)
                    val allowCredentials = parsed.optJSONArray("allowCredentials")
                    if (allowCredentials != null && allowCredentials.length() > 0) {
                        val ids = HashSet<String>()
                        for (i in 0 until allowCredentials.length()) {
                            val item = allowCredentials.optJSONObject(i) ?: continue
                            val id = item.optString("id")
                            if (id.isNotEmpty()) {
                                ids.add(id)
                            }
                        }
                        allowedCredentialIds = ids
                    }
                }
                is BeginGetPasswordOption -> {
                    passwordOption = option
                }
            }
        }
        if (site.isEmpty()) {
            site = callingOrigin
        }

        // RP ID check: if origin host is present, it must be equal to or a subdomain of rpId.
        if (callingAppInfo.isOriginPopulated() && site.isNotEmpty()) {
            val host = callingOrigin.removeSuffix("/")
            val rpId = site
            val ok = host == rpId || host.endsWith(rpId)
            if (!ok) {
                callback.onResult(BeginGetCredentialResponse(emptyList()))
                return
            }
        }

        val client = APNClient(this)
        client.bind { service: APNService ->
            val push = service.pushState
            if (push == null) {
                client.destroy()
                callback.onResult(BeginGetCredentialResponse(emptyList()))
                return@bind
            }

            push.getSiteConfig(site, object : RetrieveKeysCallback {
                override fun keys(passwords: List<SavedPassword>, passkeys: List<SavedPasskey>) {
                    val entries = ArrayList<CredentialEntry>()

                    passwords.forEachIndexed { index, saved ->
                        val option = passwordOption ?: return@forEachIndexed
                        val intent = Intent(this@CredentialService, CredentialGetActivity::class.java).apply {
                            putExtra(EXTRA_SITE, site)
                            putExtra(EXTRA_CRED_ID, saved.credId)
                            putExtra(EXTRA_TYPE, TYPE_PASSWORD)
                            putExtra(EXTRA_ORIGIN, callingOrigin)
                            putExtra(EXTRA_PACKAGE_NAME, callingAppInfo.packageName)
                        }
                        val pending = PendingIntent.getActivity(
                            this@CredentialService,
                            index,
                            intent,
                            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                        )
                        val entry = PasswordCredentialEntry.Builder(
                            this@CredentialService,
                            saved.username,
                            pending,
                            option,
                        )
                            .build()
                        entries.add(entry)
                    }

                    passkeys.forEachIndexed { index, saved ->
                        val option = passkeyOption ?: return@forEachIndexed
                        if (allowedCredentialIds != null) {
                            val savedId = base64UrlEncode(saved.id)
                            if (!allowedCredentialIds!!.contains(savedId)) {
                                return@forEachIndexed
                            }
                        }
                        val user = decodeUserTag(saved.tag)
                        val username = user.name ?: user.displayName ?: "Passkey"
                        val displayName = user.displayName ?: user.name ?: username
                        val intent = Intent(this@CredentialService, CredentialGetActivity::class.java).apply {
                            putExtra(EXTRA_SITE, site)
                            putExtra(EXTRA_CRED_ID, saved.credId)
                            putExtra(EXTRA_TYPE, TYPE_PASSKEY)
                            putExtra(EXTRA_ORIGIN, callingOrigin)
                            putExtra(EXTRA_PACKAGE_NAME, callingAppInfo.packageName)
                            if (passkeyRequestJson != null) {
                                putExtra(EXTRA_REQUEST_JSON, passkeyRequestJson)
                            }
                            if (clientDataHash != null) {
                                putExtra(EXTRA_CLIENT_DATA_HASH, clientDataHash)
                            }
                        }
                        val pending = PendingIntent.getActivity(
                            this@CredentialService,
                            10_000 + index,
                            intent,
                            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                        )
                        val entry = PublicKeyCredentialEntry.Builder(
                            this@CredentialService,
                            username,
                            pending,
                                    option,
                        )
                            .setDisplayName(displayName)
                            .build()
                        entries.add(entry)
                    }

                    client.destroy()
                    callback.onResult(BeginGetCredentialResponse(entries))
                }
            })
        }
    }

    override fun onClearCredentialStateRequest(
        request: ProviderClearCredentialStateRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<Void?, ClearCredentialException>
    ) {
        callback.onResult(null)
    }

    companion object {
        const val EXTRA_SITE = "credential.site"
        const val EXTRA_CRED_ID = "credential.cred_id"
        const val EXTRA_TYPE = "credential.type"
        const val EXTRA_ORIGIN = "credential.origin"
        const val EXTRA_PACKAGE_NAME = "credential.package_name"
        const val EXTRA_REQUEST_JSON = "credential.request_json"
        const val EXTRA_CLIENT_DATA_HASH = "credential.client_data_hash"
        const val TYPE_PASSWORD = "password"
        const val TYPE_PASSKEY = "passkey"

        fun appInfoToOrigin(context: Context, info: CallingAppInfo): String {
            if (info.isOriginPopulated()) {
                val privilegedAllowlist =
                    CredentialWebAuthnUtils.readPrivilegedAllowlistFromDiskOrThrow(context)
                return info.getOrigin(privilegedAllowlist)!!
            }

            val cert = info.signingInfo.apkContentsSigners[0].toByteArray()
            val md = MessageDigest.getInstance("SHA-256")
            val certHash = md.digest(cert)
            // This is the format for origin
            return "android:apk-key-hash:${base64UrlEncode(certHash)}"
        }
    }
}
