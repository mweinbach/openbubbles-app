package com.bluebubbles.messaging.services.system

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.bluebubbles.messaging.models.MethodCallHandlerImpl
import com.radzivon.bartoshyk.avif.coder.HeifCoder
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.io.File
import java.io.FileOutputStream

class HeifEncoder : MethodCallHandlerImpl() {

    companion object {
        const val tag = "encode-heif"
    }

    override fun handleMethodCall(
        call: MethodCall,
        result: MethodChannel.Result,
        context: Context
    ) {
        val file: String = call.argument("file")!!
        val output: String = call.argument("output")!!


        val bitmap = BitmapFactory.decodeFile(file)


        val bytes = FileOutputStream(output)
        bytes.write(HeifCoder().encodeHeic(bitmap))

        bytes.close()

        result.success(null)
    }
}