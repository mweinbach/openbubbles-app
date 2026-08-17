package app.openbubbles.nativeapp.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import app.openbubbles.core.contacts.RawContact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

    suspend fun read(context: Context): List<RawContact> = withContext(Dispatchers.IO) {
        if (!hasPermission(context)) {
            emptyList()
        } else {
            runCatching { readLocked(context) }.getOrDefault(emptyList())
        }
    }

    private fun readLocked(context: Context): List<RawContact> {
        val out = mutableListOf<RawContact>()
        context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME,
                ContactsContract.Contacts.PHOTO_URI,
                ContactsContract.Contacts.PHOTO_THUMBNAIL_URI,
                ContactsContract.Contacts.IN_VISIBLE_GROUP,
            ),
            null, null, null,
        )?.use { cursor ->
            val addressesById = readAddresses(context)
            while (cursor.moveToNext()) {
                val id = cursor.getString(0) ?: continue
                val name = cursor.getString(1)
                val photo = cursor.getString(2)?.takeIf { it.isNotBlank() }
                    ?: cursor.getString(3)?.takeIf { it.isNotBlank() }
                val addresses = addressesById[id].orEmpty()
                if (addresses.isEmpty()) continue // unreachable over iMessage/SMS anyway
                val (first, last) = splitName(name)
                out += RawContact(
                    id = id,
                    displayName = name,
                    firstName = first,
                    lastName = last,
                    avatarPath = photo,
                    addresses = addresses,
                )
            }
        }
        return out
    }

    private fun readAddresses(context: Context): Map<String, List<String>> {
        val byId = mutableMapOf<String, MutableList<String>>()
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Email.CONTACT_ID,
                ContactsContract.CommonDataKinds.Email.ADDRESS,
            ),
            null, null, null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0) ?: continue
                val email = cursor.getString(1) ?: continue
                byId.getOrPut(id) { mutableListOf() }.add(email)
            }
        }
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
            ),
            null, null, null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0) ?: continue
                val number = cursor.getString(1) ?: continue
                byId.getOrPut(id) { mutableListOf() }.add(number)
            }
        }
        return byId
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
