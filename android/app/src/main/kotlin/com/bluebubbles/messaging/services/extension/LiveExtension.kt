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
import com.bluebubbles.messaging.IMessageViewHandle
import com.bluebubbles.messaging.ITaskCompleteCallback
import com.bluebubbles.messaging.IViewUpdateCallback
import com.bluebubbles.messaging.MadridMessage
import com.bluebubbles.messaging.MainActivity
import com.bluebubbles.messaging.services.backend_ui_interop.MethodCallHandler
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.platform.PlatformView

internal class LiveExtension(val context: Context, id: Int, val data: Map<String?, Any?>, val userCount: Int) :
    PlatformView {
    private val content: FrameLayout = FrameLayout(context)

    private var mView: View? = null;

    private var handle: MessageViewHandle? = null

    private val connection: MadridExtensionConnection = MadridExtensionConnection.bind(data["appId"] as Int, context) {
        it.extension?.let { ext ->
            val message = MadridMessage().apply {
                messageGuid = data["messageGuid"] as String?
                ldText = data["ldText"] as String?
                url = data["url"] as String
                session = data["session"] as String?

                imageBase64 = data["imageBase64"] as String?
                imageSubtitle = data["imageSubtitle"] as String?
                imageTitle = data["imageTitle"] as String?
                caption = data["caption"] as String?
                secondaryCaption = data["secondaryCaption"] as String?
                tertiaryCaption = data["tertiaryCaption"] as String?
                subcaption = data["subcaption"] as String?

                isLive = (data["isLive"] as Boolean?) ?: false
            }
            MessageViewRegistry.registered.add(message.session)
            handle = MessageViewHandle(
                context,
                data["session"] as String,
                data["appId"] as Int,
                data["messageGuid"] as String?,
            ) {
                it.unbind(context)
            }
            try {
                val view = ext.getLiveView(object : IViewUpdateCallback.Stub() {
                    override fun updateView(views: RemoteViews?) {
                        Handler(context.mainLooper).post {
                            newViews(views!!)
                        }
                    }
                }, message, handle, userCount)
                newViews(view)
            } catch (e: RemoteException) {
                Log.e("Extensions", "Failed to get live view $e")
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
        handle?.markDead()
    }
}