package dev.colorclash.app.ui.game

import dev.colorclash.protocol.EventView
import dev.colorclash.protocol.GameSettings
import dev.colorclash.protocol.PhaseKind
import dev.colorclash.protocol.PlayerView
import dev.colorclash.protocol.SeatInfo
import dev.colorclash.protocol.viewFor
import dev.colorclash.rules.Action
import dev.colorclash.rules.Card
import dev.colorclash.rules.CardColor
import dev.colorclash.rules.Direction
import dev.colorclash.rules.DrawReason
import dev.colorclash.rules.EngineResult
import dev.colorclash.rules.Face
import dev.colorclash.rules.GameEngine
import dev.colorclash.rules.GameMode
import dev.colorclash.rules.GameState
import dev.colorclash.rules.Phase
import dev.colorclash.rules.Player
import dev.colorclash.rules.RuleSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TableLogicTest {
    private val seats = listOf("Ana", "Ben", "Cy", "Dee").mapIndexed { i, n -> SeatInfo(i, n, i) }

    private fun card(id: Int, face: Face, color: CardColor? = CardColor.RED) = Card(id, face, color)

    /** Four players, Ana (seat 0) to play on a red 9. */
    private fun table(
        hands: List<List<Card>> = List(4) { s -> List(3) { card(100 + s * 10 + it, Face.ONE, CardColor.BLUE) } },
        rules: RuleSet = RuleSet(),
        current: Int = 0,
    ) = GameState(
        rules = rules,
        players = hands.map { Player(it) },
        drawPile = List(10) { card(200 + it, Face.EIGHT, CardColor.GREEN) },
        discardPile = listOf(card(1, Face.NINE)),
        activeColor = CardColor.RED,
        direction = Direction.CLOCKWISE,
        current = current,
        phase = Phase.Turn,
    )

    private fun GameState.view(seat: Int): PlayerView = viewFor(seat, seats, GameSettings(rules))

    private fun GameState.then(action: Action): GameState = (GameEngine.reduce(this, action) as EngineResult.Ok).state

    @Test
    fun `your turn offers drawing`() {
        val controls = tableControls(table().view(0))
        assertEquals("Your turn", controls.headline)
        assertTrue(controls.canDraw)
        assertEquals("Draw", controls.drawLabel)
        assertFalse(controls.canKeep)
    }

    @Test
    fun `someone else's turn names them`() {
        val controls = tableControls(table().view(2))
        assertEquals("Ana's turn", controls.headline)
        assertFalse(controls.canDraw)
    }

    @Test
    fun `wild asks you for a color`() {
        val wild = card(5, Face.WILD, null)
        val hands = listOf(listOf(wild, card(6, Face.ONE), card(7, Face.TWO))) + List(3) { listOf(card(20 + it, Face.ONE)) }
        val state = table(hands).then(Action.PlayCard(0, wild.id))
        assertTrue(tableControls(state.view(0)).pickingColor)
        assertEquals("Ana is picking a color", tableControls(state.view(1)).headline)
        assertFalse(tableControls(state.view(1)).pickingColor)
    }

    @Test
    fun `plus four on you offers challenge and take four`() {
        val w4 = card(5, Face.WILD_DRAW_FOUR, null)
        val hands = listOf(listOf(w4, card(6, Face.ONE), card(7, Face.TWO))) + List(3) { List(3) { i -> card(30 + it * 5 + i, Face.ONE, CardColor.BLUE) } }
        val state = table(hands).then(Action.PlayCard(0, w4.id)).then(Action.ChooseColor(0, CardColor.GREEN))
        val controls = tableControls(state.view(1))
        assertTrue(controls.canChallenge)
        assertEquals("Take 4", controls.drawLabel)
        assertTrue(controls.headline.startsWith("Wild +4 on you"))
    }

    @Test
    fun `drawing a playable card offers keep`() {
        val playable = card(200, Face.EIGHT, CardColor.RED)
        val state = table().copy(drawPile = listOf(playable)).then(Action.Draw(0))
        val controls = tableControls(state.view(0))
        assertTrue(controls.canKeep)
        assertEquals("Play the card you drew, or keep it", controls.headline)
    }

    @Test
    fun `UNO can be called at two cards and catch targets the exposed player`() {
        val r5 = card(5, Face.FIVE)
        val hands = listOf(listOf(r5, card(6, Face.ONE, CardColor.BLUE))) + List(3) { List(3) { i -> card(30 + it * 5 + i, Face.ONE, CardColor.BLUE) } }
        val start = table(hands)
        assertTrue(tableControls(start.view(0)).canCallLastCard)

        val exposed = start.then(Action.PlayCard(0, r5.id))
        assertEquals(0, tableControls(exposed.view(2)).catchTarget)
        assertNull(tableControls(exposed.view(0)).catchTarget)
    }

    @Test
    fun `round over names the winner`() {
        val state = table().copy(phase = Phase.RoundOver(winner = 1, points = 40))
        assertEquals("Ben won the round", tableControls(state.view(0)).headline)
        assertEquals("You won the round!", tableControls(state.view(1)).headline)
        assertEquals(PhaseKind.ROUND_OVER, state.view(0).phase)
    }

    @Test
    fun `opponents run clockwise from the next player`() {
        assertEquals(listOf(3, 0, 1), opponentOrder(table().view(2)))
        assertEquals(listOf(1, 2, 3), opponentOrder(table().view(0)))
    }

    @Test
    fun `arc runs left to right and is symmetric`() {
        val arc = arcPositions(5)
        assertEquals(arc.map { it.first }.sorted(), arc.map { it.first })
        assertEquals(0.5f, arc[2].first, 0.001f)
        assertEquals(arc[0].second, arc[4].second, 0.001f)
        assertTrue(arc[2].second < arc[0].second, "middle seat sits highest")
        assertEquals(listOf(0.5f to 0.15f), arcPositions(1))
    }

    @Test
    fun `hand is grouped by color then face, wilds last`() {
        val hand = listOf(
            card(1, Face.WILD, null), card(2, Face.NINE, CardColor.BLUE), card(3, Face.TWO, CardColor.RED),
            card(4, Face.ONE, CardColor.BLUE), card(5, Face.SKIP, CardColor.RED),
        )
        assertEquals(listOf(3, 5, 4, 2, 1), sortHand(hand).map { it.id })
        // By number: faces in order, colors breaking ties, wilds still last.
        assertEquals(listOf(4, 3, 2, 5, 1), sortHand(hand, byNumber = true).map { it.id })
    }

    @Test
    fun `finishing on a power card is stamped and logged`() {
        val view = table().view(0)
        assertEquals(
            listOf(SeatStamp(1, StampMark.DRAW, "POWER FINISH!", good = false, count = 2)),
            seatStampsFor(EventView.Drew(1, 2, DrawReason.POWER_FINISH), you = 0),
        )
        assertEquals("NO POWER FINISH!", seatStampsFor(EventView.Drew(0, 4, DrawReason.POWER_FINISH), you = 0).single().tag)
        assertEquals("Ben can't go out on a power card: +2", describe(EventView.Drew(1, 2, DrawReason.POWER_FINISH), view))
    }

    @Test
    fun `next seat follows the direction of play and stops when the round is over`() {
        val view = table().view(0)
        assertEquals(1, nextSeat(view))
        assertEquals(3, nextSeat(view.copy(direction = Direction.COUNTER_CLOCKWISE)))
        assertEquals(0, nextSeat(view.copy(current = 3)))
        assertNull(nextSeat(view.copy(phase = PhaseKind.ROUND_OVER)))
    }

    @Test
    fun `events read naturally`() {
        val view = table().view(0)
        assertEquals("Ben played red +2", describe(EventView.CardPlayed(1, card(9, Face.DRAW_TWO)), view))
        assertEquals("You drew 1 card", describe(EventView.Drew(0, 1, dev.colorclash.rules.DrawReason.TURN), view))
        assertEquals("Cy caught you! +2", describe(EventView.PenaltyApplied(0, 2, 2), view))
        assertNull(describe(EventView.StarterFlipped(card(9, Face.ONE)), view))
    }

    @Test
    fun `skips, draw cards and challenges stamp the right seats`() {
        assertEquals(
            listOf(SeatStamp(2, StampMark.SKIP, "SKIPPED!", good = false)),
            seatStampsFor(EventView.Skipped(2), you = 0),
        )
        assertEquals("YOU'RE SKIPPED!", seatStampsFor(EventView.Skipped(0), you = 0).single().tag)
        assertEquals(
            listOf(SeatStamp(1, StampMark.DRAW, "DRAW 4!", good = false, count = 4)),
            seatStampsFor(EventView.Drew(1, 4, DrawReason.DRAW_CARD), you = 0),
        )
        assertEquals("YOU DRAW 6!", seatStampsFor(EventView.Drew(0, 6, DrawReason.DRAW_CARD), you = 0).single().tag)
        // Ordinary draws, UNO penalties and challenge cards don't get a stamp of their own.
        assertEquals(emptyList(), seatStampsFor(EventView.Drew(1, 1, DrawReason.TURN), you = 0))
        assertEquals(emptyList(), seatStampsFor(EventView.Drew(1, 2, DrawReason.MISSED_LAST_CARD), you = 0))
        assertEquals(emptyList(), seatStampsFor(EventView.Drew(1, 6, DrawReason.CHALLENGE), you = 0))

        assertEquals(
            listOf(
                SeatStamp(0, StampMark.CHECK, "YOU CAUGHT THEM!", good = true),
                SeatStamp(3, StampMark.CROSS, "BLUFFED!", good = false),
            ),
            seatStampsFor(EventView.Challenged(challenger = 0, offender = 3, succeeded = true), you = 0),
        )
        assertEquals(
            listOf(
                SeatStamp(3, StampMark.CHECK, "LEGIT!", good = true),
                SeatStamp(1, StampMark.CROSS, "WRONG CALL!", good = false),
            ),
            seatStampsFor(EventView.Challenged(challenger = 1, offender = 3, succeeded = false), you = 0),
        )
        assertEquals("BAD CHALLENGE!", seatStampsFor(EventView.Challenged(0, 3, succeeded = false), you = 0)[1].tag)
        assertEquals("YOU GOT CAUGHT!", seatStampsFor(EventView.Challenged(1, 0, succeeded = true), you = 0)[1].tag)
    }

    @Test
    fun `action cards, colors and UNO get announced, plain numbers don't`() {
        val view = table().view(0)
        assertEquals(
            Announcement("SKIP!", "Ben played a Skip", CardColor.RED, "Skip!"),
            announcementFor(EventView.CardPlayed(1, card(9, Face.SKIP)), view),
        )
        assertEquals("+4", announcementFor(EventView.CardPlayed(0, card(9, Face.WILD_DRAW_FOUR, null)), view)?.title)
        assertEquals("You played a Wild Draw Four", announcementFor(EventView.CardPlayed(0, card(9, Face.WILD_DRAW_FOUR, null)), view)?.detail)
        assertEquals("Draw two!", announcementFor(EventView.CardPlayed(2, card(9, Face.DRAW_TWO)), view)?.spoken)
        assertEquals("REVERSE!", announcementFor(EventView.CardPlayed(3, card(9, Face.REVERSE)), view)?.title)
        assertEquals(
            Announcement("BLUE!", "Cy picked blue", CardColor.BLUE, "Blue!"),
            announcementFor(EventView.WildColorChosen(2, CardColor.BLUE), view),
        )
        assertEquals("Dee called UNO", announcementFor(EventView.LastCardCalled(3), view)?.detail)
        assertNull(announcementFor(EventView.CardPlayed(1, card(9, Face.SEVEN)), view))
        assertNull(announcementFor(EventView.Skipped(1), view))
    }

    // No Mercy

    private val noMercy = RuleSet(mode = GameMode.NO_MERCY, targetScore = 1000)

    @Test
    fun `a no mercy stack says what it takes to pass it on`() {
        val state = table(rules = noMercy).copy(
            discardPile = listOf(card(2, Face.WILD_DRAW_SIX, null)),
            pendingDraw = 10,
            pendingDrawValue = 6,
        )
        assertEquals("Stack +6 or higher, or take 10", tableControls(state.view(0)).headline)
        assertEquals("Take 10", tableControls(state.view(0)).drawLabel)
    }

    @Test
    fun `a roulette spin is spelled out to whoever has to call it`() {
        val state = table(rules = noMercy).copy(
            discardPile = listOf(card(2, Face.WILD_COLOR_ROULETTE, null)),
            phase = Phase.PickColor(roulette = true),
        )
        assertEquals("Name a color — you keep everything turned over", tableControls(state.view(0)).headline)
        assertEquals("Ana is spinning for a color", tableControls(state.view(2)).headline)
        assertTrue(tableControls(state.view(0)).pickingColor)
    }

    @Test
    fun `being knocked out takes over the headline and the turn order`() {
        val state = table(rules = noMercy).copy(
            players = List(4) { if (it == 1) Player(emptyList(), knockedOut = true) else Player(listOf(card(300 + it, Face.ONE, CardColor.BLUE))) },
        )
        assertEquals(2, nextSeat(state.view(0)))
        assertEquals("You're out of this round — back next one", tableControls(state.view(1)).headline)
        assertEquals("Ana's turn", tableControls(state.view(2)).headline)
        assertTrue(state.view(2).seats[1].knockedOut)
    }

    @Test
    fun `the new cards all have a name and a callout`() {
        val view = table(rules = noMercy).view(0)
        val faces = listOf(
            Face.DRAW_FOUR, Face.SKIP_EVERYONE, Face.DISCARD_ALL,
            Face.WILD_REVERSE_DRAW_FOUR, Face.WILD_DRAW_SIX, Face.WILD_DRAW_TEN, Face.WILD_COLOR_ROULETTE,
        )
        for (face in faces) {
            val c = card(9, face, if (face.isWild) null else CardColor.RED)
            assertFalse(c.label().contains("null"), "$face has no name")
            assertNotNull(announcementFor(EventView.CardPlayed(1, c), view), "$face has no callout")
        }
        assertEquals("red Discard All", card(9, Face.DISCARD_ALL).label())
        assertEquals("Wild +10", card(9, Face.WILD_DRAW_TEN, null).label())
    }

    @Test
    fun `a knockout is stamped, announced and written up`() {
        val view = table(rules = noMercy).view(0)
        assertEquals("YOU'RE OUT!", seatStampsFor(EventView.KnockedOut(0, 26), you = 0).single().tag)
        assertEquals("OUT!", seatStampsFor(EventView.KnockedOut(1, 25), you = 0).single().tag)
        assertEquals("NO MERCY!", announcementFor(EventView.KnockedOut(1, 25), view)?.title)
        assertEquals("Ben is out on 25 cards", describe(EventView.KnockedOut(1, 25), view))
        assertEquals("Ben skipped everyone and goes again", describe(EventView.EveryoneSkipped(1), view))
        assertEquals("Ben dumped 3 more red cards", describe(EventView.DiscardedAll(1, CardColor.RED, List(3) { card(it, Face.ONE) }), view))
        assertEquals("Ben turned over 7 cards", describe(EventView.Drew(1, 7, DrawReason.ROULETTE), view))
    }
}
