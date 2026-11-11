package dev.colorclash.protocol

import dev.colorclash.rules.Action
import dev.colorclash.rules.Card
import dev.colorclash.rules.CardColor
import dev.colorclash.rules.DrawReason
import dev.colorclash.rules.EngineResult
import dev.colorclash.rules.Face
import dev.colorclash.rules.GameEngine
import dev.colorclash.rules.GameEvent
import dev.colorclash.rules.GameMode
import dev.colorclash.rules.GameState
import dev.colorclash.rules.Phase
import dev.colorclash.rules.Player
import dev.colorclash.rules.RuleSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProtocolTest {
    private val seats = List(4) { SeatInfo(it, "P$it", avatar = it) }
    private val settings = GameSettings()
    private val game = GameEngine.newGame(4, RuleSet(), seed = 3).state
    private val noMercy = GameEngine.newGame(4, RuleSet(mode = GameMode.NO_MERCY, targetScore = 1000), seed = 3).state

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
    fun `flip shows the hands facing you and never your own cards' other side`() {
        val flip = GameEngine.newGame(4, RuleSet(mode = GameMode.FLIP), seed = 3).state
        for (seat in 0 until 4) {
            val view = flip.viewFor(seat, seats, settings)
            assertTrue(view.hand.all { it.back == null }, "own hand")
            assertNull(view.topCard.back)
            // Every other seat's hand is public from the side it faces the table with, and no further.
            assertEquals(List(4) { if (it == seat) 0 else flip.hand(it).size }, view.seats.map { it.facing.size })
            // The light side is in play, so every side facing you is a dark one or a wild.
            assertTrue(view.seats.flatMap { it.facing }.all { it.color == null || it.color in CardColor.dark })
            assertEquals(flip.hand((seat + 1) % 4).map { it.back }, view.seats[(seat + 1) % 4].facing)
        }
        val dealt = GameEvent.Dealt(1, flip.hand(1)).toView(1) as EventView.Dealt
        assertTrue(dealt.cards!!.all { it.back == null })
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
    fun `no mercy events survive the wire and hide nothing`() {
        val cards = listOf(Card(1, Face.ONE, CardColor.RED), Card(2, Face.SKIP, CardColor.RED))
        val events = listOf(
            EventView.EveryoneSkipped(2),
            EventView.DiscardedAll(1, CardColor.RED, cards),
            EventView.ColorRoulette(3, CardColor.BLUE, 7),
            EventView.KnockedOut(0, 26),
        )
        val message = ServerMessage.Game(noMercy.viewFor(0, seats, settings), events)
        assertEquals(message, Codec.decodeServer(Codec.encode(message)))

        // These are all public: the cards went onto the pile or came off it in the open.
        assertEquals(events[1], GameEvent.DiscardedAll(1, CardColor.RED, cards).toView(2))
        assertEquals(events[3], GameEvent.KnockedOut(0, 26).toView(1))
    }

    @Test
    fun `a knocked out seat and a roulette pick show up in the view`() {
        val state = noMercy.copy(
            players = noMercy.players.mapIndexed { i, p -> if (i == 2) Player(emptyList(), p.score, knockedOut = true) else p },
            phase = Phase.PickColor(roulette = true),
            current = 0,
            pendingDraw = 6,
            pendingDrawValue = 6,
        )
        val view = state.viewFor(0, seats, settings)
        assertTrue(view.seats[2].knockedOut)
        assertFalse(view.seats[0].knockedOut)
        assertTrue(view.roulette)
        assertEquals(6, view.pendingDrawValue)
        assertEquals(0, view.seats[2].cardCount)
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
