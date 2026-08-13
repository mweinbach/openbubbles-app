package com.bluebubbles.messaging.services.extension

import android.content.Context
import io.flutter.plugin.common.StandardMessageCodec
import io.flutter.plugin.platform.PlatformView
import io.flutter.plugin.platform.PlatformViewFactory

class KeyboardViewFactory : PlatformViewFactory(StandardMessageCodec.INSTANCE) {
    override fun create(context: Context, viewId: Int, args: Any?): PlatformView {
        val creationParams = args as Map<String?, Any?>
        val appId = creationParams["app-id"] as Int;
        val userCount = creationParams["user-count"] as Int
        return KeyboardView(context, viewId, appId, userCount)
    }
}