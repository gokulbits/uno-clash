package dev.colorclash.app.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.colorclash.app.ui.components.AvatarBadge
import dev.colorclash.app.ui.components.BigButton
import dev.colorclash.app.ui.theme.GameTheme
import dev.colorclash.protocol.PhaseKind
import dev.colorclash.protocol.PlayerView
import dev.colorclash.rules.Card
import dev.colorclash.rules.CardColor

/** Dims the table and holds a choice the player has to make. */
@Composable
private fun Overlay(content: @Composable ColumnScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            // Swallow taps so nothing underneath gets played by accident.
            .clickable(enabled = true, onClick = {}),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(GameTheme.tableTop)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}

@Composable
fun ColorPicker(hand: List<Card>, onPick: (CardColor) -> Unit) {
    val counts = hand.mapNotNull { it.color }.groupingBy { it }.eachCount()
    Overlay {
        Text("Pick a color", style = MaterialTheme.typography.headlineMedium)
        CardColor.entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                row.forEach { color ->
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1.2f)
                            .clip(RoundedCornerShape(22.dp))
                            .background(GameTheme.cardColor(color))
                            .clickable { onPick(color) },
                        contentAlignment = Alignment.Center,
                    ) {
                        ColorLabel(color, counts[color] ?: 0)
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxScope.ColorLabel(color: CardColor, count: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(color.name.lowercase().replaceFirstChar { it.uppercase() }, color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp)
        Text(if (count == 1) "1 card" else "$count cards", color = Color.White.copy(alpha = 0.85f))
    }
}

@Composable
fun SwapPicker(view: PlayerView, onPick: (Int) -> Unit) {
    Overlay {
        Text("Swap hands with…", style = MaterialTheme.typography.headlineMedium)
        opponentOrder(view).forEach { seat ->
            val info = view.seats[seat]
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(GameTheme.panelStrong)
                    .clickable { onPick(seat) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AvatarBadge(info.info.avatar, size = 40.dp)
                Text(info.info.name, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                Text(if (info.cardCount == 1) "1 card" else "${info.cardCount} cards", color = GameTheme.yellow, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Shown between rounds and at the end. The host moves things along; everyone else waits. */
@Composable
fun ScoreBoard(
    view: PlayerView,
    headline: String,
    isHost: Boolean,
    onNextRound: () -> Unit,
    onBackToLobby: () -> Unit,
    onLeave: () -> Unit,
) {
    val gameOver = view.phase == PhaseKind.GAME_OVER
    Overlay {
        Text(if (gameOver) "🏆" else "🎉", fontSize = 48.sp)
        Text(headline, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        view.roundPoints?.let { Text("+$it points", color = GameTheme.yellow, fontWeight = FontWeight.Black) }

        view.seats.sortedByDescending { it.score }.forEachIndexed { rank, seat ->
            val isWinner = seat.info.seat == view.winner
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isWinner) GameTheme.cardColors[rank % 4].copy(alpha = 0.8f) else GameTheme.panel)
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("${rank + 1}", fontWeight = FontWeight.Black, color = GameTheme.mutedText)
                AvatarBadge(seat.info.avatar, size = 34.dp)
                Text(seat.info.name + if (seat.info.seat == view.you) " (you)" else "", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                Text("${seat.score}", fontWeight = FontWeight.Black)
            }
        }
        Text("Playing to ${view.settings.rules.targetScore}", color = GameTheme.mutedText)

        when {
            isHost && gameOver -> {
                BigButton("Back to lobby", GameTheme.green, onBackToLobby)
                BigButton("Close room", GameTheme.red, onLeave)
            }
            isHost -> BigButton("Next round", GameTheme.green, onNextRound)
            gameOver -> BigButton("Leave", GameTheme.blue, onLeave)
            else -> Text("Waiting for the host to start the next round…", color = GameTheme.mutedText, textAlign = TextAlign.Center)
        }
    }
}
