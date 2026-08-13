package com.bluebubbles.messaging

import android.util.Log
import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.content.Intent
import com.bluebubbles.messaging.services.backend_ui_interop.MethodCallHandler
import com.bluebubbles.messaging.services.extension.KeyboardViewFactory
import com.bluebubbles.messaging.services.extension.LiveExtensionFactory
import com.bluebubbles.messaging.services.foreground.ForegroundServiceBroadcastReceiver
import com.bluebubbles.messaging.Constants
import com.bluebubbles.messaging.services.rustpush.APNService
import com.bluebubbles.messaging.services.system.CreateDocumentHandler
import com.bluebubbles.messaging.services.system.EnableBTHandler
import com.google.firebase.firestore.FirebaseFirestoreException
import io.flutter.embedding.android.FlutterFragmentActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.io.FileInputStream

class MainActivity : FlutterFragmentActivity() {
    companion object {
        private val engineLock = Any()
        @Volatile private var _engine: FlutterEngine? = null
        @Volatile private var _engineReady: Boolean = false
        
        fun getEngine(): FlutterEngine? {
            synchronized(engineLock) {
                return _engine
            }
        }
        
        fun setEngine(newEngine: FlutterEngine?) {
            synchronized(engineLock) {
                _engine = newEngine
            }
        }

        fun isEngineReady(): Boolean {
            synchronized(engineLock) {
                return _engineReady
            }
        }

        fun setEngineReady(ready: Boolean) {
            synchronized(engineLock) {
                _engineReady = ready
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(Intent(this, APNService::class.java))
        } else {
            startService(Intent(this, APNService::class.java))
        }
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        setEngine(flutterEngine)
        setEngineReady(false)
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, Constants.methodChannel).setMethodCallHandler {
            call, result ->
            if (call.method == "engine-done") {
                Log.i("BBEngine", "Destroyed")
                flutterEngine.destroy()
                if (getEngine() == flutterEngine) {
                    setEngine(null)
                }
            }
            MethodCallHandler().methodCallHandler(call, result, this)
        }
        flutterEngine.platformViewsController.registry.registerViewFactory("extension-keyboard", KeyboardViewFactory())
        flutterEngine.platformViewsController.registry.registerViewFactory("extension-live", LiveExtensionFactory())

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val cause = throwable.cause ?: throwable
            if (cause is FirebaseFirestoreException) {
                when (cause.code) {
                    FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                        Log.e(Constants.logTag, "Firestore: PERMISSION_DENIED — missing or insufficient security rules (${cause.message})")
                    FirebaseFirestoreException.Code.UNAVAILABLE ->
                        Log.e(Constants.logTag, "Firestore: UNAVAILABLE — service unreachable, check network connectivity (${cause.message})")
                    FirebaseFirestoreException.Code.UNAUTHENTICATED ->
                        Log.e(Constants.logTag, "Firestore: UNAUTHENTICATED — request not authenticated (${cause.message})")
                    FirebaseFirestoreException.Code.NOT_FOUND ->
                        Log.e(Constants.logTag, "Firestore: NOT_FOUND — document or collection does not exist (${cause.message})")
                    FirebaseFirestoreException.Code.CANCELLED ->
                        Log.d(Constants.logTag, "Firestore: CANCELLED — listener was cancelled (${cause.message})")
                    FirebaseFirestoreException.Code.ALREADY_EXISTS ->
                        Log.w(Constants.logTag, "Firestore: ALREADY_EXISTS — document already exists (${cause.message})")
                    FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED ->
                        Log.e(Constants.logTag, "Firestore: RESOURCE_EXHAUSTED — quota exceeded (${cause.message})")
                    FirebaseFirestoreException.Code.FAILED_PRECONDITION ->
                        Log.e(Constants.logTag, "Firestore: FAILED_PRECONDITION — operation rejected, check indexes or state (${cause.message})")
                    FirebaseFirestoreException.Code.ABORTED ->
                        Log.e(Constants.logTag, "Firestore: ABORTED — transaction conflict or contention (${cause.message})")
                    FirebaseFirestoreException.Code.INTERNAL ->
                        Log.e(Constants.logTag, "Firestore: INTERNAL — internal server error (${cause.message})")
                    FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
                        Log.e(Constants.logTag, "Firestore: DEADLINE_EXCEEDED — operation timed out (${cause.message})")
                    else ->
                        Log.e(Constants.logTag, "Firestore: unhandled error ${cause.code} (${cause.message})")
                }
            } else {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    override fun onDestroy() {
        Log.d(Constants.logTag, "BlueBubbles MainActivity is being destroyed")
        MethodCallHandler.clearNotificationListenerResult()
        setEngine(null)
        setEngineReady(false)

        // If we are finishing "gracefully", the dart code would have started the foreground service.
        // If we are finishing because the system is destroying the activity, we need to start the foreground service
        // via a broadcast intent.
        if (isFinishing) {
            Log.d(Constants.logTag, "BlueBubbles activity is finishing")
        } else {
            Log.d(Constants.logTag, "BlueBubbles activity is being destroyed by the system")

            val prefs = applicationContext.getSharedPreferences("FlutterSharedPreferences", 0)
            val keepAppAlive: Boolean = prefs.getBoolean("flutter.keepAppAlive", false)

            // Create an intent to start the foreground service
            if (keepAppAlive) {
                Log.d(Constants.logTag, "Creating broadcast intent to restart the foreground service...")
                val broadcastIntent = Intent(this, ForegroundServiceBroadcastReceiver::class.java)
                broadcastIntent.setAction("restartservice");
                sendBroadcast(broadcastIntent);
            }
        }

        try {
            super.onDestroy()
        } catch (e: ConcurrentModificationException) {
            Log.d(Constants.logTag, "Caught ConcurrentModificationException when destroying MainActivity")
            Log.e(Constants.logTag, e.stackTraceToString())
        } catch (e: Exception) {
            Log.d(Constants.logTag, "Caught unhandled Exception when destroying MainActivity")
            Log.e(Constants.logTag, e.stackTraceToString())
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == Constants.notificationListenerRequestCode) {
            MethodCallHandler.consumeNotificationListenerResult()?.success(resultCode == Activity.RESULT_OK)
        }
        if (requestCode == Constants.documentSaveRequestCode) {
            var result = CreateDocumentHandler.savedResult!!
            CreateDocumentHandler.savedResult = null
            val uri = data?.data
            if (uri == null) {
                result.success(null)
                return
            }

            try {
                val output = contentResolver.openOutputStream(uri)!!
                val input = FileInputStream(CreateDocumentHandler.savedPath!!)
                input.copyTo(output)
                output.close()
                input.close()
                result.success(null)
            } catch (e: Exception) {
                result.error("FILE_WRITE_ERROR", e.message, null)
            }
        }
        if (requestCode == Constants.enableBtRequestCode) {
            var result = EnableBTHandler.savedResult!!
            result.success(true)
        }
    }
}
