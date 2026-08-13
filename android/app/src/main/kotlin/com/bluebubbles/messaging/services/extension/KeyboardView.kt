package com.bluebubbles.messaging.services.extension

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Handler
import android.os.RemoteException
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.TextView
import com.bluebubbles.messaging.IKeyboardHandle
import com.bluebubbles.messaging.IMadridExtension
import com.bluebubbles.messaging.IViewUpdateCallback
import com.bluebubbles.messaging.MadridMessage
import com.bluebubbles.messaging.MainActivity
import com.bluebubbles.messaging.services.backend_ui_interop.MethodCallHandler
import io.flutter.plugin.platform.PlatformView

internal class KeyboardView(val context: Context, id: Int, val appId: Int, val userCount: Int) :
    PlatformView {
    private val content: FrameLayout = FrameLayout(context)

    private var mView: View? = null;

    private val connection: MadridExtensionConnection = MadridExtensionConnection.bind(appId, context) {
        it.extension?.let { ext ->
            try {
                val view = ext.keyboardOpened(object : IViewUpdateCallback.Stub() {
                    override fun updateView(views: RemoteViews?) {
                        Handler(context.mainLooper).post {
                            newViews(views!!)
                        }
                    }
                }, object : IKeyboardHandle.Stub() {
                    override fun addMessage(message: MadridMessage?) {
                        if (message == null)
                            throw RemoteException("Message cannot be null!")
                        Handler(context.mainLooper).post {
                            val myMsg = MadridMessageUtil.toMap(message)
                            myMsg["appId"] = appId;
                            MethodCallHandler.invokeMethod("extension-add-message", myMsg)
                        }
                    }
                }, userCount)
                newViews(view)
            } catch (e: RemoteException) {
                Log.e("Extensions", "Failed to get keyboard view $e")
            }
        }
    }

    fun newViews(view: RemoteViews) {
        mView?.let {
            view.reapply(context, mView)
            return
        }
        mView = view.apply(context, content)
        content.addView(mView)
    }

    override fun getView(): View {
        return content
    }

    override fun dispose() {
        connection.extension?.keyboardClosed()
        connection.unbind(context)
    }
}