package dev.colorclash.app.net

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Inet4Address
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.seconds

data class FoundRoom(val roomCode: String, val hostName: String, val address: String, val port: Int)

/** mDNS advertising and discovery. Handy when it works, but routers often block it, so it's never the only way in. */
class RoomDiscovery(context: Context) {
    private val nsd = context.getSystemService(NsdManager::class.java)
    private val wifi = context.applicationContext.getSystemService(WifiManager::class.java)

    fun advertise(roomCode: String, port: Int, hostName: String): AutoCloseable {
        val info = NsdServiceInfo().apply {
            serviceName = "ColorClash $roomCode"
            serviceType = SERVICE_TYPE
            this.port = port
            setAttribute(ATTR_CODE, roomCode)
            setAttribute(ATTR_NAME, hostName.take(20))
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) = Unit
            override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
            override fun onRegistrationFailed(info: NsdServiceInfo, error: Int) {
                Log.w(TAG, "mDNS registration failed: $error")
            }
            override fun onUnregistrationFailed(info: NsdServiceInfo, error: Int) = Unit
        }
        runCatching { nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener) }
            .onFailure { Log.w(TAG, "mDNS registration threw", it) }
        return AutoCloseable { runCatching { nsd.unregisterService(listener) } }
    }

    /** Live list of rooms on this network while collected. */
    fun rooms(): Flow<List<FoundRoom>> = callbackFlow {
        val multicast = wifi.createMulticastLock("colorclash-discovery").apply {
            setReferenceCounted(false)
            acquire()
        }
        val found = ConcurrentHashMap<String, FoundRoom>()
        val resolving = Mutex() // older NsdManager can only resolve one service at a time
        fun publish() = trySend(found.values.sortedBy { it.hostName })

        val listener = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(info: NsdServiceInfo) {
                launch {
                    val room = resolving.withLock { resolve(info) } ?: return@launch
                    found[info.serviceName] = room
                    publish()
                }
            }

            override fun onServiceLost(info: NsdServiceInfo) {
                found.remove(info.serviceName)
                publish()
            }

            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, error: Int) {
                Log.w(TAG, "mDNS discovery failed: $error")
            }
            override fun onStopDiscoveryFailed(serviceType: String, error: Int) = Unit
        }

        publish()
        runCatching { nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener) }
            .onFailure { Log.w(TAG, "mDNS discovery threw", it) }
        awaitClose {
            runCatching { nsd.stopServiceDiscovery(listener) }
            multicast.release()
        }
    }

    @Suppress("DEPRECATION") // resolveService still works everywhere and is the only option below API 34
    private suspend fun resolve(info: NsdServiceInfo): FoundRoom? = withTimeoutOrNull(3.seconds) {
        suspendCancellableCoroutine { cont ->
            nsd.resolveService(info, object : NsdManager.ResolveListener {
                override fun onServiceResolved(resolved: NsdServiceInfo) {
                    if (cont.isActive) cont.resume(resolved.toRoom())
                }

                override fun onResolveFailed(info: NsdServiceInfo, error: Int) {
                    if (cont.isActive) cont.resume(null)
                }
            })
        }
    }

    @Suppress("DEPRECATION")
    private fun NsdServiceInfo.toRoom(): FoundRoom? {
        val code = attributes[ATTR_CODE]?.let { String(it) } ?: return null
        val name = attributes[ATTR_NAME]?.let { String(it) } ?: "Host"
        val address = if (Build.VERSION.SDK_INT >= 34) {
            hostAddresses.filterIsInstance<Inet4Address>().firstOrNull()
        } else {
            host as? Inet4Address
        } ?: return null
        return FoundRoom(code, name, address.hostAddress ?: return null, port)
    }

    private companion object {
        const val TAG = "RoomDiscovery"
        const val SERVICE_TYPE = "_colorclash._tcp"
        const val ATTR_CODE = "code"
        const val ATTR_NAME = "name"
    }
}
