package com.bluebubbles.messaging.services.extension

import com.bluebubbles.messaging.MadridMessage
import io.flutter.plugin.common.MethodCall

object MadridMessageUtil {
    fun toMap(message: MadridMessage): HashMap<String, Any> {
        return hashMapOf(
            "messageGuid" to message.messageGuid,
            "ldText" to message.ldText,
            "url" to message.url,
            "session" to message.session,

            "imageBase64" to message.imageBase64,
            "imageSubtitle" to message.imageSubtitle,
            "imageTitle" to message.imageTitle,
            "caption" to message.caption,
            "secondaryCaption" to message.secondaryCaption,
            "tertiaryCaption" to message.tertiaryCaption,
            "subcaption" to message.subcaption,

            "isLive" to message.isLive,
        )
    }

    fun fromCall(call: MethodCall): MadridMessage {
        return MadridMessage().apply {
            messageGuid = call.argument("messageGuid")
            ldText = call.argument("ldText")
            url = call.argument("url")
            session = call.argument("session")

            imageBase64 = call.argument("imageBase64")
            imageSubtitle = call.argument("imageSubtitle")
            imageTitle = call.argument("imageTitle")
            caption = call.argument("caption")
            secondaryCaption = call.argument("secondaryCaption")
            tertiaryCaption = call.argument("tertiaryCaption")
            subcaption = call.argument("subcaption")

            isLive = call.argument("isLive") ?: false
        }
    }
}