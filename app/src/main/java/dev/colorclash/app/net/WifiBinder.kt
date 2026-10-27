package dev.colorclash.app.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Build
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

/**
 * Pins this process's sockets to Wi-Fi. Without it, Android happily sends traffic for a Wi-Fi
 * with no internet (like a phone hotspot with data off) over mobile data, and the host is unreachable.
 * Also keeps the guest's Wi-Fi out of power save while bound, which otherwise stalls the link for seconds.
 */
class WifiBinder(context: Context) {
    private val cm = context.getSystemService(ConnectivityManager::class.java)
    private val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
    private var callback: ConnectivityManager.NetworkCallback? = null
    private var lock: WifiManager.WifiLock? = null
    /** Two callers racing the guard below each registered a callback, and only one was ever unregistered. */
    private val binding = Mutex()

    /** Waits briefly for a Wi-Fi network so the first connection attempt already goes the right way. */
    suspend fun bind(): Boolean = binding.withLock { bindLocked() }

    private suspend fun bindLocked(): Boolean {
        if (callback != null) return cm.boundNetworkForProcess != null
        acquireLock()
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

    suspend fun unbind() = binding.withLock {
        callback?.let { runCatching { cm.unregisterNetworkCallback(it) } }
        callback = null
        cm.bindProcessToNetwork(null)
        lock?.takeIf { it.isHeld }?.release()
        lock = null
    }

    /** LOW_LATENCY only counts with the screen on and the app in front, which is when a guest plays. */
    @Suppress("DEPRECATION")
    private fun acquireLock() {
        if (lock != null) return
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) WifiManager.WIFI_MODE_FULL_LOW_LATENCY else WifiManager.WIFI_MODE_FULL_HIGH_PERF
        lock = runCatching {
            wifi.createWifiLock(mode, "ColorClash:guest").apply {
                setReferenceCounted(false)
                acquire()
            }
        }.getOrNull()
    }
}
