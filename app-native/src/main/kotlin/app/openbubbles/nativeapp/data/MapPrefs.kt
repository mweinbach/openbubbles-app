package app.openbubbles.nativeapp.data

import android.content.Context
import androidx.core.content.edit

private const val PREFS_NAME = "map_prefs"
private const val KEY_IMAGERY_ENABLED = "imageryEnabled"

/**
 * Whether the in-app map draws real imagery.
 *
 * Fetching a tile tells whoever serves it roughly where the thing being tracked
 * is, so this is a deliberate, per-user switch rather than a hidden default.
 * With imagery off the map still works — pins, accuracy and tracks are drawn on
 * a plain graticule — so turning it off costs context, not function.
 */
class MapPrefs(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var imageryEnabled: Boolean
        get() = prefs.getBoolean(KEY_IMAGERY_ENABLED, true)
        set(value) {
            prefs.edit { putBoolean(KEY_IMAGERY_ENABLED, value) }
        }
}
