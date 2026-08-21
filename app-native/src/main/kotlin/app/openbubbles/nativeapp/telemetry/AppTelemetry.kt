package app.openbubbles.nativeapp.telemetry

import android.content.Context
import android.os.Bundle
import app.openbubbles.nativeapp.BuildConfig
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Privacy boundary for production diagnostics.
 *
 * Callers may pass only coarse, enumerated state. Never pass message text,
 * handles, account identifiers, Firebase tokens, URLs, or native error bodies.
 */
object AppTelemetry {
    private const val MAX_VALUE_LENGTH = 80

    fun initialize(context: Context) {
        val enabled = BuildConfig.FIREBASE_TELEMETRY_ENABLED
        FirebaseAnalytics.getInstance(context).setAnalyticsCollectionEnabled(enabled)
        FirebaseCrashlytics.getInstance().apply {
            setCrashlyticsCollectionEnabled(enabled)
            if (enabled) {
                setCustomKey("distribution", "direct")
                setCustomKey("update_source", "update_ledger")
                setCustomKey("telemetry_schema", 1)
                log("app_process_started")
            }
        }
    }

    fun event(context: Context, name: String, parameters: Map<String, String> = emptyMap()) {
        if (!BuildConfig.FIREBASE_TELEMETRY_ENABLED) return
        val safe = parameters.mapValues { (_, value) -> value.take(MAX_VALUE_LENGTH) }
        FirebaseAnalytics.getInstance(context).logEvent(
            name,
            Bundle().apply { safe.forEach(::putString) },
        )
        FirebaseCrashlytics.getInstance().log(
            buildString {
                append(name)
                safe.toSortedMap().forEach { (key, value) -> append(" ").append(key).append("=").append(value) }
            },
        )
    }

    fun state(key: String, value: String) {
        if (!BuildConfig.FIREBASE_TELEMETRY_ENABLED) return
        FirebaseCrashlytics.getInstance().setCustomKey(key, value.take(MAX_VALUE_LENGTH))
    }

    /** Records only a sanitized category; the original throwable is never uploaded. */
    fun nonFatal(scope: String, kind: String) {
        if (!BuildConfig.FIREBASE_TELEMETRY_ENABLED) return
        FirebaseCrashlytics.getInstance().recordException(
            SanitizedDiagnosticException(scope.take(40), kind.take(40)),
        )
    }

    private class SanitizedDiagnosticException(scope: String, kind: String) :
        RuntimeException("$scope:$kind")
}
