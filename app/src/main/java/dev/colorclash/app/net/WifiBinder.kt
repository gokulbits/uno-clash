package dev.colorclash.app.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

/**
 * Pins this process's sockets to Wi-Fi. Without it, Android happily sends traffic for a Wi-Fi
 * with no internet (like a phone hotspot with data off) over mobile data, and the host is unreachable.
 */
class WifiBinder(context: Context) {
    private val cm = context.getSystemService(ConnectivityManager::class.java)
    private var callback: ConnectivityManager.NetworkCallback? = null

    /** Waits briefly for a Wi-Fi network so the first connection attempt already goes the right way. */
    suspend fun bind(): Boolean {
        if (callback != null) return cm.boundNetworkForProcess != null
        val ready = CompletableDeferred<Unit>()
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                cm.bindProcessToNetwork(network)
                ready.complete(Unit)
            }

            override fun onLost(network: Network) {
                if (cm.boundNetworkForProcess == network) cm.bindProcessToNetwork(null)
            }
        }
        callback = cb
        // Requesting (rather than just listening) tells the system we need this Wi-Fi, so it doesn't
        // drop a hotspot that has no internet in favour of mobile data halfway through a game.
        try {
            cm.requestNetwork(request, cb)
        } catch (e: SecurityException) {
            cm.registerNetworkCallback(request, cb)
        }
        return withTimeoutOrNull(1_500.milliseconds) { ready.await() } != null
    }

    fun unbind() {
        callback?.let { runCatching { cm.unregisterNetworkCallback(it) } }
        callback = null
        cm.bindProcessToNetwork(null)
    }
}
