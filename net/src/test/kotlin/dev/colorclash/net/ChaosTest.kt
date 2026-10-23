package dev.colorclash.net

import dev.colorclash.net.client.ClientStatus
import dev.colorclash.net.host.HostStage
import dev.colorclash.protocol.GameSettings
import dev.colorclash.protocol.PhaseKind
import dev.colorclash.rules.RuleSet
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val MIN_DROPS = 20

/** Real-world network trouble, simulated with a misbehaving relay in front of every client. */
class ChaosTest {

    @Test
    fun `a full game survives constant wifi blips`(): Unit = runBlocking {
        Harness().start().use { h ->
            h.host.updateSettings(GameSettings(RuleSet(targetScore = 500)))
            val proxies = List(4) { ChaosProxy(h.port) }
            try {
                val clients = proxies.mapIndexed { i, proxy -> h.client("P$i", viaPort = proxy.port) }
                clients.forEach { it.awaitConnected(); it.setReady(true) }
                h.awaitHost { it.seats.size == 4 && it.canStart }
                assertNull(h.host.startGame())
                clients.forEach { h.scope.autoPlay(it) }

                // Somebody's link drops every few dozen milliseconds for the whole game.
                val random = Random(1)
                var drops = 0
                val chaos = h.scope.launch {
                    while (isActive) {
                        delay(random.nextLong(20, 120))
                        proxies[random.nextInt(proxies.size)].dropAll()
                        drops++
                    }
                }
                val final = h.awaitHost(120.seconds) { it.stage == HostStage.GAME_OVER }
                chaos.cancel()
                val state = assertNotNull(final.game)
                println("Game finished through $drops dropped connections")
                assertTrue(drops >= MIN_DROPS, "Only $drops drops; the game ended too fast to prove anything")

                // Once things calm down, every phone ends up showing exactly what the host has.
                for (client in clients) {
                    client.awaitConnected()
                    val view = withTimeout(10.seconds) { client.game.filterNotNull().first { it.phase == PhaseKind.GAME_OVER } }
                    assertEquals(state.hand(view.you), view.hand)
                    assertEquals(state.players.map { it.score }, view.seats.map { it.score })
                }
            } finally {
                proxies.forEach { it.close() }
            }
        }
    }

    @Test
    fun `a frozen phone is noticed, covered by a bot, and gets its seat back`(): Unit = runBlocking {
        Harness(fastHost.copy(heartbeatTimeout = 800.milliseconds, reconnectGrace = 1_200.milliseconds)).start().use { h ->
            h.host.updateSettings(GameSettings(RuleSet(targetScore = 300)))
            val frozenLink = ChaosProxy(h.port)
            try {
                val steady = List(2) { h.client("Steady$it") }
                val frozen = h.client("Frozen", viaPort = frozenLink.port)
                (steady + frozen).forEach { it.awaitConnected(); it.setReady(true) }
                h.awaitHost { it.seats.size == 3 && it.canStart }
                assertNull(h.host.startGame())
                (steady + frozen).forEach { h.scope.autoPlay(it) }
                val seat = withTimeout(5.seconds) { frozen.game.filterNotNull().first() }.you

                // The link goes silent without closing, like a phone frozen in someone's pocket.
                frozenLink.stalled = true
                h.awaitHost(5.seconds) { !it.seats[seat].connected }
                h.awaitHost(5.seconds) { it.seats[seat].botControlled }
                frozen.awaitStatus(5.seconds) { it is ClientStatus.Reconnecting }

                // Back to life: it reconnects with its token and takes the seat back from the bot.
                frozenLink.stalled = false
                frozenLink.dropAll()
                frozen.awaitConnected()
                h.awaitHost(10.seconds) { it.seats[seat].connected && !it.seats[seat].botControlled }

                h.awaitHost(120.seconds) { it.stage == HostStage.GAME_OVER }
            } finally {
                frozenLink.close()
            }
        }
    }
}
