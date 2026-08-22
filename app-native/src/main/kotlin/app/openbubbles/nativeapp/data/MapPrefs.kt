package app.openbubbles.nativeapp.data

import android.content.Context
import androidx.core.content.edit

private const val PREFS_NAME = "map_prefs"
private const val KEY_IMAGERY_ENABLED = "imageryEnabled"

internal fun resolveMapImageryPreference(storedPreference: Boolean?): Boolean =
    storedPreference ?: true

/**
 * Whether the in-app map draws real imagery.
 *
 * Imagery starts enabled so Find My opens as a useful map. Fetching a tile tells
 * its provider roughly where the tracked thing is, so users can still turn the
 * layer off and that explicit choice stays persisted.
 * With imagery off the map still works — pins, accuracy and tracks are drawn on
 * a plain graticule — so turning it off costs context, not function.
 */
class MapPrefs(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var imageryEnabled: Boolean
        get() = resolveMapImageryPreference(
            if (prefs.contains(KEY_IMAGERY_ENABLED)) {
                prefs.getBoolean(KEY_IMAGERY_ENABLED, false)
            } else {
                null
            },
        )
        set(value) {
            prefs.edit { putBoolean(KEY_IMAGERY_ENABLED, value) }
        }
}
