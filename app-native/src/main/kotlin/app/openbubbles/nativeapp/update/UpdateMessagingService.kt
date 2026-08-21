package app.openbubbles.nativeapp.update

import android.content.Context
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

/** Receives a trusted wake-up hint; Update Ledger's signed metadata stays authoritative. */
class UpdateMessagingService : FirebaseMessagingService() {
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
            FirebaseMessaging.getInstance().subscribeToTopic(UpdatePushContract.TOPIC)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        AppTelemetry.event(context, "ota_topic_ready")
                    } else {
                        AppTelemetry.nonFatal("ota_topic", "subscribe_failed")
                    }
                }
        }
    }
}
