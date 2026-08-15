package app.openbubbles.nativeapp

import android.app.Application
import android.content.ComponentCallbacks2
import app.openbubbles.nativeapp.data.AppContext
import app.openbubbles.nativeapp.data.MemoryCaches

/** Initializes process-scoped dependencies before any Android component runs. */
class OpenBubblesApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContext.initialize(this)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            MemoryCaches.clear()
        }
    }

    override fun onLowMemory() {
        MemoryCaches.clear()
        super.onLowMemory()
    }
}
