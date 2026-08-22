package app.openbubbles.nativeapp.credentials

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.CancellationSignal
import android.os.OutcomeReceiver
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.CreateCredentialUnknownException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.provider.BeginCreateCredentialRequest
import androidx.credentials.provider.BeginCreateCredentialResponse
import androidx.credentials.provider.BeginGetCredentialRequest
import androidx.credentials.provider.BeginGetCredentialResponse
import androidx.credentials.provider.CallingAppInfo
import androidx.credentials.provider.CreateEntry
import androidx.credentials.provider.CredentialProviderService
import androidx.credentials.provider.ProviderClearCredentialStateRequest

import app.openbubbles.nativeapp.data.APNClient
import app.openbubbles.nativeapp.data.APNService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import uniffi.rust_lib_bluebubbles.AvailableGroupsCallback
import java.security.MessageDigest
import java.time.Instant

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class CredentialService : CredentialProviderService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

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
            val requestingPackage = request.callingAppInfo?.packageName.orEmpty()

            fun createIntent(groupId: String?): Intent =
                Intent(this, CredentialCreateActivity::class.java)
                    .setAction(
                        credentialPendingIntentAction(
                            requestingPackage,
                            request.type,
                            site = "create",
                            credentialId = groupId ?: "personal",
                            type = "create",
                        ),
                    )
                    .apply {
                        if (requestingPackage.isNotEmpty()) {
                            putExtra(CredentialIntentContract.EXTRA_PACKAGE_NAME, requestingPackage)
                        }
                        if (groupId != null) putExtra("group_id", groupId)
                    }

            val intent = createIntent(null)
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
                            val intent = createIntent(group.value)
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
        val query = CredentialEntries.parse(this, request)
        if (query == null) {
            callback.onResult(BeginGetCredentialResponse(emptyList()))
            return
        }
        val context = applicationContext
        // The system binds this service cold and expects one answer. The catalog
        // usually answers without touching Rust; when it cannot, the reducer
        // decides between a backend lookup and an unlock action, and either way
        // exactly one response is delivered.
        scope.launch {
            val response = try {
                CredentialEntries.respond(context, query)
            } catch (failure: Throwable) {
                Log.w(TAG, "credential query failed (${failure.javaClass.simpleName})")
                BeginGetCredentialResponse(emptyList())
            }
            callback.onResult(response)
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
        private const val TAG = "CredentialService"

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
