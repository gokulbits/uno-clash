package dev.colorclash.net

import dev.colorclash.net.client.ClientStatus
import dev.colorclash.net.host.HostStage
import dev.colorclash.protocol.ClientMessage
import dev.colorclash.protocol.Codec
import dev.colorclash.protocol.EventView
import dev.colorclash.protocol.GameSettings
import dev.colorclash.protocol.JoinError
import dev.colorclash.protocol.PROTOCOL_VERSION
import dev.colorclash.protocol.PhaseKind
import dev.colorclash.protocol.PlayerIntent
import dev.colorclash.protocol.ServerMessage
import dev.colorclash.protocol.WS_PATH
import dev.colorclash.rules.RuleSet
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.Collections
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class HostClientIntegrationTest {

    @Test
    fun `six clients play a full game over websockets`(): Unit = runBlocking {
        Harness().start().use { h ->
            h.host.updateSettings(GameSettings(RuleSet(targetScore = 200, stackDraws = true, jumpIn = true)))
            val clients = (1..6).map { h.client("P$it") }
            val received = clients.associateWith { Collections.synchronizedList(mutableListOf<ServerMessage.Game>()) }
            clients.forEach { c -> h.scope.launch { c.updates.collect { received.getValue(c) += it } } }
            clients.forEach { it.awaitConnected() }
            clients.forEach { it.setReady(true) }
            h.awaitHost { it.seats.size == 6 && it.canStart }

            assertNull(h.host.startGame())
            clients.forEach { h.scope.autoPlay(it) }
            val final = h.awaitHost(60.seconds) { it.stage == HostStage.GAME_OVER }
            val state = assertNotNull(final.game)

            for (client in clients) {
                val view = withTimeout(5.seconds) { client.game.filterNotNull().first { it.phase == PhaseKind.GAME_OVER } }
                assertEquals(state.hand(view.you), view.hand)
                assertEquals(state.players.map { it.score }, view.seats.map { it.score })

                // Nobody ever saw another player's dealt or drawn cards.
                val events = synchronized(received.getValue(client)) { received.getValue(client).flatMap { it.events } }
                assertTrue(events.isNotEmpty())
                for (event in events) {
                    when (event) {
                        is EventView.Drew -> assertEquals(event.player == view.you, event.cards != null)
                        is EventView.Dealt -> assertEquals(event.player == view.you, event.cards != null)
                        else -> Unit
                    }
                }
            }
        }
    }

    @Test
    fun `a dropped client reconnects on its own and gets resynced`(): Unit = runBlocking {
        Harness().start().use { h ->
            h.host.updateSettings(GameSettings(RuleSet(targetScore = 300)))
            val clients = (1..3).map { h.client("P$it") }
            clients.forEach { it.awaitConnected(); it.setReady(true) }
            h.awaitHost { it.seats.size == 3 && it.canStart }
            assertNull(h.host.startGame())

            val victim = clients[1]
            val updates = Collections.synchronizedList(mutableListOf<ServerMessage.Game>())
            h.scope.launch { victim.updates.collect { updates += it } }

            val running = MutableStateFlow(true)
            clients.forEach { h.scope.autoPlay(it, running) }
            h.awaitHost { (it.game?.round ?: 0) >= 1 && it.stage == HostStage.PLAYING }
            running.value = false
            val seat = withTimeout(5.seconds) { victim.game.filterNotNull().first() }.you
            delay(200) // let any moves already in flight land

            val token = victim.sessionToken
            val seenBeforeDrop = updates.size
            victim.dropConnection()
            victim.awaitStatus { it is ClientStatus.Reconnecting }
            victim.awaitConnected()
            assertEquals(token, victim.sessionToken)

            // The host resends full state on reconnect, and it matches the authoritative hand.
            val resync = withTimeout(5.seconds) {
                while (synchronized(updates) { updates.drop(seenBeforeDrop).none { it.events.isEmpty() } }) delay(10)
                synchronized(updates) { updates.drop(seenBeforeDrop).first { it.events.isEmpty() } }
            }
            assertEquals(seat, resync.view.you)
            assertEquals(h.host.status.value.game?.hand(seat), resync.view.hand)
            assertTrue(h.host.status.value.seats[seat].connected)

            running.value = true
            h.awaitHost(60.seconds) { it.stage == HostStage.GAME_OVER }
        }
    }

    @Test
    fun `bot takes over after the grace period and the player can reclaim the seat`(): Unit = runBlocking {
        Harness(fastHost.copy(reconnectGrace = 300.milliseconds)).start().use { h ->
            h.host.updateSettings(GameSettings(RuleSet(targetScore = 500)))
            val clients = (1..3).map { h.client("P$it") }
            clients.forEach { it.awaitConnected(); it.setReady(true) }
            h.awaitHost { it.seats.size == 3 && it.canStart }
            assertNull(h.host.startGame())

            val running = MutableStateFlow(false)
            clients.forEach { h.scope.autoPlay(it, running) }
            val victim = clients[2]
            val seat = assertNotNull(withTimeout(5.seconds) { victim.game.filterNotNull().first() }).you
            val token = assertNotNull(victim.sessionToken)

            // Phone dies without a goodbye.
            victim.close()
            h.awaitHost { !it.seats[seat].connected && !it.seats[seat].botControlled }
            h.awaitHost { it.seats[seat].botControlled }

            // The game keeps going with the bot in that seat.
            running.value = true
            val before = h.host.status.value.game?.hand(seat)
            h.awaitHost { it.game?.hand(seat) != before || it.stage != HostStage.PLAYING }
            running.value = false

            val back = h.client("P3 again", token = token)
            back.awaitConnected()
            val reclaimed = h.awaitHost { it.seats[seat].connected && !it.seats[seat].botControlled }
            assertEquals("P3", reclaimed.seats[seat].name)
            assertEquals(seat, withTimeout(5.seconds) { back.game.filterNotNull().first() }.you)

            h.scope.autoPlay(back, running)
            running.value = true
            h.awaitHost(60.seconds) { it.stage == HostStage.GAME_OVER }
        }
    }

    @Test
    fun `host with bots in empty seats plays a solo game`(): Unit = runBlocking {
        Harness().start().use { h ->
            val token = h.host.reserveHostSeat("Me")
            repeat(3) { h.host.addBot() }
            h.host.updateSettings(GameSettings(RuleSet(targetScore = 200)))
            val me = h.client("Me", token = token)
            me.awaitConnected()
            val lobby = withTimeout(5.seconds) { me.lobby.filterNotNull().first { it.seats.size == 4 } }
            assertTrue(lobby.seats[0].isHost)
            assertEquals(3, lobby.seats.count { it.isBot })
            assertTrue(lobby.canStart)

            assertNull(h.host.startGame())
            h.scope.autoPlay(me)
            h.awaitHost(60.seconds) { it.stage == HostStage.GAME_OVER }
        }
    }

    @Test
    fun `turn timer plays for a player who doesn't move`(): Unit = runBlocking {
        Harness().start().use { h ->
            h.host.updateSettings(GameSettings(RuleSet(), turnTimerSeconds = 1))
            val active = h.client("Active")
            val idle = h.client("Idle")
            listOf(active, idle).forEach { it.awaitConnected(); it.setReady(true) }
            h.awaitHost { it.seats.size == 2 && it.canStart }
            assertNull(h.host.startGame())
            h.scope.autoPlay(active)

            val idleSeat = assertNotNull(withTimeout(5.seconds) { idle.game.filterNotNull().first() }).you
            val waiting = withTimeout(10.seconds) { idle.game.filterNotNull().first { it.current == idleSeat && it.phase == PhaseKind.TURN } }
            assertNotNull(waiting.turnMillisLeft)
            // The timer runs out and the host moves on without the idle player doing anything.
            withTimeout(4.seconds) { idle.game.filterNotNull().first { it.current != idleSeat || it.phase != PhaseKind.TURN } }
        }
    }

    @Test
    fun `out of turn intents are rejected`(): Unit = runBlocking {
        Harness().start().use { h ->
            val a = h.client("A")
            val b = h.client("B")
            listOf(a, b).forEach { it.awaitConnected(); it.setReady(true) }
            h.awaitHost { it.seats.size == 2 && it.canStart }
            assertNull(h.host.startGame())
            val view = withTimeout(5.seconds) { a.game.filterNotNull().first() }
            val waiting = if (view.isYourTurn) b else a

            val notice = h.scope.async { withTimeout(5.seconds) { waiting.notices.first() } }
            delay(50)
            waiting.send(PlayerIntent.Draw)
            assertTrue(notice.await() in setOf("Not your turn", "Can't draw now"))
            assertEquals(view.hand, a.game.value?.hand)
        }
    }

    @Test
    fun `wrong room code is refused`(): Unit = runBlocking {
        Harness().start().use { h ->
            val client = h.client("Lost", room = "NOPE")
            val status = client.awaitStatus { it is ClientStatus.Failed }
            assertEquals(JoinError.WRONG_ROOM, (status as ClientStatus.Failed).error)
        }
    }

    @Test
    fun `joining a running game without a token is refused`(): Unit = runBlocking {
        Harness().start().use { h ->
            listOf(h.client("A"), h.client("B")).forEach { it.awaitConnected(); it.setReady(true) }
            h.awaitHost { it.seats.size == 2 && it.canStart }
            assertNull(h.host.startGame())
            val late = h.client("Late")
            val status = late.awaitStatus { it is ClientStatus.Failed }
            assertEquals(JoinError.GAME_IN_PROGRESS, (status as ClientStatus.Failed).error)
        }
    }

    @Test
    fun `mismatched protocol version gets a clear message`(): Unit = runBlocking {
        Harness().start().use { h ->
            val reply = rawExchange(h.port, Codec.encode(ClientMessage.Hello(PROTOCOL_VERSION + 1, ROOM, "Old")))
            val rejected = assertIs<ServerMessage.JoinRejected>(reply)
            assertEquals(JoinError.VERSION_MISMATCH, rejected.error)
            assertTrue("same build" in rejected.message)
        }
    }

    @Test
    fun `unreadable hello is treated as a version mismatch`(): Unit = runBlocking {
        Harness().start().use { h ->
            val reply = rawExchange(h.port, """{"type":"helloV9","stuff":true}""")
            assertEquals(JoinError.VERSION_MISMATCH, assertIs<ServerMessage.JoinRejected>(reply).error)
        }
    }

    @Test
    fun `silent connection is dropped by the heartbeat timeout`(): Unit = runBlocking {
        Harness().start().use { h ->
            val http = HttpClient(CIO) { install(WebSockets) }
            http.use {
                it.webSocket(host = "127.0.0.1", port = h.port, path = WS_PATH) {
                    send(Frame.Text(Codec.encode(ClientMessage.Hello(PROTOCOL_VERSION, ROOM, "Quiet"))))
                    h.awaitHost { s -> s.seats.size == 1 }
                    // Say nothing more; the host should give up on us and free the lobby seat.
                    h.awaitHost(5.seconds) { s -> s.seats.isEmpty() }
                }
            }
        }
    }

    @Test
    fun `room full is refused`(): Unit = runBlocking {
        Harness().start().use { h ->
            repeat(6) { h.host.addBot() }
            h.awaitHost { it.seats.size == 6 }
            val status = h.client("Seventh").awaitStatus { it is ClientStatus.Failed }
            assertEquals(JoinError.ROOM_FULL, (status as ClientStatus.Failed).error)
        }
    }

    /** Sends one raw frame and returns the first reply. */
    private suspend fun rawExchange(port: Int, text: String): ServerMessage {
        val http = HttpClient(CIO) { install(WebSockets) }
        return http.use {
            var reply: ServerMessage? = null
            it.webSocket(host = "127.0.0.1", port = port, path = WS_PATH) {
                send(Frame.Text(text))
                val frame = withTimeout(5.seconds) { incoming.receive() } as Frame.Text
                reply = Codec.decodeServer(frame.readText())
            }
            assertNotNull(reply)
        }
    }

}
