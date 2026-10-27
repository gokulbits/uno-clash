package dev.colorclash.app.ui.room

import dev.colorclash.app.data.Prefs
import dev.colorclash.app.feedback.Feedback
import dev.colorclash.app.feedback.reactionSfx
import dev.colorclash.app.session.Session
import dev.colorclash.app.ui.game.RecentPlay
import dev.colorclash.app.ui.game.RoundResult
import dev.colorclash.app.ui.game.describe
import dev.colorclash.app.ui.game.fx.TableFx
import dev.colorclash.protocol.EventView
import dev.colorclash.protocol.ServerMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Everything a room needs that isn't just the host's latest view: the table chatter, the round
 * history, the recent-plays peek, the notice line, and the lifetime stats this phone keeps.
 *
 * It lives with the session rather than with the composition, so leaving the game screen doesn't
 * lose the game's history, and so writing to disk no longer happens inside a composable.
 *
 * [parent] must be a main-thread scope: the FX state it touches is Compose snapshot state. The FX
 * pump itself is deliberately NOT started here — Animatable needs a MonotonicFrameClock in its
 * context, and only composition provides one, so [RoomScreen] runs `fx.run`.
 */
class RoomPresenter(
    val session: Session,
    private val prefs: Prefs,
    private val feedback: Feedback,
    parent: CoroutineScope,
) {
    val fx = TableFx()

    private val scope = CoroutineScope(parent.coroutineContext + SupervisorJob(parent.coroutineContext[Job]))

    private val _log = MutableStateFlow(emptyList<String>())
    val log: StateFlow<List<String>> = _log.asStateFlow()

    private val _history = MutableStateFlow(emptyList<RoundResult>())
    val history: StateFlow<List<RoundResult>> = _history.asStateFlow()

    private val _recentPlays = MutableStateFlow(emptyList<RecentPlay>())
    val recentPlays: StateFlow<List<RecentPlay>> = _recentPlays.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    init {
        // Collected here rather than on the game screen so the first updates of a round aren't missed.
        scope.launch {
            session.client.updates.collect { update ->
                fx.submit(update)
                record(update)
            }
        }
        scope.launch {
            session.client.reactions.collect {
                fx.react(it.seat, it.reaction)
                feedback.sfx(reactionSfx(it.reaction))
            }
        }
        scope.launch {
            session.client.notices.collectLatest {
                _notice.value = it
                delay(NOTICE_MILLIS)
                _notice.value = null
            }
        }
        // Back in the lobby: forget the last table so the next game doesn't flash the old one.
        scope.launch {
            session.client.game.collect { if (it == null) fx.reset() }
        }
    }

    private fun record(update: ServerMessage.Game) {
        val lines = update.events.mapNotNull { describe(it, update.view) }
        if (lines.isNotEmpty()) _log.value = (_log.value + lines).takeLast(LOG_LINES)
        for (event in update.events) {
            if (event is EventView.RoundStarted) {
                if (event.round == 1) _history.value = emptyList()
                _recentPlays.value = emptyList()
            }
            if (event is EventView.CardPlayed) {
                _recentPlays.value = (_recentPlays.value + RecentPlay(event.player, event.card)).takeLast(RECENT_PLAYS)
            }
            if (event is EventView.RoundWon) {
                _history.value = _history.value + RoundResult(update.view.round, event.player, event.points)
            }
            recordStats(event, update.view.you)
        }
    }

    /** Counted from what the host sends, once per event. */
    private fun recordStats(event: EventView, you: Int) {
        when (event) {
            is EventView.RoundWon -> if (event.player == you) {
                prefs.updateStats { it.copy(roundsWon = it.roundsWon + 1, bestRound = maxOf(it.bestRound, event.points)) }
            }
            is EventView.GameWon -> prefs.updateStats {
                it.copy(gamesPlayed = it.gamesPlayed + 1, gamesWon = it.gamesWon + if (event.player == you) 1 else 0)
            }
            else -> Unit
        }
    }

    fun close() {
        scope.cancel()
    }

    private companion object {
        const val LOG_LINES = 3
        const val RECENT_PLAYS = 8
        const val NOTICE_MILLIS = 2_000L
    }
}
