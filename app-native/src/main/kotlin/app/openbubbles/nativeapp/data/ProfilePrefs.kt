package app.openbubbles.nativeapp.data

import android.content.Context
import androidx.core.content.edit

class ProfilePrefs(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var firstName: String
        get() = prefs.getString(KEY_FIRST_NAME, "").orEmpty()
        set(value) = prefs.edit { putString(KEY_FIRST_NAME, value) }

    var lastName: String
        get() = prefs.getString(KEY_LAST_NAME, "").orEmpty()
        set(value) = prefs.edit { putString(KEY_LAST_NAME, value) }

    var displayName: String
        get() = prefs.getString(KEY_DISPLAY_NAME, "").orEmpty()
        set(value) = prefs.edit { putString(KEY_DISPLAY_NAME, value) }

    var avatarPath: String?
        get() = prefs.getString(KEY_AVATAR_PATH, null)
        set(value) = prefs.edit {
            if (value.isNullOrBlank()) remove(KEY_AVATAR_PATH) else putString(KEY_AVATAR_PATH, value)
        }

    var shareProfileJson: String?
        get() = prefs.getString(KEY_SHARE_PROFILE_JSON, null)
        set(value) = prefs.edit {
            if (value.isNullOrBlank()) remove(KEY_SHARE_PROFILE_JSON) else putString(KEY_SHARE_PROFILE_JSON, value)
        }

    var nameAndPhotoSharing: Boolean
        get() = prefs.getBoolean(KEY_NAME_AND_PHOTO_SHARING, false)
        set(value) = prefs.edit { putBoolean(KEY_NAME_AND_PHOTO_SHARING, value) }

    var shareAutomatically: Boolean
        get() = prefs.getBoolean(KEY_SHARE_AUTOMATICALLY, false)
        set(value) = prefs.edit { putBoolean(KEY_SHARE_AUTOMATICALLY, value) }

    fun wasSharedWith(address: String): Boolean =
        prefs.getStringSet(KEY_SHARED_CONTACTS, emptySet()).orEmpty().contains(address)

    fun markSharedWith(address: String) {
        val shared = prefs.getStringSet(KEY_SHARED_CONTACTS, emptySet()).orEmpty().toMutableSet()
        shared += address
        prefs.edit { putStringSet(KEY_SHARED_CONTACTS, shared) }
    }

    fun clearSharedContacts() = prefs.edit { remove(KEY_SHARED_CONTACTS) }

    companion object {
        private const val PREFS_NAME = "profile_prefs"
        private const val KEY_FIRST_NAME = "first_name"
        private const val KEY_LAST_NAME = "last_name"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_AVATAR_PATH = "avatar_path"
        private const val KEY_SHARE_PROFILE_JSON = "share_profile_json"
        private const val KEY_NAME_AND_PHOTO_SHARING = "name_and_photo_sharing"
        private const val KEY_SHARE_AUTOMATICALLY = "share_automatically"
        private const val KEY_SHARED_CONTACTS = "shared_contacts"
    }
}
