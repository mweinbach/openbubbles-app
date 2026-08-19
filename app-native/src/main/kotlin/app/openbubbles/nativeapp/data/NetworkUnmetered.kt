package app.openbubbles.nativeapp.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** True when the active network is marked unmetered (typical Wi-Fi). */
fun isUnmeteredNetwork(context: Context): Boolean {
    val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
    val network = manager.activeNetwork ?: return false
    val capabilities = manager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
}
