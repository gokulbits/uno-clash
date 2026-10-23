package dev.colorclash.app.ui.room

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.colorclash.app.session.Session
import dev.colorclash.app.ui.components.AvatarBadge
import dev.colorclash.app.ui.components.BigButton
import dev.colorclash.app.ui.components.Panel
import dev.colorclash.app.ui.theme.GameTheme
import dev.colorclash.protocol.PhaseKind
import dev.colorclash.protocol.PlayerView
import dev.colorclash.rules.Card
import dev.colorclash.rules.Face

/** Stand-in until the real game screen lands in Phase 4: shows the table so the connection can be checked end to end. */
@Composable
fun GamePlaceholder(view: PlayerView, session: Session, onLeave: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onLeave) { Text("‹ Leave", color = GameTheme.text) }
            Text("Round ${view.round}", style = MaterialTheme.typography.headlineMedium)
        }
        Panel {
            view.seats.forEach { seat ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AvatarBadge(seat.info.avatar, size = 36.dp, ring = if (seat.info.seat == view.current) GameTheme.yellow else null)
                    Text(seat.info.name, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Text("${seat.cardCount} cards · ${seat.score} pts", color = GameTheme.mutedText)
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Top card", color = GameTheme.mutedText)
            MiniCard(view.topCard)
        }
        Text(
            when {
                view.phase == PhaseKind.GAME_OVER -> "${view.seats[view.winner ?: 0].info.name} wins the game!"
                view.phase == PhaseKind.ROUND_OVER -> "${view.seats[view.winner ?: 0].info.name} won the round"
                view.isYourTurn -> "Your turn"
                else -> "${view.seats[view.current].info.name}'s turn"
            },
            style = MaterialTheme.typography.titleLarge,
        )
        Text("Your hand", color = GameTheme.mutedText)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            view.hand.forEach { MiniCard(it) }
        }
        Text("The full game table arrives in the next phase.", color = GameTheme.mutedText)
        if (session.isHost && view.phase == PhaseKind.ROUND_OVER) {
            BigButton("Next round", GameTheme.green, { session.hosting?.host?.startNextRound() })
        }
    }
}

@Composable
private fun MiniCard(card: Card) {
    Box(
        Modifier
            .size(40.dp, 56.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(GameTheme.cardColor(card.color)),
        contentAlignment = Alignment.Center,
    ) {
        Text(card.face.label(), color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
    }
}

private fun Face.label(): String = number?.toString() ?: when (this) {
    Face.SKIP -> "⊘"
    Face.REVERSE -> "⇄"
    Face.DRAW_TWO -> "+2"
    Face.WILD -> "W"
    Face.WILD_DRAW_FOUR -> "+4"
    else -> "?"
}
