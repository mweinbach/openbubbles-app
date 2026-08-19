package app.openbubbles.nativeapp.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import app.openbubbles.core.contacts.AvatarUpdate
import app.openbubbles.core.contacts.ContactSync
import app.openbubbles.core.contacts.DeviceContactSnapshot
import app.openbubbles.core.contacts.RawContact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface DeviceContactsReadResult {
    data class Success(val snapshot: DeviceContactSnapshot) : DeviceContactsReadResult
    data object PermissionDenied : DeviceContactsReadResult
    data class Failure(val cause: Throwable) : DeviceContactsReadResult
}

/**
 * Runs reconciliation only for a complete successful provider snapshot.
 * In particular, successful empty snapshots are authoritative while denied
 * and failed reads are not.
 */
internal inline fun DeviceContactsReadResult.applySuccessfulSnapshot(
    reconcile: (DeviceContactSnapshot) -> Unit,
): Boolean = when (this) {
    is DeviceContactsReadResult.Success -> {
        reconcile(snapshot)
        true
    }
    DeviceContactsReadResult.PermissionDenied,
    is DeviceContactsReadResult.Failure,
    -> false
}

internal fun stableAndroidContactId(lookupKey: String): String =
    ContactSync.DEVICE_CONTACT_PREFIX + lookupKey

/**
 * Reads the device contacts provider into [RawContact] rows for
 * [app.openbubbles.core.contacts.ContactSync]. Avatars are referenced by
 * lookup URI; ContactSync stores whatever path string we hand it, so we
 * pass the photo URI when present.
 */
object DeviceContacts {

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    suspend fun read(context: Context): DeviceContactsReadResult = withContext(Dispatchers.IO) {
        if (!hasPermission(context)) {
            DeviceContactsReadResult.PermissionDenied
        } else {
            runCatching { DeviceContactsReadResult.Success(readLocked(context)) }
                .getOrElse(DeviceContactsReadResult::Failure)
        }
    }

    private fun readLocked(context: Context): DeviceContactSnapshot {
        val out = mutableListOf<RawContact>()
        val legacyIds = LinkedHashMap<String, String>()
        val addressesById = readAddresses(context)
        val cursor = context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.LOOKUP_KEY,
                ContactsContract.Contacts.DISPLAY_NAME,
                ContactsContract.Contacts.PHOTO_URI,
                ContactsContract.Contacts.PHOTO_THUMBNAIL_URI,
                ContactsContract.Contacts.IN_VISIBLE_GROUP,
            ),
            null, null, null,
        ) ?: error("ContactsProvider returned no contacts cursor")
        cursor.use {
            while (cursor.moveToNext()) {
                val rowId = cursor.getString(0)
                    ?: error("ContactsProvider returned a contact without _ID")
                val addresses = addressesById[rowId].orEmpty()
                if (addresses.isEmpty()) continue // unreachable over iMessage/SMS anyway
                val lookupKey = cursor.getString(1)?.takeIf(String::isNotBlank)
                    ?: error("ContactsProvider returned addressable contact $rowId without LOOKUP_KEY")
                val id = stableAndroidContactId(lookupKey)
                val name = cursor.getString(2)
                val photo = cursor.getString(3)?.takeIf { it.isNotBlank() }
                    ?: cursor.getString(4)?.takeIf { it.isNotBlank() }
                val (first, last) = splitName(name)
                out += RawContact(
                    id = id,
                    displayName = name,
                    firstName = first,
                    lastName = last,
                    avatarUpdate = photo?.let(AvatarUpdate::Set) ?: AvatarUpdate.Clear,
                    addresses = addresses,
                )
                legacyIds[id] = rowId
            }
        }
        return DeviceContactSnapshot(
            contacts = out,
            legacyNativeIds = legacyIds,
        )
    }

    private fun readAddresses(context: Context): Map<String, List<String>> {
        val byId = mutableMapOf<String, MutableList<String>>()
        val emailCursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Email.CONTACT_ID,
                ContactsContract.CommonDataKinds.Email.ADDRESS,
            ),
            null, null, null,
        ) ?: error("ContactsProvider returned no email cursor")
        emailCursor.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0) ?: continue
                val email = cursor.getString(1) ?: continue
                byId.getOrPut(id) { mutableListOf() }.add(email)
            }
        }
        val phoneCursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
            ),
            null, null, null,
        ) ?: error("ContactsProvider returned no phone cursor")
        phoneCursor.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0) ?: continue
                val number = cursor.getString(1) ?: continue
                byId.getOrPut(id) { mutableListOf() }.add(number)
            }
        }
        return byId.mapValues { (_, addresses) -> addresses.distinct() }
    }

    private fun splitName(displayName: String?): Pair<String?, String?> {
        displayName ?: return null to null
        return when (displayName.count { it == ' ' }) {
            0 -> displayName to null
            else -> {
                val idx = displayName.indexOf(' ')
                displayName.substring(0, idx) to displayName.substring(idx + 1)
            }
        }
    }
}
