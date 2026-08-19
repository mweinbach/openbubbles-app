package app.openbubbles.nativeapp.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** How the app resolves light versus dark, independent of dynamic color. */
enum class ThemeMode(
    val persistedValue: String,
    val title: String,
    val description: String,
) {
    SYSTEM(
        persistedValue = "system",
        title = "System default",
        description = "Follow the Android light or dark setting",
    ),
    LIGHT(
        persistedValue = "light",
        title = "Light",
        description = "Always use the light theme",
    ),
    DARK(
        persistedValue = "dark",
        title = "Dark",
        description = "Always use the dark theme",
    ),
    ;

    fun resolvesToDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        fun fromPersistedValue(value: String?): ThemeMode =
            entries.firstOrNull { it.persistedValue == value } ?: SYSTEM
    }
}

/**
 * Appearance settings with observable state so the Compose theme recomposes
 * the moment a toggle flips (the theme reads [dynamicColorFlow] and
 * [themeModeFlow] directly).
 */
object AppearancePrefs {
    private const val PREFS_NAME = "appearance_prefs"
    private const val KEY_DYNAMIC_COLOR = "dynamic_color"
    private const val KEY_THEME_MODE = "theme_mode"

    private var prefs: SharedPreferences? = null

    private val _dynamicColor = MutableStateFlow(true)
    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)

    /** Wallpaper-derived (Material You) color; on by default, as before. */
    val dynamicColorFlow: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    /** Light/dark override; follows the system setting by default. */
    val themeModeFlow: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _dynamicColor.value = prefs?.getBoolean(KEY_DYNAMIC_COLOR, true) ?: true
        _themeMode.value = ThemeMode.fromPersistedValue(prefs?.getString(KEY_THEME_MODE, null))
    }

    var dynamicColor: Boolean
        get() = prefs?.getBoolean(KEY_DYNAMIC_COLOR, true) ?: true
        set(value) {
            prefs?.edit { putBoolean(KEY_DYNAMIC_COLOR, value) }
            _dynamicColor.value = value
        }

    var themeMode: ThemeMode
        get() = ThemeMode.fromPersistedValue(prefs?.getString(KEY_THEME_MODE, null))
        set(value) {
            prefs?.edit { putString(KEY_THEME_MODE, value.persistedValue) }
            _themeMode.value = value
        }
}
