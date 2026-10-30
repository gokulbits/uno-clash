package dev.colorclash.app.session

import android.app.Application
import android.util.Log
import dev.colorclash.app.data.Prefs
import dev.colorclash.app.data.SavedSession
import dev.colorclash.app.net.FoundRoom
import dev.colorclash.app.net.LocalNetwork
import dev.colorclash.app.net.RoomDiscovery
import dev.colorclash.app.net.WifiBinder
import dev.colorclash.net.client.ClientStatus
import dev.colorclash.net.client.GameClient
import dev.colorclash.net.host.GameHost
import dev.colorclash.net.host.HostConfig
import dev.colorclash.net.host.HostServer
import dev.colorclash.protocol.DEFAULT_PORT
import dev.colorclash.protocol.JoinError
import dev.colorclash.protocol.JoinLink
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    /**
     * One room at a time. Opening and joining both suspend partway through (binding Wi-Fi, probing
     * addresses, waiting for an answer), and interleaving two of them left a server listening with
     * no session, or the foreground service running with no room.
     */
    private val transitions = Mutex()

    private val _session = MutableStateFlow<Session?>(null)
    val session: StateFlow<Session?> = _session.asStateFlow()

    /** A guest seat worth offering to rejoin, if the app was closed mid-game recently. Off the main thread: it goes through the keystore. */
    suspend fun rejoinable(): SavedSession? = withContext(Dispatchers.IO) {
        prefs.lastSession?.takeIf { System.currentTimeMillis() - it.savedAtMillis < REJOIN_WINDOW.inWholeMilliseconds }
    }

    /**
     * Runs a host or join to the end on this scope, whatever happens to the caller. Started from the
     * UI, these ran on the main thread (building Ktor's server and client takes long enough to drop
     * frames), and the activity going away partway would leave a client or server with no session.
     */
    private suspend fun <T> transition(block: suspend () -> T): T = scope.async { transitions.withLock { block() } }.await()

    /** Opens a room on this phone. Returns an error message, or null on success. */
    suspend fun host(bots: Int = 0): String? = transition { hostLocked(bots) }

    private suspend fun hostLocked(bots: Int): String? {
        leaveLocked()
        // Rounds roll on by themselves after a short look at the scores, so nobody waits on the host.
        val host = GameHost(scope, HostConfig(autoNextRound = NEXT_ROUND_AFTER))
        host.updateSettings(prefs.lobbySettings)
        val (server, port) = startServer(host) ?: return "Couldn't open a game server on this phone."
        val profile = prefs.profile.value
        val token = host.reserveHostSeat(profile.name, profile.avatar, profile.avatarColor, profile.avatarFrame, profile.photo)
        repeat(bots) { host.addBot() }
        // Refused if the app went to the background a moment ago. The room still works, it just
        // won't survive the screen going off, which beats crashing over it.
        runCatching { HostService.start(app, host.roomCode) }.onFailure { Log.w(TAG, "Couldn't start the host service", it) }
        val advert = discovery.advertise(port, profile.name)

        // The host plays through the same client code as everyone else, over loopback.
        val client = GameClient(scope)
        client.connect("127.0.0.1", port, host.roomCode, profile.name, profile.avatar, token, profile.avatarColor, profile.avatarFrame, profile.photo)
        _session.value = Session(client, host.roomCode, Hosting(host, server, port, advert))
        return null
    }

    suspend fun join(link: JoinLink, token: String? = null): String? =
        transition { joinLocked(link.hosts, link.port, link.roomCode, token) }

    /**
     * Finds the room with this code. Rooms don't advertise their code, so every room nearby (and the
     * Wi-Fi gateway, in case we're on the host's hotspot) is asked in turn until one accepts it.
     */
    suspend fun joinByCode(code: String): String? = transition {
        val roomCode = code.trim().uppercase()
        leaveLocked()
        wifi.bind()
        var nearby = emptyList<FoundRoom>()
        withTimeoutOrNull(DISCOVERY_WAIT) { discovery.rooms().collect { nearby = it } }
        val candidates = nearby.map { it.address to it.port } + listOfNotNull(LocalNetwork.wifiGateway(app)?.let { it to DEFAULT_PORT })
        when (val attempt = joinChecked(candidates.distinct(), roomCode)) {
            Attempt.Joined -> null
            is Attempt.Refused -> attempt.message
            Attempt.NoMatch -> "No room with code $roomCode here. Check the code on the host's phone, or scan its QR."
            Attempt.Unreachable -> "Couldn't find any room on this network. Make sure you're on the host's Wi-Fi or hotspot."
        }
    }

    /** A room from the nearby list, with the code the player typed for it. */
    suspend fun joinRoom(room: FoundRoom, code: String): String? = transition {
        joinAtLocked(room.address, room.port, code, "That's not ${room.hostName}'s room code. Check it on their phone.")
    }

    /** A host's address typed in by hand. */
    suspend fun joinAddress(address: String, port: Int, code: String): String? = transition {
        joinAtLocked(address, port, code, "No room with that code at $address. Check the code on the host's phone.")
    }

    private suspend fun joinAtLocked(address: String, port: Int, code: String, noMatch: String): String? {
        leaveLocked()
        wifi.bind()
        return when (val attempt = joinChecked(listOf(address to port), code.trim().uppercase())) {
            Attempt.Joined -> null
            is Attempt.Refused -> attempt.message
            Attempt.NoMatch -> noMatch
            Attempt.Unreachable -> "Couldn't reach the host at $address. Make sure you're on the same Wi-Fi or hotspot."
        }
    }

    private sealed interface Attempt {
        data object Joined : Attempt
        data class Refused(val message: String) : Attempt
        data object NoMatch : Attempt
        data object Unreachable : Attempt
    }

    /** Tries each address until a host takes the code, and only then opens the room, so a wrong code stays on the join screen. */
    private suspend fun joinChecked(candidates: List<Pair<String, Int>>, roomCode: String): Attempt {
        val profile = prefs.profile.value
        var reachedAny = false
        for ((address, port) in candidates) {
            if (!reachable(address, port)) continue
            reachedAny = true
            val client = GameClient(scope)
            client.connect(address, port, roomCode, profile.name, profile.avatar, null, profile.avatarColor, profile.avatarFrame, profile.photo)
            val status = withTimeoutOrNull(ANSWER_WAIT) { client.status.first { it == ClientStatus.Connected || it is ClientStatus.Failed } }
            when {
                status == ClientStatus.Connected -> {
                    open(client, address, port, roomCode)
                    return Attempt.Joined
                }
                status is ClientStatus.Failed && status.error != JoinError.WRONG_ROOM -> {
                    client.close()
                    wifi.unbind()
                    return Attempt.Refused(status.message)
                }
                else -> client.close()
            }
        }
        wifi.unbind()
        return if (reachedAny) Attempt.NoMatch else Attempt.Unreachable
    }

    suspend fun rejoin(saved: SavedSession): String? = transition {
        joinLocked(listOf(saved.host), saved.port, saved.roomCode, saved.token)
    }

    private suspend fun joinLocked(hosts: List<String>, port: Int, roomCode: String, token: String? = null): String? {
        leaveLocked()
        wifi.bind()
        val address = hosts.firstOrNull { reachable(it, port) }
        if (address == null) {
            wifi.unbind()
            return "Couldn't reach the host at ${hosts.joinToString()}. Make sure you're on the same Wi-Fi or hotspot."
        }
        val profile = prefs.profile.value
        val client = GameClient(scope)
        client.connect(address, port, roomCode, profile.name, profile.avatar, token, profile.avatarColor, profile.avatarFrame, profile.photo)
        open(client, address, port, roomCode)
        return null
    }

    private fun open(client: GameClient, address: String, port: Int, roomCode: String) {
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
    }

    /**
     * Leaves the room (and shuts it down if we're hosting). The room goes from the UI at once; the
     * teardown itself queues behind any host or join still in flight.
     */
    fun leave() {
        val session = _session.getAndUpdate { null } ?: return
        scope.launch { transitions.withLock { tearDown(session) } }
    }

    private suspend fun leaveLocked() {
        val session = _session.getAndUpdate { null } ?: return
        tearDown(session)
    }

    private suspend fun tearDown(session: Session) {
        session.watcher?.cancel()
        prefs.lastSession = null
        session.client.leave()
        session.hosting?.let { hosting ->
            hosting.advert.close()
            hosting.host.close()
            HostService.stop(app)
        }
        // Off the transition lock: the goodbye messages need a moment to go out.
        scope.launch {
            delay(500)
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
            } catch (e: CancellationException) {
                server.stop()
                throw e
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
        val DISCOVERY_WAIT = 2.5.seconds
        val ANSWER_WAIT = 8.seconds
        val REJOIN_WINDOW = 2.hours
    }
}

private val NEXT_ROUND_AFTER = 15.seconds
