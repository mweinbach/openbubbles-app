package com.bluebubbles.messaging.services.system

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.ContactsContract
import android.util.Base64
import com.bluebubbles.messaging.models.MethodCallHandlerImpl
import com.radzivon.bartoshyk.avif.coder.HeifCoder
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream


/// Open the new contact form picker
class HeifDecoder: MethodCallHandlerImpl() {
    companion object {
        const val tag = "decode-heif"
    }

    override fun handleMethodCall(
        call: MethodCall,
        result: MethodChannel.Result,
        context: Context
    ) {
        val file: String = call.argument("file")!!
        val output: String = call.argument("output")!!

        val photoByteArray = File(file).readBytes()

        val bitmap = HeifCoder().decode(photoByteArray)

        val bytes = FileOutputStream(output)
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes)

        bytes.close()

        result.success(null)
    }
}