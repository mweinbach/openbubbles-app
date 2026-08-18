package app.openbubbles.nativeapp.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Appearance settings with observable state so the Compose theme recomposes
 * the moment a toggle flips (the theme reads [dynamicColorFlow] directly).
 */
object AppearancePrefs {
    private const val PREFS_NAME = "appearance_prefs"
    private const val KEY_DYNAMIC_COLOR = "dynamic_color"

    private var prefs: SharedPreferences? = null

    private val _dynamicColor = MutableStateFlow(true)

    /** Wallpaper-derived (Material You) color; on by default, as before. */
    val dynamicColorFlow: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _dynamicColor.value = prefs?.getBoolean(KEY_DYNAMIC_COLOR, true) ?: true
    }

    var dynamicColor: Boolean
        get() = prefs?.getBoolean(KEY_DYNAMIC_COLOR, true) ?: true
        set(value) {
            prefs?.edit { putBoolean(KEY_DYNAMIC_COLOR, value) }
            _dynamicColor.value = value
        }
}
