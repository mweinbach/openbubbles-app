package app.openbubbles.nativeapp

import android.app.Application
import android.content.ComponentCallbacks2
import app.openbubbles.nativeapp.data.AppContext
import app.openbubbles.nativeapp.data.MemoryCaches
import app.openbubbles.nativeapp.telemetry.AppTelemetry
import app.openbubbles.nativeapp.update.UpdateMessagingService

/** Initializes process-scoped dependencies before any Android component runs. */
class OpenBubblesApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppTelemetry.initialize(this)
        AppContext.initialize(this)
        UpdateMessagingService.subscribeToUpdates(this)
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
