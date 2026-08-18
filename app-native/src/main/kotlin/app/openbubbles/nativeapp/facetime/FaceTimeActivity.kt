package app.openbubbles.nativeapp.facetime

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.database.ContentObserver
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Rational
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import android.view.WindowInsets
import android.view.WindowManager
import android.webkit.PermissionRequest
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.marginTop
import androidx.core.view.updateLayoutParams
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import app.openbubbles.nativeapp.R
import app.openbubbles.nativeapp.databinding.ActivityFaceTimeBinding
import app.openbubbles.nativeapp.service.FaceTimeDispatch
import app.openbubbles.nativeapp.ui.adaptive.FaceTimeTabletopInsets
import app.openbubbles.nativeapp.ui.adaptive.faceTimeTabletopInsets
import app.openbubbles.nativeapp.ui.adaptive.isTabletopFold
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private fun lerp(start: Float, stop: Float, fraction: Float): Float =
    start + (stop - start) * fraction

class FaceTimeActivity : Activity() {
    private lateinit var binding: ActivityFaceTimeBinding

    private var pendingPermissionRequest: PermissionRequest? = null
    private val permissionMap = mapOf(
        PermissionRequest.RESOURCE_VIDEO_CAPTURE to listOf(Manifest.permission.CAMERA),
        PermissionRequest.RESOURCE_AUDIO_CAPTURE to listOf(Manifest.permission.RECORD_AUDIO),
    )
    var isCall = false
    var answered = false
    private var mirrorReady = false
    private var notificationId = 0
    var callUuid: String? = null
    private lateinit var cached: CachedWebview

    private lateinit var webView: WebView
    private var initialMediaVolume: Int? = null;
    private val callTimeoutHandler = Handler(Looper.getMainLooper())
    private var connected = false
    private val outgoingCallTimeout = Runnable {
        if (!isCall && !connected) {
            dispatchCallAction(FaceTimeActionReceiver.ACTION_END)
        }
    }
    private val foldScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var lastFoldFeature: FoldingFeature? = null
    private var defaultCallDescriptionTopMargin: Int? = null

    companion object {
        @SuppressLint("StaticFieldLeak") // Cleared in onDestroy when this instance is the active call.
        var activeFaceTimeActivity: FaceTimeActivity? = null
        @SuppressLint("StaticFieldLeak") // Process-wide prerender cache; transferred or dropped when a call starts.
        var cachedWebview: CachedWebview? = null
    }

    fun endCall() {
        webView.loadUrl("javascript:document.getElementById(\"callcontrols-leave-button-session-banner\").click()")
    }

    /** Close visual call state after a remote response or a receiver action. */
    fun closeCallUi() {
        runOnUiThread { finishAndRemoveTask() }
    }

    fun markConnected() {
        runOnUiThread {
            connected = true
            callTimeoutHandler.removeCallbacks(outgoingCallTimeout)
        }
    }

    private fun dispatchCallAction(action: String) {
        val guid = callUuid ?: return
        sendBroadcast(
            Intent(this, FaceTimeActionReceiver::class.java)
                .setAction(action)
                .putExtra(FaceTimeActionReceiver.EXTRA_CALL_UUID, guid)
                .putExtra(FaceTimeActionReceiver.EXTRA_NOTIFICATION_ID, notificationId),
        )
    }

    private fun hideControlsForPIP() {
        webView.loadUrl("javascript:if (document.querySelector(\".session-banner\").style.opacity == 1) { document.getElementById(\"canvas-layout-container\").click() }")
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration?
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (isInPictureInPictureMode) {
            hideControlsForPIP()
        }
    }

    private fun decline() {
        // delete notification
        if (notificationId != 0) {
            runCatching { getSystemService(android.app.NotificationManager::class.java)?.cancel(FtConstants.NEW_FACE_TIME_NOTIFICATION_TAG, notificationId) }
        }
        dispatchCallAction(FaceTimeActionReceiver.ACTION_DECLINE)
    }

    private fun invLerp(a: Int, b: Int, x: Int): Float {
        return (x - a).toFloat() / (b - a).toFloat()
    }

