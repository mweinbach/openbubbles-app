package app.openbubbles.nativeapp.data.contacts

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.openbubbles.core.contacts.ConflictDecision
import app.openbubbles.core.contacts.ContactConflict
import app.openbubbles.core.contacts.ContactMergePolicy
import app.openbubbles.nativeapp.data.CoreGraph
import app.openbubbles.nativeapp.data.DeviceContacts
import app.openbubbles.nativeapp.data.DeviceContactsReadResult
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * "Save iCloud contacts to phone": mirrors the stored iCloud cards into the
 * device contacts store when the user opted in and granted WRITE_CONTACTS.
 *
 * Runs from three triggers behind one mutex — the tail of a successful
 * CardDAV pull, the 12h [ContactDeviceSyncWorker], and the moment the user
 * enables the toggle or answers a conflict. Every pass rebuilds the full
 * [ContactMergePolicy] plan from current state, so reruns and crashes
 * converge instead of compounding.
 */
object ContactDeviceSync {

    private const val TAG = "ContactDeviceSync"
    private const val PREFS = "contact_device_sync"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_CONFLICTS = "pending_conflicts"
    private const val DECISION_PREFIX = "decision:"
    private const val WORK_NAME = "openbubbles-contact-device-sync"

    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    data class SyncOutcome(val written: Int, val conflicts: Int)

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, false)

    /**
     * Persists the opt-in and (un)schedules the periodic job. The caller owns
     * the WRITE_CONTACTS request and the immediate first sync, both of which
     * need UI. Disabling keeps written contacts on the phone — removing them
     * would surprise anyone who toggled off just to pause syncing.
     */
    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(KEY_ENABLED, enabled) }
        if (enabled) schedule(context) else {
            runCatching { WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME) }
        }
    }

    /** Same shape as `UpdateCoordinator.schedule`: 12h, network, unique. */
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<ContactDeviceSyncWorker>(12, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()
        runCatching {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }
    }

    fun pendingConflicts(context: Context): List<ContactConflict> {
        val stored = prefs(context).getString(KEY_CONFLICTS, null) ?: return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(StoredConflict.serializer()), stored)
                .map { it.toConflict() }
        }.getOrDefault(emptyList())
    }

    /**
     * Remembers the user's answer so the same conflict is never re-asked,
     * and drops it from the pending list. The follow-up sync pass that
     * enacts USE_ICLOUD is the caller's job (it needs a coroutine).
     */
    fun recordDecision(context: Context, icloudId: String, decision: ConflictDecision) {
        val remaining = pendingConflicts(context).filterNot { it.icloudId == icloudId }
        prefs(context).edit {
            putString(DECISION_PREFIX + icloudId, decision.name)
            putString(KEY_CONFLICTS, encode(remaining))
        }
    }

    suspend fun syncNow(context: Context): SyncOutcome = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (!isEnabled(context) || !DeviceContactWriter.hasPermission(context)) {
                return@withContext SyncOutcome(written = 0, conflicts = 0)
            }
            val icloud = CoreGraph.icloudContacts()
            val device = when (val read = DeviceContacts.read(context)) {
                is DeviceContactsReadResult.Success -> read.snapshot.contacts
                DeviceContactsReadResult.PermissionDenied ->
                    return@withContext SyncOutcome(written = 0, conflicts = 0)
                is DeviceContactsReadResult.Failure -> throw read.cause
            }
            val ours = DeviceContactWriter.readOurs(context)
            val plan = ContactMergePolicy.plan(
                icloud = icloud,
                device = device,
                oursSourceIds = ours.keys,
                decisions = decisions(context),
            )
            val written = DeviceContactWriter.apply(context, plan, ours)
            prefs(context).edit { putString(KEY_CONFLICTS, encode(plan.conflicts)) }
            Log.i(
                TAG,
                "device contact pass: ${icloud.size} iCloud cards, $written written, " +
                    "${plan.deletions.size} removed, ${plan.conflicts.size} conflicts pending",
            )
            SyncOutcome(written = written, conflicts = plan.conflicts.size)
        }
    }

    private fun decisions(context: Context): Map<String, ConflictDecision> =
        prefs(context).all.entries.mapNotNull { (key, value) ->
            if (!key.startsWith(DECISION_PREFIX)) return@mapNotNull null
            val decision = (value as? String)?.let { name ->
                ConflictDecision.entries.firstOrNull { it.name == name }
            } ?: return@mapNotNull null
            key.removePrefix(DECISION_PREFIX) to decision
        }.toMap()

    private fun encode(conflicts: List<ContactConflict>): String =
        json.encodeToString(
            ListSerializer(StoredConflict.serializer()),
            conflicts.map { StoredConflict.of(it) },
        )

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Core's [ContactConflict] mirror; core does not apply the serialization plugin. */
    @Serializable
    private data class StoredConflict(
        val icloudId: String,
        val icloudName: String? = null,
        val icloudNumbers: List<String> = emptyList(),
        val deviceName: String? = null,
        val deviceNumbers: List<String> = emptyList(),
    ) {
        fun toConflict() = ContactConflict(icloudId, icloudName, icloudNumbers, deviceName, deviceNumbers)

        companion object {
            fun of(conflict: ContactConflict) = StoredConflict(
                icloudId = conflict.icloudId,
                icloudName = conflict.icloudName,
                icloudNumbers = conflict.icloudNumbers,
                deviceName = conflict.deviceName,
                deviceNumbers = conflict.deviceNumbers,
            )
        }
    }
}

/**
 * Periodic device write pass. CardDAV freshness is not a precondition: the
 * pass reads whatever iCloud rows ObjectBox holds, so it also repairs edits
 * a user made to our raw contacts from the phone's contacts app.
 */
class ContactDeviceSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        if (!ContactDeviceSync.isEnabled(context)) return Result.success()
        return runCatching { ContactDeviceSync.syncNow(context) }
            .fold(
                onSuccess = { Result.success() },
                onFailure = {
                    Log.w("ContactDeviceSync", "periodic pass failed: ${it.message}")
                    Result.retry()
                },
            )
    }
}
