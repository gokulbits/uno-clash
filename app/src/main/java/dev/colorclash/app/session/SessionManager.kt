package dev.colorclash.app.session

import android.app.Application
import android.util.Log
import dev.colorclash.app.data.Prefs
import dev.colorclash.app.data.SavedSession
import dev.colorclash.app.net.LocalNetwork
import dev.colorclash.app.net.RoomDiscovery
import dev.colorclash.app.net.WifiBinder
import dev.colorclash.net.client.ClientStatus
import dev.colorclash.net.client.GameClient
import dev.colorclash.net.host.GameHost
import dev.colorclash.net.host.HostConfig
import dev.colorclash.net.host.HostServer
import dev.colorclash.protocol.DEFAULT_PORT
import dev.colorclash.protocol.JoinLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

/** The host side of a session: the room, its server and the mDNS advert. */
class Hosting(val host: GameHost, val server: HostServer, val port: Int, val advert: AutoCloseable)

class Session(val client: GameClient, val roomCode: String, val hosting: Hosting?) {
    val isHost: Boolean get() = hosting != null
    internal var watcher: Job? = null
}

/**
 * Owns the current room for the whole process, so it survives the activity being recreated
 * and keeps running behind [HostService] when the screen is off.
 */
class SessionManager(private val app: Application, private val prefs: Prefs) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val wifi = WifiBinder(app)
    val discovery = RoomDiscovery(app)

    private val _session = MutableStateFlow<Session?>(null)
    val session: StateFlow<Session?> = _session.asStateFlow()

    /** A guest seat worth offering to rejoin, if the app was closed mid-game recently. */
    fun rejoinable(): SavedSession? = prefs.lastSession?.takeIf {
        System.currentTimeMillis() - it.savedAtMillis < REJOIN_WINDOW.inWholeMilliseconds
    }

    /** Opens a room on this phone. Returns an error message, or null on success. */
    suspend fun host(bots: Int = 0): String? {
        leave()
        // Rounds roll on by themselves after a short look at the scores, so nobody waits on the host.
        val host = GameHost(scope, HostConfig(autoNextRound = NEXT_ROUND_AFTER))
        val (server, port) = startServer(host) ?: return "Couldn't open a game server on this phone."
        val profile = prefs.profile.value
        val token = host.reserveHostSeat(profile.name, profile.avatar, profile.avatarColor, profile.avatarFrame, profile.photo)
        repeat(bots) { host.addBot() }
        HostService.start(app, host.roomCode)
        val advert = discovery.advertise(host.roomCode, port, profile.name)

        // The host plays through the same client code as everyone else, over loopback.
        val client = GameClient(scope)
        client.connect("127.0.0.1", port, host.roomCode, profile.name, profile.avatar, token, profile.avatarColor, profile.avatarFrame, profile.photo)
        _session.value = Session(client, host.roomCode, Hosting(host, server, port, advert))
        return null
    }

    suspend fun join(link: JoinLink, token: String? = null): String? =
        join(link.hosts, link.port, link.roomCode, token)

    /** Finds a room by its code: mDNS first, then the Wi-Fi gateway in case we're on the host's hotspot. */
    suspend fun joinByCode(code: String): String? {
        val roomCode = code.trim().uppercase()
        wifi.bind()
        val found = withTimeoutOrNull(4.seconds) {
            discovery.rooms().map { rooms -> rooms.firstOrNull { it.roomCode == roomCode } }.filterNotNull().first()
        }
        if (found != null) return join(listOf(found.address), found.port, roomCode)
        val gateway = LocalNetwork.wifiGateway(app)
        if (gateway != null && reachable(gateway, DEFAULT_PORT)) return join(listOf(gateway), DEFAULT_PORT, roomCode)
        wifi.unbind()
        return "Couldn't find room $roomCode on this network. Try scanning the QR code or typing the host's IP."
    }

    suspend fun rejoin(saved: SavedSession): String? = join(listOf(saved.host), saved.port, saved.roomCode, saved.token)

    suspend fun join(hosts: List<String>, port: Int, roomCode: String, token: String? = null): String? {
        leave()
        wifi.bind()
        val address = hosts.firstOrNull { reachable(it, port) }
        if (address == null) {
            wifi.unbind()
            return "Couldn't reach the host at ${hosts.joinToString()}. Make sure you're on the same Wi-Fi or hotspot."
        }
        val profile = prefs.profile.value
        val client = GameClient(scope)
        client.connect(address, port, roomCode, profile.name, profile.avatar, token, profile.avatarColor, profile.avatarFrame, profile.photo)
        val session = Session(client, roomCode, hosting = null)
        session.watcher = scope.launch {
            client.status.collect { status ->
                when (status) {
                    ClientStatus.Connected -> client.sessionToken?.let {
                        prefs.lastSession = SavedSession(address, port, roomCode, it, System.currentTimeMillis())
                    }
                    is ClientStatus.Failed, is ClientStatus.Closed -> prefs.lastSession = null
                    else -> Unit
                }
            }
        }
        _session.value = session
        return null
    }

    /** Leaves the room (and shuts it down if we're hosting). */
    fun leave() {
        val session = _session.value ?: return
        _session.value = null
        session.watcher?.cancel()
        prefs.lastSession = null
        session.client.leave()
        session.hosting?.let { hosting ->
            hosting.advert.close()
            hosting.host.close()
            HostService.stop(app)
        }
        scope.launch {
            delay(500) // let the goodbye messages go out first
            session.client.close()
            session.hosting?.server?.stop()
        }
        wifi.unbind()
    }

    private suspend fun startServer(host: GameHost): Pair<HostServer, Int>? {
        for (port in listOf(DEFAULT_PORT, 0)) {
            val server = HostServer(host, port)
            try {
                return server to server.start()
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't listen on port $port", e)
                server.stop()
            }
        }
        return null
    }

    private suspend fun reachable(host: String, port: Int): Boolean = withContext(Dispatchers.IO) {
        runCatching { Socket().use { it.connect(InetSocketAddress(host, port), PROBE_TIMEOUT_MS) } }.isSuccess
    }

    private companion object {
        const val TAG = "SessionManager"
        const val PROBE_TIMEOUT_MS = 1_500
        val REJOIN_WINDOW = 2.hours
    }
}

private val NEXT_ROUND_AFTER = 15.seconds
