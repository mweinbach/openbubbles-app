package app.openbubbles.nativeapp.data

import android.annotation.SuppressLint
import android.content.Context

/**
 * Process-wide application context, initialized before any activity, service,
 * worker, or manifest receiver can access [CoreGraph].
 */
@SuppressLint("StaticFieldLeak") // Stores applicationContext only, never an Activity.
object AppContext {
    @SuppressLint("StaticFieldLeak") // Application context, set once at process start.
    @Volatile
    private var value: Context? = null

    val current: Context?
        get() = value

    fun initialize(context: Context) {
        value = context.applicationContext
    }
}
