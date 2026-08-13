package com.bluebubbles.messaging.services.extension

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PackageManager.NameNotFoundException
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Base64
import com.bluebubbles.messaging.models.MethodCallHandlerImpl
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.io.ByteArrayOutputStream
import com.google.gson.GsonBuilder
import com.google.gson.ToNumberPolicy

class StatusQuery: MethodCallHandlerImpl() {

    fun drawableToBitmap(drawable: Drawable): Bitmap {
        var bitmap: Bitmap? = null
        if (drawable is BitmapDrawable) {
            if (drawable.bitmap != null) {
                return drawable.bitmap
            }
        }
        bitmap = if (drawable.intrinsicWidth <= 0 || drawable.intrinsicHeight <= 0) {
            Bitmap.createBitmap(
                1,
                1,
                Bitmap.Config.ARGB_8888
            ) // Single color bitmap will be created of 1x1 pixel
        } else {
            Bitmap.createBitmap(
                drawable.intrinsicWidth,
                drawable.intrinsicHeight,
                Bitmap.Config.ARGB_8888
            )
        }
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    companion object {
        const val tag = "extension-status"
    }

    @SuppressLint("DiscouragedApi")
    override fun handleMethodCall(
        call: MethodCall,
        result: MethodChannel.Result,
        context: Context
    ) {
        val packageManager = context.packageManager

        val myItems: ArrayList<HashMap<String, Any>> = arrayListOf()

        for (extension in ExtensionRegistry.map) {
            val myMap: HashMap<String, Any> = hashMapOf(
                "appId" to extension.key,
                "store" to extension.value.store,
                "madridName" to extension.value.madridName,
                "madridBundleId" to extension.value.madridBundleId,
            )

            try {
                if (!extension.value.validateService(context)) {
                    myItems.add(myMap)
                    continue
                }
                val name = packageManager.getApplicationLabel(packageManager.getApplicationInfo(extension.value.appPackage, 0))
                val resources = packageManager.getResourcesForApplication(extension.value.appPackage)

                val service = packageManager.getServiceInfo(extension.value.getServiceName(), PackageManager.GET_META_DATA);

                val id = service.metaData.getInt("madrid_icon")
                val color = service.metaData.getInt("color", 0xFF00BFA6.toInt())
                val app = resources.getDrawable(id, null)

                val bitmap = drawableToBitmap(app)
                val baos = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos)
                val b = baos.toByteArray()
                val imageEncoded: String = Base64.encodeToString(b, Base64.NO_WRAP)

                val appInfo = hashMapOf(
                    "name" to name,
                    "icon" to imageEncoded,
                    "color" to color
                )
                myMap["available"] = appInfo
            } catch (_: NameNotFoundException) { }

            myItems.add(myMap)
        }

        val gson = GsonBuilder()
            .setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
            .create()

        result.success(gson.toJson(myItems).toString())

    }
}