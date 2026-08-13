package com.bluebubbles.messaging.services.extension

import android.content.Context
import android.os.Handler
import com.bluebubbles.messaging.IMessageViewHandle
import com.bluebubbles.messaging.ITaskCompleteCallback
import com.bluebubbles.messaging.MadridMessage
import com.bluebubbles.messaging.services.backend_ui_interop.MethodCallHandler
import io.flutter.plugin.common.MethodChannel

class MessageViewHandle(val context: Context, val session: String, val appId: Int, val guid: String?, val destroy: () -> Unit) : IMessageViewHandle.Stub() {

    var locked = false
    var alive = false
    var suppressing = false

    override fun setSuppressNotifications(suppress: Boolean) {
        if (suppress && !locked) {
            throw Exception("Trying to suppress notifications for unlocked handle!  ")
        }
        suppressing = suppress
        Handler(context.mainLooper).post {
            val myMsg = hashMapOf(
                "appId" to appId,
                "session" to session,
                "suppress" to suppress,
            )
            MethodCallHandler.invokeMethod(
                "extension-set-suppress",
                myMsg)
        }
    }

    override fun updateMessage(
        message: MadridMessage?,
        callback: ITaskCompleteCallback?
    ) {
        Handler(context.mainLooper).post {
            val myMsg = MadridMessageUtil.toMap(message!!)
            myMsg["appId"] = appId
            myMsg["session"] = session
            myMsg["messageGuid"] = guid!!
            MethodCallHandler.invokeMethodCb(
                "extension-update-message",
                myMsg,
                object : MethodChannel.Result {
                    override fun success(result: Any?) {
                        callback?.complete()
                    }

                    override fun notImplemented() {
                        throw Exception("not implemeneted!")
                    }

                    override fun error(
                        errorCode: String,
                        errorMessage: String?,
                        errorDetails: Any?
                    ) {
                        // handled in dart
                    }
                })
        }
    }

    override fun lock() {
        locked = true
    }

    override fun unlock() {
        locked = false
        if (suppressing) {
            setSuppressNotifications(false)
        }
        if (!alive) {
            destroy()
            MessageViewRegistry.registered.remove(session)
        }
    }

    fun markDead() {
        alive = false
        if (!locked) {
            destroy()
            MessageViewRegistry.registered.remove(session)
        }
    }

}