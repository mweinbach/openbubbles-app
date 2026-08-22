package app.openbubbles.nativeapp.update

import android.content.Context
import app.openbubbles.nativeapp.BuildConfig
import app.openbubbles.nativeapp.telemetry.AppTelemetry
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

internal data class UpdatePushPayload(
    val version: String,
    val build: Long,
)

internal object UpdatePushContract {
    const val TOPIC = "update-ledger-openbubbles-stable"
    private val VERSION = Regex("^[0-9A-Za-z][0-9A-Za-z._+-]{0,39}$")

    fun parse(data: Map<String, String>): UpdatePushPayload? {
        if (data["type"] != "update_available") return null
        if (data["project"] != "openbubbles" || data["channel"] != "stable") return null
        val version = data["version"]?.takeIf(VERSION::matches) ?: return null
        val build = data["build"]?.toLongOrNull()?.takeIf { it > 0 } ?: return null
        return UpdatePushPayload(version, build)
    }
}

/** Mirrors startup policy without retaining or exposing the refreshed installation token. */
internal fun refreshUpdateTopicForNewToken(
    token: String,
    debugBuild: Boolean,
    firebaseTelemetryEnabled: Boolean,
    performanceTest: Boolean,
    resubscribe: () -> Unit,
): Boolean {
    if (token.isBlank() || performanceTest || !(debugBuild || firebaseTelemetryEnabled)) {
        return false
    }
    resubscribe()
    return true
}

/** Receives a trusted wake-up hint; Update Ledger's signed metadata stays authoritative. */
class UpdateMessagingService : FirebaseMessagingService() {
    @Deprecated("Required by Firebase's default token-based registration mode")
    override fun onNewToken(token: String) {
        refreshUpdateTopicForNewToken(
            token = token,
            debugBuild = BuildConfig.DEBUG,
            firebaseTelemetryEnabled = BuildConfig.FIREBASE_TELEMETRY_ENABLED,
            performanceTest = BuildConfig.PERFORMANCE_TEST,
        ) {
            // Firebase durably schedules and retries topic subscriptions; the
            // application context survives this short-lived messaging service.
            subscribeToUpdates(applicationContext)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val payload = UpdatePushContract.parse(message.data)
        if (payload == null) {
            AppTelemetry.event(this, "ota_push_ignored", mapOf("reason" to "invalid_contract"))
            return
        }
        AppTelemetry.event(
            this,
            "ota_push_received",
            mapOf("version" to payload.version, "build" to payload.build.toString()),
        )
        UpdateCoordinator.notifyUpdateAvailable(this, payload.version)
        UpdateCoordinator.enqueueImmediateCheck(this, trigger = "push", replace = true)
    }

    override fun onDeletedMessages() {
        AppTelemetry.event(this, "ota_push_gap")
        UpdateCoordinator.enqueueImmediateCheck(this, trigger = "push_gap", replace = true)
    }

    companion object {
        fun subscribeToUpdates(context: Context) {
            val applicationContext = context.applicationContext
            FirebaseMessaging.getInstance().subscribeToTopic(UpdatePushContract.TOPIC)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        AppTelemetry.event(applicationContext, "ota_topic_ready")
                    } else {
                        AppTelemetry.nonFatal("ota_topic", "subscribe_failed")
                    }
                }
        }
    }
}
