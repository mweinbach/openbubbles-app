package com.bluebubbles.messaging.services.system

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Point
import android.os.Build
import android.provider.ContactsContract
import android.util.Base64
import android.view.WindowManager
import com.bluebubbles.messaging.models.MethodCallHandlerImpl
import com.radzivon.bartoshyk.avif.coder.HeifCoder
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream


/// Gets full screen resolution, including from an isolate, with no system bars
class GetFullResolution: MethodCallHandlerImpl() {
    companion object {
        const val tag = "get-full-resolution"
    }

    override fun handleMethodCall(
        call: MethodCall,
        result: MethodChannel.Result,
        context: Context
    ) {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager;
        val dims = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            val metrics = wm.maximumWindowMetrics
            hashMapOf(
                "width" to metrics.bounds.width(),
                "height" to metrics.bounds.height(),
                "ratio" to context.resources.displayMetrics.densityDpi.toDouble() / 160
            )
        } else {
            val size = Point()
            wm.defaultDisplay.getRealSize(size)
            hashMapOf(
                "width" to size.x,
                "height" to size.y,
                "ratio" to context.resources.displayMetrics.densityDpi.toDouble() / 160
            )
        }

        result.success(dims)
    }
}