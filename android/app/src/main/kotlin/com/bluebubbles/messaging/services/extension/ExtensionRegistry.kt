package com.bluebubbles.messaging.services.extension

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import java.security.MessageDigest

data class AvailableExtension(
    val store: String,
    val appPackage: String,
    val madridName: String,
    val madridBundleId: String,
    val serviceName: String,
    val sha256: String?,
) {
    fun getServiceName(): ComponentName {
        return ComponentName.createRelative(appPackage, serviceName)
    }

    fun validateService(context: Context): Boolean {
        if (sha256 == null) return true
        val packageInfo = context.packageManager.getPackageInfo(appPackage, PackageManager.GET_SIGNING_CERTIFICATES)
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.signingInfo!!.apkContentsSigners!!
        } else {
            @Suppress("DEPRECATION")
            packageInfo.signatures!!
        }

        val md = MessageDigest.getInstance("SHA-256")
        val sig = signatures.any { signature ->
            val digest = md.digest(signature.toByteArray())
            digest.joinToString(":") { "%02X".format(it) } == sha256
        }

        if (!sig) {
            Log.e("ExtensionRegistry", "Extension $appPackage did not match signature $sha256")
        }

        return sig
    }
}

// also add to queries in manifest
object ExtensionRegistry {
    val map = hashMapOf<Int, AvailableExtension>(
        1124197642 to AvailableExtension(
            "https://play.google.com/store/apps/details?id=com.openbubbles.openpigeon",
            "com.openbubbles.openpigeon",
            "GamePigeon",
            "com.apple.messages.MSMessageExtensionBalloonPlugin:EWFNLB79LQ:com.gamerdelights.gamepigeon.ext",
            ".MadridExtensionService",
            "CB:77:DB:97:3F:D8:CE:CD:55:75:B0:85:92:F4:4C:F5:4C:25:DE:21:B7:95:3F:ED:45:7C:BD:F4:36:D4:01:1D"
        )
    )

    fun registerDevExtension(context: Context, name: String) {
        val parts = ArrayList(name.split("."))
        val cls = "." + parts.removeAt(parts.lastIndex)
        val pak = parts.joinToString(".")
        val component = ComponentName.createRelative(pak, cls)
        val service = context.packageManager.getServiceInfo(component, PackageManager.GET_META_DATA)

        map[service.metaData.getInt("madrid_id")] = AvailableExtension(
            "https://play.google.com/store/games?hl=en_US",
            pak,
            service.metaData.getString("madrid_name")!!,
            service.metaData.getString("madrid_bundle_id")!!,
            cls,
            null,
        )
    }
}