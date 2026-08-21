package app.openbubbles.nativeapp

import android.app.Application
import android.content.ComponentCallbacks2
import app.openbubbles.nativeapp.data.AppContext
import app.openbubbles.nativeapp.data.MemoryCaches
import app.openbubbles.nativeapp.data.reconcileAbandonedOutgoingDrafts
import app.openbubbles.nativeapp.telemetry.AppTelemetry
import app.openbubbles.nativeapp.update.UpdateMessagingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Initializes process-scoped dependencies before any Android component runs. */
class OpenBubblesApplication : Application() {
    private val maintenanceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        AppTelemetry.initialize(this)
        AppContext.initialize(this)
        maintenanceScope.launch {
            runCatching { reconcileAbandonedOutgoingDrafts(cacheDir) }
        }
        val updatesEnabled = BuildConfig.DEBUG || BuildConfig.FIREBASE_TELEMETRY_ENABLED
        if (!BuildConfig.PERFORMANCE_TEST && updatesEnabled) {
            UpdateMessagingService.subscribeToUpdates(this)
        }
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
