package app.openbubbles.nativeapp.ui.chat.composer

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager

@SuppressLint("MissingPermission")
fun currentLocationMessage(context: Context): String? {
    val manager = context.getSystemService(LocationManager::class.java) ?: return null
    val providers = manager.getProviders(true)
    val location = providers.mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
        .maxByOrNull { it.time }
        ?: return null
    val latitude = "%.6f".format(java.util.Locale.US, location.latitude)
    val longitude = "%.6f".format(java.util.Locale.US, location.longitude)
    return "My current location: https://maps.apple.com/?ll=$latitude,$longitude (geo:$latitude,$longitude)"
}
