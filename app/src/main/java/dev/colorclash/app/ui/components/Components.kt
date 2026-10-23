package dev.colorclash.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.colorclash.app.ui.theme.GAME_TITLE
import dev.colorclash.app.ui.theme.GameTheme

/** The title, one little tilted card per letter, cycling through the card colors. */
@Composable
fun Logo(modifier: Modifier = Modifier, letterSize: Dp = 34.dp) {
    val words = GAME_TITLE.split(" ")
    var colorIndex = 0
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (word in words) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                word.forEachIndexed { i, letter ->
                    val color = GameTheme.cardColors[colorIndex++ % GameTheme.cardColors.size]
                    Box(
                        Modifier
                            .rotate(if (i % 2 == 0) -6f else 6f)
                            .size(letterSize, letterSize * 1.3f)
                            .shadow(6.dp, RoundedCornerShape(8.dp))
                            .clip(RoundedCornerShape(8.dp))
                            .background(color)
                            .border(2.dp, Color.White, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            letter.uppercase(),
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = (letterSize.value * 0.7f).sp,
                            fontFamily = GameTheme.displayFont,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AvatarBadge(index: Int, modifier: Modifier = Modifier, size: Dp = 48.dp, ring: Color? = null) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(GameTheme.avatarBackground(index))
            .then(if (ring != null) Modifier.border(3.dp, ring, CircleShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(GameTheme.avatar(index), fontSize = (size.value * 0.55f).sp)
    }
}

@Composable
fun BigButton(
    text: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = Color.White,
            disabledContainerColor = color.copy(alpha = 0.35f),
            disabledContentColor = Color.White.copy(alpha = 0.6f),
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp),
        contentPadding = PaddingValues(horizontal = 20.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}

/** Translucent rounded box that sits on the table background. */
@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(GameTheme.panel)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = GameTheme.mutedText)
}

/** Room code as a row of colored tiles, easy to read out loud across a table. */
@Composable
fun RoomCodeTiles(code: String, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        code.forEachIndexed { i, c ->
            Box(
                Modifier
                    .size(46.dp, 56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(GameTheme.cardColors[i % GameTheme.cardColors.size]),
                contentAlignment = Alignment.Center,
            ) {
                Text(c.toString(), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}
