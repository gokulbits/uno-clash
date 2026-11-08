package dev.colorclash.app.ui.room

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.colorclash.app.ui.components.Panel
import dev.colorclash.app.ui.components.SectionTitle
import dev.colorclash.app.ui.components.SheetSurface
import dev.colorclash.app.ui.components.SheetTitle
import dev.colorclash.app.ui.components.Tag
import dev.colorclash.app.ui.components.checkIcon
import dev.colorclash.app.ui.game.CardFace
import dev.colorclash.app.ui.theme.GameTheme
import dev.colorclash.protocol.GameSettings

/**
 * How to play, for exactly the rules this room is using. [actions] are the buttons pinned under it,
 * so the lobby can make "I'm ready" or "Start game" the way past it.
 */
@Composable
fun RulesDialog(settings: GameSettings, onDismiss: () -> Unit, actions: @Composable RowScope.() -> Unit) {
    val guide = remember(settings) { ruleGuide(settings) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        SheetSurface(onClose = onDismiss, footer = actions) {
            SheetTitle("How to play")
            Tag(guide.mode.uppercase(), GameTheme.highlight, style = MaterialTheme.typography.labelMedium)
            Text(guide.goal, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            if (guide.onRules.isNotEmpty()) OnRules(guide.onRules)

            Panel {
                SectionTitle("Your turn")
                guide.basics.forEachIndexed { i, item -> GuideRow(item) { StepNumber(i + 1) } }
            }
            Panel {
                SectionTitle("Power cards")
                guide.cards.forEach { CardRow(it) }
            }
            if (guide.special.isNotEmpty()) {
                Panel {
                    SectionTitle("Special rules")
                    guide.special.forEach { item -> GuideRow(item) { Tick() } }
                }
            }
            Panel {
                SectionTitle("Winning")
                guide.winning.forEach { item -> GuideRow(item) { Tick() } }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OnRules(names: List<String>) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        names.forEach { Tag(it, GameTheme.panelStrong, color = GameTheme.text, style = MaterialTheme.typography.labelMedium) }
    }
}

/** A marker on the left, then a heading and what it means. */
@Composable
private fun GuideRow(item: GuideItem, marker: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(MARKER), contentAlignment = Alignment.Center) { marker() }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.title, style = MaterialTheme.typography.titleSmall)
            Text(item.text, color = GameTheme.mutedText, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun CardRow(card: GuideCard) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        CardFace(card.card, 40.dp, elevation = 1.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(card.name, style = MaterialTheme.typography.titleSmall)
            Text(card.text, color = GameTheme.mutedText, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun StepNumber(n: Int) {
    Box(Modifier.size(MARKER).clip(CircleShape).background(GameTheme.highlight), contentAlignment = Alignment.Center) {
        Text("$n", color = GameTheme.contentOn(GameTheme.highlight), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun Tick() {
    Canvas(Modifier.size(14.dp).padding(top = 2.dp)) { checkIcon(GameTheme.highlight) }
}

private val MARKER = 24.dp
