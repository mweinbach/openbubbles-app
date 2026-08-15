package app.openbubbles.nativeapp.data

import android.content.Context

/**
 * Process-wide application context, initialized before any activity, service,
 * worker, or manifest receiver can access [CoreGraph].
 */
object AppContext {
    @Volatile
    private var value: Context? = null

    val current: Context?
        get() = value

    fun initialize(context: Context) {
        value = context.applicationContext
    }
}
