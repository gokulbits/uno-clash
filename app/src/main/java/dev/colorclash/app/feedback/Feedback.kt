package dev.colorclash.app.feedback

import dev.colorclash.app.ui.theme.GameTheme
import dev.colorclash.protocol.EventView
import dev.colorclash.rules.DrawReason

data class Cue(val sfx: Sfx?, val buzz: Buzz? = null)

val YOUR_TURN_CUE = Cue(Sfx.YOUR_TURN, Buzz.TICK)

/** Dealing has no cue here: each dealt card gets its own sound as it lands. */
fun cueFor(event: EventView, you: Int): Cue? = when (event) {
    is EventView.RoundStarted -> Cue(Sfx.SHUFFLE)
    is EventView.Dealt -> null
    is EventView.StarterFlipped -> Cue(Sfx.FLIP)
    is EventView.CardPlayed -> Cue(Sfx.FLIP, Buzz.TICK.takeIf { event.player == you })
    is EventView.Drew -> if (event.reason == DrawReason.POWER_FINISH) {
        Cue(Sfx.PENALTY, Buzz.HEAVY.takeIf { event.player == you })
    } else {
        Cue(Sfx.DRAW, Buzz.BUMP.takeIf { event.player == you && event.reason != DrawReason.TURN })
    }
    is EventView.Skipped -> Cue(Sfx.SKIP, Buzz.BUMP.takeIf { event.player == you })
    is EventView.Reversed -> Cue(Sfx.REVERSE)
    is EventView.WildColorChosen -> Cue(Sfx.WILD)
    is EventView.LastCardCalled -> Cue(Sfx.LAST_CARD, Buzz.SUCCESS.takeIf { event.player == you })
    is EventView.PenaltyApplied -> Cue(Sfx.PENALTY, Buzz.HEAVY.takeIf { event.player == you })
    is EventView.Challenged -> {
        val youLost = (event.succeeded && event.offender == you) || (!event.succeeded && event.challenger == you)
        Cue(Sfx.PENALTY, Buzz.HEAVY.takeIf { youLost })
    }
    is EventView.HandsSwapped -> Cue(Sfx.SHUFFLE, Buzz.BUMP.takeIf { you == event.player || you == event.target })
    is EventView.HandsRotated -> Cue(Sfx.SHUFFLE, Buzz.BUMP)
    is EventView.DeckReshuffled -> Cue(Sfx.SHUFFLE)
    is EventView.RoundWon -> if (event.player == you) Cue(Sfx.WIN, Buzz.SUCCESS) else Cue(Sfx.LOSE)
    // The RoundWon just before it already played the fanfare.
    is EventView.GameWon -> null
}

/** Emoji pop; the meme phrases bring their own noise. */
fun reactionSfx(reaction: Int): Sfx = when (GameTheme.reaction(reaction)) {
    GameTheme.FAAA -> Sfx.FAAA
    GameTheme.SUS -> Sfx.SUS
    GameTheme.BRUH -> Sfx.BRUH
    GameTheme.EH_EH_EH -> Sfx.EH_EH_EH
    GameTheme.QUACK -> Sfx.QUACK
    GameTheme.PUNCH -> Sfx.PUNCH
    GameTheme.RIZZ -> Sfx.RIZZ
    GameTheme.SHOCKED -> Sfx.SHOCKED
    GameTheme.WOWW -> Sfx.WOWW
    else -> Sfx.POP
}

/** An effect that just played stays quiet for its own length, however often it's asked for. */
fun canPlayAgain(lastMillis: Long?, nowMillis: Long, minGapMillis: Long): Boolean =
    minGapMillis <= 0 || lastMillis == null || nowMillis - lastMillis >= minGapMillis

class Feedback(private val sound: SoundEngine, private val haptics: Haptics, private val voice: Announcer) {
    fun cue(cue: Cue?) {
        cue ?: return
        cue.sfx?.let { sound.play(it) }
        cue.buzz?.let { haptics.buzz(it) }
    }

    fun sfx(sfx: Sfx, volume: Float = 1f) = sound.play(sfx, volume)

    fun buzz(kind: Buzz) = haptics.buzz(kind)

    fun say(text: String) = voice.say(text)
}