    private fun updateMediaVolume(audioManager: AudioManager) {
        try {
            val progress = invLerp(
                audioManager.getStreamMinVolumeCompat(AudioManager.STREAM_VOICE_CALL),
                audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL),
                audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL),
            )
            val volume = lerp(
                audioManager.getStreamMinVolumeCompat(AudioManager.STREAM_MUSIC).toFloat(),
                audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat(),
                progress
            ).roundToInt()
            audioManager.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                volume,
                0
            )
        } catch (e: SecurityException) {
            Log.w("FaceTime", "Unable to set stream volume!")
        }

    }

    var contentObserver: ContentObserver? = null

    private fun handlePermissionRequests() {
        for (request in cached.deferredRequests) {
            handlePermissionRequest(request)
        }
        cached.deferredRequests.clear()
        cached.deferredRequestsUpdated = {
            for (request in cached.deferredRequests) {
                handlePermissionRequest(request)
            }
            cached.deferredRequests.clear()
        }

        // weird bug where it uses the Music stream but the default stream is set to call
        // you want it maxed. Trust me. And if you don't the UI will open so you know :)
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        initialMediaVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        updateMediaVolume(audioManager)
        val observer = object : ContentObserver(
            Handler(Looper.getMainLooper())
        ) {
            override fun deliverSelfNotifications(): Boolean {
                return false
            }

            override fun onChange(selfChange: Boolean) {
                updateMediaVolume(audioManager)
            }
        }
        applicationContext.contentResolver.registerContentObserver(android.provider.Settings.System.CONTENT_URI, true, observer)
        contentObserver = observer
    }

    private fun answerCall() {
        answered = true

        handlePermissionRequests()

        if (notificationId != 0) {
            runCatching { getSystemService(android.app.NotificationManager::class.java)?.cancel(FtConstants.NEW_FACE_TIME_NOTIFICATION_TAG, notificationId) }
        }

        if (mirrorReady) {
            showInCallSurface()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                window.setBackgroundBlurRadius(0)
            }
            webView.loadUrl("javascript:document.getElementById(\"callcontrols-join-button-session-banner\").click()")
        } else {
            connecting()
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFaceTimeBinding.inflate(layoutInflater)

        val extras = intent?.extras
        val link = extras?.getString("link")
        if (extras == null || link == null || secureWebOrigin(link) == null) {
            Log.w("FaceTime", "refusing missing or non-HTTPS FaceTime link")
            finishAndRemoveTask()
            return
        }

        activeFaceTimeActivity = this

        // Deprecated on 35+ where edge-to-edge already renders bars
        // transparent; still required for the pre-35 devices we support.
        @Suppress("DEPRECATION")
        window.statusBarColor = Color.TRANSPARENT
        @Suppress("DEPRECATION")
        window.navigationBarColor = Color.TRANSPARENT

        WindowCompat.setDecorFitsSystemWindows(window, false)


        // show when locked
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            // setShowWhenLocked/setTurnScreenOn need API 27; this branch only
            // runs on API 26 (minSdk), where the flags are the only option.
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        handleConfig(extras)
        binding.mainFrame.addView(webView)

        binding.accept.setOnClickListener {
            answerCall()
        }

        binding.reject.setOnClickListener {
            decline()
        }



        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val sourceRectHint = Rect()
            webView.getGlobalVisibleRect(sourceRectHint)

            val intentWithData = Intent(
                this,
                FaceTimeActionReceiver::class.java
            ).setAction(FaceTimeActionReceiver.ACTION_END)
                .putExtra(FaceTimeActionReceiver.EXTRA_CALL_UUID, callUuid)
                .putExtra(FaceTimeActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)

            setPictureInPictureParams(
                PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(1, 1))
                    .setActions(listOf(
                        RemoteAction(
                            Icon.createWithResource(this, R.drawable.call_end),
                            "End Call",
                            "End this FaceTime Call",
                            PendingIntent.getBroadcast(
                                this,
                                callUuid.hashCode(),
                                intentWithData,
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                            )
                        )
                    ))
                    .setSourceRectHint(sourceRectHint)
                    .setAutoEnterEnabled(true)
                    .build())

            val mOnLayoutChangeListener =
                View.OnLayoutChangeListener { v: View?, oldLeft: Int,
                                              oldTop: Int, oldRight: Int, oldBottom: Int, newLeft: Int, newTop:
                                              Int, newRight: Int, newBottom: Int ->
                    val sourceRectHint = Rect()
                    webView.getGlobalVisibleRect(sourceRectHint)
                    val builder = PictureInPictureParams.Builder()
                        .setSourceRectHint(sourceRectHint)
                    setPictureInPictureParams(builder.build())
                }

            webView.addOnLayoutChangeListener(mOnLayoutChangeListener)
        }

        val view = binding.root
        setContentView(view)
        observeFoldingFeatures()
    }

    /**
     * Incoming splash: poster/identity above a tabletop hinge, accept/decline
     * below. In-call: letterbox the WebView above that hinge so call chrome
     * does not sit on the crease.
     */
    private fun observeFoldingFeatures() {
        binding.root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            applyIncomingCallFold(lastFoldFeature)
        }
        foldScope.launch {
            WindowInfoTracker.getOrCreate(this@FaceTimeActivity)
                .windowLayoutInfo(this@FaceTimeActivity)
                .collect { layoutInfo ->
                    lastFoldFeature = layoutInfo.displayFeatures
                        .filterIsInstance<FoldingFeature>()
                        .firstOrNull()
                    applyIncomingCallFold(lastFoldFeature)
                }
        }
    }

    private fun applyIncomingCallFold(feature: FoldingFeature?) {
        if (!::binding.isInitialized) return
        if (defaultCallDescriptionTopMargin == null) {
            defaultCallDescriptionTopMargin =
                (binding.callDescription.layoutParams as FrameLayout.LayoutParams).topMargin
        }
        val windowHeight = binding.root.height.takeIf { it > 0 }
            ?: resources.displayMetrics.heightPixels
        val split = if (
            feature != null &&
            isTabletopFold(
                horizontalHinge = feature.orientation == FoldingFeature.Orientation.HORIZONTAL,
                halfOpened = feature.state == FoldingFeature.State.HALF_OPENED,
            )
        ) {
            faceTimeTabletopInsets(
                windowHeightPx = windowHeight,
                hingeTopPx = feature.bounds.top,
                hingeBottomPx = feature.bounds.bottom,
            )
        } else {
            null
        }
        applyTabletopSplit(split)
        applyInCallLetterbox(split)
    }

    private fun applyTabletopSplit(split: FaceTimeTabletopInsets?) {
        val description = binding.callDescription.layoutParams as FrameLayout.LayoutParams
        val buttons = binding.acceptButtons.layoutParams as FrameLayout.LayoutParams
        if (split == null) {
            description.height = FrameLayout.LayoutParams.WRAP_CONTENT
            description.topMargin = defaultCallDescriptionTopMargin ?: description.topMargin
            description.gravity = Gravity.NO_GRAVITY
            buttons.topMargin = 0
            buttons.height = FrameLayout.LayoutParams.MATCH_PARENT
            buttons.gravity = Gravity.BOTTOM
        } else {
            description.height = split.contentHeightPx
            description.topMargin = 0
            description.gravity = Gravity.CENTER_HORIZONTAL or Gravity.CENTER_VERTICAL
            buttons.topMargin = split.controlsTopMarginPx
            buttons.height = FrameLayout.LayoutParams.MATCH_PARENT
            buttons.gravity = Gravity.BOTTOM
        }
        binding.callDescription.layoutParams = description
        binding.acceptButtons.layoutParams = buttons
    }

    private fun applyInCallLetterbox(split: FaceTimeTabletopInsets?) {
        val params = binding.mainFrame.layoutParams as FrameLayout.LayoutParams
        val inCall = binding.splashLayout.visibility != View.VISIBLE
        if (split != null && inCall) {
            params.height = split.contentHeightPx
            params.topMargin = 0
            params.gravity = Gravity.TOP
        } else {
            params.height = FrameLayout.LayoutParams.MATCH_PARENT
            params.topMargin = 0
            params.gravity = Gravity.NO_GRAVITY
        }
        binding.mainFrame.layoutParams = params
    }

    private fun showInCallSurface() {
        binding.mainFrame.visibility = View.VISIBLE
        binding.splashLayout.visibility = View.GONE
        applyIncomingCallFold(lastFoldFeature)
    }

    var serviceStarted: Boolean = false

    fun startService() {
        if (serviceStarted) return
        val hasCamera = checkSelfPermission(Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        val hasMic = checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (faceTimeForegroundServiceType(hasCamera, hasMic) == 0) {
            Log.w("FaceTime", "skipping in-call FGS without camera or microphone permission")
            return
        }

        val intent = Intent(this, FaceTimeInCallService::class.java)
        startForegroundService(intent)
        serviceStarted = true
    }

    fun handlePermissionRequest(request: PermissionRequest) {
        if (pendingPermissionRequest != null || request.resources.any { it !in permissionMap }) {
            request.deny()
            return
        }
        val permissions = request.resources.flatMap { permissionMap.getValue(it) }.distinct()
        if (permissions.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }) {
            request.grant(request.resources)
            startService()
            return
        }
        pendingPermissionRequest = request
        requestPermissions(permissions.toTypedArray(), 1)
    }

    override fun onDestroy() {
        callTimeoutHandler.removeCallbacks(outgoingCallTimeout)
        callUuid?.let(FaceTimeDispatch::clearActiveCall)
        pendingPermissionRequest?.deny()
        pendingPermissionRequest = null
        if (::cached.isInitialized) cached.cancelDeferredPermissions()
        if (::webView.isInitialized) webView.destroy()
        if (activeFaceTimeActivity === this) activeFaceTimeActivity = null

        val intent = Intent(this, FaceTimeInCallService::class.java)
        stopService(intent)
        serviceStarted = false

        // restore default media volume
        initialMediaVolume?.let {
            try {
                val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
                audioManager.setStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    it,
                    0
                )
            } catch (e: SecurityException) {
                Log.w("FaceTime", "Unable to set stream volume!")
            }
        }

        contentObserver?.let {
            applicationContext.contentResolver.unregisterContentObserver(it)
        }
        foldScope.cancel()

        super.onDestroy()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode != 1) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
            return
        }
        val request = pendingPermissionRequest ?: return
        pendingPermissionRequest = null
        val granted = request.resources.all { resource ->
            permissionMap.getValue(resource).all { permission ->
                val permissionIdx = permissions.indexOf(permission)
                permissionIdx >= 0 &&
                    permissionIdx < grantResults.size &&
                    grantResults[permissionIdx] == PackageManager.PERMISSION_GRANTED
            }
        }
        if (granted) {
            request.grant(request.resources)
            startService()
        } else {
            request.deny()
        }
    }

    private fun connecting() {
        binding.acceptButtons.visibility = View.GONE
        binding.loadingBanner.text = getString(R.string.facetime_connecting)
        Handler(Looper.getMainLooper()).postDelayed({
            showInCallSurface()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                window.setBackgroundBlurRadius(0)
            }
        }, 15000)
    }

    private fun handleConfig(extras: Bundle) {
        val link = extras.getString("link")!!
        val name = extras.getString("name")
        // sanitize desc
        val desc = extras.getString("desc")?.replace("[^a-zA-Z0-9, +.@:&]+".toRegex(), "") ?: "FaceTime Call"
        if (cachedWebview != null) {
            // take control of a pre-rendered webview
            cached = cachedWebview!!
            cachedWebview = null
        } else {
            if (secureWebOrigin(link) == null) {
                Log.w("FaceTime", "refusing non-HTTPS FaceTime link")
                finishAndRemoveTask()
                return
            }
            cached = CachedWebview(this, name, desc, link)
        }

        cached.endTask = {
            dispatchCallAction(FaceTimeActionReceiver.ACTION_END)
        }
        mirrorReady = cached.mirrorReady
        cached.mirrorReadyCall = {
            mirrorReady = true
            if (answered) {
                showInCallSurface()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    window.setBackgroundBlurRadius(0)
                }
                webView.loadUrl("javascript:document.getElementById(\"callcontrols-join-button-session-banner\").click()")
            }
        }

        webView = cached.webView

        val isAnsweringCall = extras.containsKey("answer")
        notificationId = extras.getString("notificationId")?.toInt() ?: 0
        callUuid = extras.getString("callUuid")
        callUuid?.let(FaceTimeDispatch::activateCall)

        if (CreateIncomingFaceTimeNotification.avatarCache.containsKey(callUuid)) {
            val bitmap = CreateIncomingFaceTimeNotification.avatarCache.remove(callUuid)!!
            binding.avatarView.setImageBitmap(bitmap)
        }

        Log.i("FaceTime", "started activity for call $callUuid")

        val poster = extras.getString("poster")
        if (poster != null) {
            binding.posterView.setImageBitmap(BitmapFactory.decodeFile(poster))
            binding.callDescription.visibility = View.GONE
            // no background blur because we are occluded
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                window.setBackgroundBlurRadius(0)
            }
        } else {
            binding.posterView.visibility = View.GONE
        }

        if (isAnsweringCall) {
            isCall = true
            binding.callTitle.text = desc
            binding.splashLayout.visibility = View.VISIBLE
            if (extras.getBoolean("answer")) {
                answerCall()
            }
        } else {
            binding.splashLayout.visibility = View.GONE
            binding.mainFrame.visibility = View.VISIBLE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                window.setBackgroundBlurRadius(0)
            }
            handlePermissionRequests()
            callTimeoutHandler.postDelayed(outgoingCallTimeout, 30_000)
        }
    }
}
