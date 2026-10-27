package dev.colorclash.protocol

import dev.colorclash.rules.Action
import dev.colorclash.rules.Card
import dev.colorclash.rules.CardColor
import dev.colorclash.rules.DrawReason
import dev.colorclash.rules.EngineResult
import dev.colorclash.rules.Face
import dev.colorclash.rules.GameEngine
import dev.colorclash.rules.GameEvent
import dev.colorclash.rules.GameState
import dev.colorclash.rules.Phase
import dev.colorclash.rules.RuleSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProtocolTest {
    private val seats = List(4) { SeatInfo(it, "P$it", avatar = it) }
    private val settings = GameSettings()
    private val game = GameEngine.newGame(4, RuleSet(), seed = 3).state

    @Test
    fun `messages round trip through json`() {
        val client = listOf(
            ClientMessage.Hello(PROTOCOL_VERSION, "ABCD", "Sam", 2, rejoinNonce = "a".repeat(32), rejoinProof = "b".repeat(64)),
            ClientMessage.Hello(PROTOCOL_VERSION, "ABCD", "Sam", 20, avatarColor = 4, avatarFrame = 3, photo = "/9j/AA=="),
            ClientMessage.Intent(PlayerIntent.Play(12)),
            ClientMessage.Intent(PlayerIntent.ChooseColor(CardColor.BLUE)),
            ClientMessage.Intent(PlayerIntent.Draw),
            ClientMessage.Ping(5),
            ClientMessage.Leave,
            ClientMessage.React(3),
        )
        client.forEach { assertEquals(it, Codec.decodeClient(Codec.encode(it))) }

        val view = game.viewFor(0, seats, settings)
        val server = listOf(
            ServerMessage.Welcome(0, "tok", "ABCD"),
            ServerMessage.JoinRejected(JoinError.ROOM_FULL, "full"),
            ServerMessage.Game(view, listOf(EventView.Drew(1, 2, DrawReason.TURN))),
            ServerMessage.Lobby(LobbyView("ABCD", 0, seats, settings, canStart = true)),
            ServerMessage.Reaction(2, 7),
            ServerMessage.Photo("abc123", "/9j/AA=="),
            ServerMessage.Closed("bye"),
        )
        server.forEach { assertEquals(it, Codec.decodeServer(Codec.encode(it))) }
    }

    @Test
    fun `hello json shape is stable`() {
        val json = Codec.encode(ClientMessage.Hello(1, "ABCD", "Sam"))
        assertEquals("""{"type":"hello","protocolVersion":1,"roomCode":"ABCD","name":"Sam"}""", json)
    }

    @Test
    fun `an older hello without avatar looks still decodes`() {
        val hello = Codec.decodeClient("""{"type":"hello","protocolVersion":2,"roomCode":"ABCD","name":"Sam","avatar":5}""")
        assertEquals(ClientMessage.Hello(2, "ABCD", "Sam", 5), hello)
        assertEquals(AUTO_AVATAR_COLOR, (hello as ClientMessage.Hello).avatarColor)
        assertEquals(0, hello.avatarFrame)
    }

    @Test
    fun `json can't smuggle in an impossible card`() {
        assertFailsWith<IllegalArgumentException> {
            Codec.json.decodeFromString(Card.serializer(), """{"id":1,"face":"WILD","color":"RED"}""")
        }
    }

    @Test
    fun `view only contains your own hand`() {
        for (seat in 0 until 4) {
            val view = game.viewFor(seat, seats, settings)
            assertEquals(game.hand(seat), view.hand)
            val visible = view.hand.map { it.id }.toSet() + view.topCard.id
            val othersCards = (0 until 4).filter { it != seat }.flatMap { game.hand(it) }.map { it.id }
            assertTrue(othersCards.none { it in visible })
            assertEquals(game.players.map { it.hand.size }, view.seats.map { it.cardCount })
        }
    }

    @Test
    fun `playable cards match the engine`() {
        val view = game.viewFor(game.current, seats, settings)
        val expected = game.hand(game.current).filter { GameEngine.isPlayable(game, game.current, it) }.map { it.id }.toSet()
        assertEquals(expected, view.playable)
        val other = (game.current + 1) % 4
        assertTrue(game.viewFor(other, seats, settings).playable.isEmpty())
    }

    @Test
    fun `dealt and drawn cards are only shown to their owner`() {
        val cards = listOf(Card(1, Face.ONE, CardColor.RED), Card(2, Face.TWO, CardColor.RED))
        assertEquals(EventView.Drew(1, 2, DrawReason.TURN, cards), GameEvent.Drew(1, cards, DrawReason.TURN).toView(1))
        assertEquals(EventView.Drew(1, 2, DrawReason.TURN, null), GameEvent.Drew(1, cards, DrawReason.TURN).toView(0))
        assertEquals(EventView.Dealt(3, 2, cards), GameEvent.Dealt(3, cards).toView(3))
        assertEquals(EventView.Dealt(3, 2, null), GameEvent.Dealt(3, cards).toView(2))
    }

    @Test
    fun `drawn card id is only shown to the drawer`() {
        val state = drawUntilPlayableDraw()
        val drawer = state.current
        assertNotNull(state.viewFor(drawer, seats, settings).drawnCardId)
        for (seat in 0 until 4) if (seat != drawer) assertNull(state.viewFor(seat, seats, settings).drawnCardId)
    }

    @Test
    fun `intent becomes an action for the connection's seat`() {
        assertEquals(Action.PlayCard(3, 9), PlayerIntent.Play(9).toAction(3))
        assertEquals(Action.CatchLastCard(2, 0), PlayerIntent.Catch(0).toAction(2))
    }

    /** Finds a seed where the first player's draw is playable. */
    private fun drawUntilPlayableDraw(): GameState {
        for (seed in 0L..500L) {
            val start = GameEngine.newGame(4, RuleSet(drawUntilPlayable = true), seed).state
            if (start.phase != Phase.Turn) continue
            val after = GameEngine.reduce(start, Action.Draw(start.current)) as EngineResult.Ok
            if (after.state.phase is Phase.DrewPlayable) return after.state
        }
        error("No seed found")
    }
}
