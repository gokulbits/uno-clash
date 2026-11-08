package dev.colorclash.app.ui.update

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.colorclash.app.ui.components.HintText
import dev.colorclash.app.ui.components.Panel
import dev.colorclash.app.ui.components.SectionTitle
import dev.colorclash.app.ui.theme.GameTheme
import dev.colorclash.app.update.MAX_NOTE_BLOCKS
import dev.colorclash.app.update.NoteBlock
import dev.colorclash.app.update.NoteSpan
import dev.colorclash.app.update.plain

/** A release's markdown notes, laid out like the rest of the game's sheets. */
@Composable
fun ReleaseNotes(blocks: List<NoteBlock>, modifier: Modifier = Modifier) {
    // Notes that open with their own "What's new" heading get it as the panel's title, not twice.
    val lead = (blocks.firstOrNull() as? NoteBlock.Heading)?.takeIf { it.level <= 2 }
    val shown = if (lead != null) blocks.drop(1) else blocks
    Panel(modifier) {
        SectionTitle(lead?.text?.plain() ?: "What's new")
        shown.take(MAX_NOTE_BLOCKS).forEachIndexed { i, block ->
            // Headings after the first get a little air above them, so sections read as sections.
            val top = if (block is NoteBlock.Heading && i > 0) Modifier.padding(top = 6.dp) else Modifier
            when (block) {
                is NoteBlock.Heading -> Text(
                    styled(block.text),
                    top,
                    style = if (block.level <= 2) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
                )
                is NoteBlock.Bullet -> ListItem(block.depth, { Dot() }, block.text)
                is NoteBlock.Numbered -> ListItem(block.depth, {
                    Text("${block.number}.", color = GameTheme.highlight, style = MaterialTheme.typography.labelMedium)
                }, block.text)
                is NoteBlock.Paragraph -> Text(styled(block.text), color = GameTheme.mutedText, style = body())
                is NoteBlock.Code -> Text(
                    block.text,
                    Modifier
                        .fillMaxWidth()
                        .clip(GameTheme.tagShape)
                        .background(GameTheme.panelStrong)
                        .padding(8.dp),
                    style = body().copy(fontFamily = FontFamily.Monospace),
                )
            }
        }
        if (shown.size > MAX_NOTE_BLOCKS) HintText("More in the full notes on GitHub.")
    }
}

@Composable
private fun ListItem(depth: Int, marker: @Composable () -> Unit, text: List<NoteSpan>) {
    Row(Modifier.padding(start = 16.dp * depth), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // As tall as the first line of text, so the marker sits beside it however the item wraps.
        Box(Modifier.width(MARKER_WIDTH).padding(top = 1.dp), contentAlignment = Alignment.TopCenter) { marker() }
        Text(styled(text), Modifier.weight(1f), color = GameTheme.mutedText, style = body())
    }
}

@Composable
private fun Dot() {
    Box(Modifier.padding(top = 6.dp).size(6.dp).clip(CircleShape).background(GameTheme.highlight))
}

@Composable
private fun body(): TextStyle = MaterialTheme.typography.bodySmall

/** Bold reads in full white against the muted body text, so it stands out the way it does on GitHub. */
@Composable
private fun styled(spans: List<NoteSpan>): AnnotatedString {
    val codeFill = GameTheme.panelStrong
    return buildAnnotatedString {
        spans.forEach { span ->
            val style = SpanStyle(
                fontWeight = if (span.bold) FontWeight.Bold else null,
                fontStyle = if (span.italic) FontStyle.Italic else null,
                color = if (span.bold || span.code) GameTheme.text else Color.Unspecified,
                fontFamily = if (span.code) FontFamily.Monospace else null,
                background = if (span.code) codeFill else Color.Unspecified,
            )
            withStyle(style) { append(span.text) }
        }
    }
}

private val MARKER_WIDTH = 18.dp
