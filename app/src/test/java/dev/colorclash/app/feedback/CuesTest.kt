package dev.colorclash.app.feedback

import dev.colorclash.app.ui.theme.GameTheme
import dev.colorclash.protocol.EventView
import dev.colorclash.rules.Card
import dev.colorclash.rules.CardColor
import dev.colorclash.rules.Direction
import dev.colorclash.rules.DrawReason
import dev.colorclash.rules.Face
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CuesTest {
    private val red5 = Card(1, Face.FIVE, CardColor.RED)

    @Test
    fun `your own moves tick, others only make sound`() {
        assertEquals(Cue(Sfx.FLIP, Buzz.TICK), cueFor(EventView.CardPlayed(0, red5), you = 0))
        assertEquals(Cue(Sfx.FLIP), cueFor(EventView.CardPlayed(1, red5), you = 0))
    }

    @Test
    fun `getting hit by a draw card bumps, drawing on your own turn doesn't`() {
        assertEquals(Cue(Sfx.DRAW, Buzz.BUMP), cueFor(EventView.Drew(0, 2, DrawReason.DRAW_CARD), you = 0))
        assertEquals(Cue(Sfx.DRAW), cueFor(EventView.Drew(0, 1, DrawReason.TURN), you = 0))
        assertEquals(Cue(Sfx.PENALTY, Buzz.HEAVY), cueFor(EventView.Drew(0, 2, DrawReason.POWER_FINISH), you = 0))
        assertEquals(Cue(Sfx.PENALTY), cueFor(EventView.Drew(1, 2, DrawReason.POWER_FINISH), you = 0))
    }

    @Test
    fun `penalties hit hard only for the one who pays`() {
        assertEquals(Cue(Sfx.PENALTY, Buzz.HEAVY), cueFor(EventView.PenaltyApplied(2, caughtBy = 1, cards = 2), you = 2))
        assertEquals(Cue(Sfx.PENALTY), cueFor(EventView.PenaltyApplied(2, caughtBy = 1, cards = 2), you = 1))
        assertEquals(Cue(Sfx.PENALTY, Buzz.HEAVY), cueFor(EventView.Challenged(challenger = 1, offender = 0, succeeded = true), you = 0))
        assertEquals(Cue(Sfx.PENALTY, Buzz.HEAVY), cueFor(EventView.Challenged(challenger = 1, offender = 0, succeeded = false), you = 1))
    }

    @Test
    fun `win and lose`() {
        assertEquals(Cue(Sfx.WIN, Buzz.SUCCESS), cueFor(EventView.RoundWon(3, 50, 120), you = 3))
        assertEquals(Cue(Sfx.LOSE), cueFor(EventView.RoundWon(3, 50, 120), you = 0))
        assertNull(cueFor(EventView.GameWon(3), you = 3))
    }

    @Test
    fun `table events`() {
        assertEquals(Cue(Sfx.REVERSE), cueFor(EventView.Reversed(Direction.COUNTER_CLOCKWISE), you = 0))
        assertEquals(Cue(Sfx.WILD), cueFor(EventView.WildColorChosen(1, CardColor.BLUE), you = 0))
        assertNull(cueFor(EventView.Dealt(0, 7), you = 0))
    }

    @Test
    fun `only the Faaa phrase plays the clip`() {
        val faaa = GameTheme.reactions.size + GameTheme.phrases.indexOf(GameTheme.FAAA)
        assertEquals(Sfx.FAAA, reactionSfx(faaa))
        assertEquals(Sfx.POP, reactionSfx(0))
        assertEquals(Sfx.POP, reactionSfx(GameTheme.reactions.size))
    }
}
