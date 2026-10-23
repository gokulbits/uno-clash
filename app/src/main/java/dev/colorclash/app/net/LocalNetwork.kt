package dev.colorclash.app.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.Inet4Address
import java.net.NetworkInterface

object LocalNetwork {

    /** Addresses other phones can use to reach this one, best first. Includes the hotspot address when it's on. */
    fun hostAddresses(): List<String> {
        val all = runCatching { NetworkInterface.getNetworkInterfaces()?.toList().orEmpty() }.getOrDefault(emptyList())
        val addresses = all
            .filter { runCatching { it.isUp && !it.isLoopback }.getOrDefault(false) }
            .flatMap { iface ->
                iface.inetAddresses.toList()
                    .filterIsInstance<Inet4Address>()
                    .mapNotNull { addr -> addr.hostAddress?.let { LocalAddress(iface.name, it) } }
            }
        return AddressRanking.rank(addresses)
    }

    /** The Wi-Fi router's address. On a friend's hotspot that's the host phone itself. */
    @Suppress("DEPRECATION") // allNetworks is the simplest synchronous way to find the Wi-Fi network
    fun wifiGateway(context: Context): String? {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
        val wifi = cm.allNetworks.firstOrNull { network ->
            cm.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        } ?: return null
        val routes = cm.getLinkProperties(wifi)?.routes.orEmpty()
        return routes.firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }?.gateway?.hostAddress
    }
}
