package app.openbubbles.nativeapp.data

import android.content.Context
import androidx.core.content.edit
import app.openbubbles.nativeapp.ui.navigation.TopLevelSurfaceOrder
import app.openbubbles.nativeapp.ui.navigation.TopLevelSurfaceOrderCodec

private const val PREFS_NAME = "surface_switcher_prefs"
private const val KEY_ORDER = "surfaceOrder"

/**
 * Header-switcher configuration: which surfaces are offered, in which order,
 * and which one a fresh launch opens.
 *
 * One versioned string holds the whole model so order and default can never
 * disagree. Reading always goes through
 * [TopLevelSurfaceOrderCodec.decode], which sanitizes stale or corrupt values
 * back to [TopLevelSurfaceOrder.Default] instead of failing. No account data or
 * secret is stored here.
 */
class SurfaceSwitcherPrefs(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var order: TopLevelSurfaceOrder
        get() = TopLevelSurfaceOrderCodec.decode(prefs.getString(KEY_ORDER, null))
        set(value) {
            prefs.edit { putString(KEY_ORDER, TopLevelSurfaceOrderCodec.encode(value)) }
        }
}
