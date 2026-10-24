package dev.colorclash.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.colorclash.app.ui.theme.GameTheme
import dev.colorclash.protocol.AUTO_AVATAR_COLOR
import dev.colorclash.protocol.SeatInfo
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Photos of the players in the current room, by [SeatInfo.photoId]. */
val LocalSeatPhotos = compositionLocalOf<Map<String, ImageBitmap>> { emptyMap() }

/** Their photo, once it arrives, wins over the emoji. */
@Composable
fun AvatarBadge(seat: SeatInfo, modifier: Modifier = Modifier, size: Dp = 48.dp, ring: Color? = null) {
    val photo = seat.photoId?.let { LocalSeatPhotos.current[it] }
    AvatarBadge(seat.avatar, modifier, size, ring, seat.avatarColor, seat.avatarFrame, photo)
}

/** [ring] is a plain highlight (whose turn, what's selected) just inside the frame. */
@Composable
fun AvatarBadge(
    index: Int,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    ring: Color? = null,
    color: Int = AUTO_AVATAR_COLOR,
    frame: Int = 0,
    photo: ImageBitmap? = null,
) {
    val style = GameTheme.avatarFrame(frame)
    // A frame takes the outer tenth all round; the face shrinks to make room.
    val face = if (style == 0) size else size * FACE_SHARE
    val glyphSize = with(LocalDensity.current) { (face * 0.55f).toSp() }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(face)
                .clip(CircleShape)
                .background(GameTheme.avatarBackground(index, color)),
            contentAlignment = Alignment.Center,
        ) {
            if (photo != null) {
                Image(photo, contentDescription = null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Text(GameTheme.avatar(index).glyph, fontSize = glyphSize)
            }
            if (ring != null) Box(Modifier.fillMaxSize().border(3.dp, ring, CircleShape))
        }
        if (style != 0) Canvas(Modifier.fillMaxSize()) { drawFrame(style) }
    }
}

private fun DrawScope.drawFrame(style: Int) {
    val s = size.minDimension
    val r = s * 0.445f
    when (style) {
        GOLD -> metal(GameTheme.frameGold, GameTheme.frameGoldDeep)
        SILVER -> metal(GameTheme.frameSilver, GameTheme.frameSilverDeep)
        RAINBOW -> {
            val colors = GameTheme.cardColors + GameTheme.cardColors.first()
            drawCircle(Brush.sweepGradient(colors, center), r, style = Stroke(s * 0.09f))
        }
        NEON -> {
            drawCircle(GameTheme.frameNeon.copy(alpha = 0.22f), s * 0.455f, style = Stroke(s * 0.09f))
            drawCircle(GameTheme.frameNeon, s * 0.44f, style = Stroke(s * 0.04f))
            drawCircle(Color.White.copy(alpha = 0.85f), s * 0.44f, style = Stroke(s * 0.012f))
        }
        SPARKLE -> {
            drawCircle(Color.White, s * 0.44f, style = Stroke(s * 0.035f))
            for (k in 0 until 4) {
                val a = PI / 4 + k * PI / 2
                val at = center + Offset((s * 0.44f * cos(a)).toFloat(), (s * 0.44f * sin(a)).toFloat())
                twinkle(at, s * 0.085f, Color.White)
                drawCircle(GameTheme.frameGold, s * 0.018f, at)
            }
        }
        FLAMES -> {
            val flames = Brush.radialGradient(0.78f to GameTheme.frameFlame, 1f to GameTheme.frameFlameTip, center = center, radius = s * 0.5f)
            val spikes = 12
            val path = Path()
            for (k in 0 until spikes) {
                val a = 2 * PI * k / spikes
                val half = PI / spikes * 0.8
                fun at(angle: Double, radius: Float) = center + Offset((radius * cos(angle)).toFloat(), (radius * sin(angle)).toFloat())
                val left = at(a - half, s * 0.41f)
                val tip = at(a + half * 0.4, s * 0.5f)
                val right = at(a + half, s * 0.41f)
                path.moveTo(left.x, left.y)
                path.lineTo(tip.x, tip.y)
                path.lineTo(right.x, right.y)
                path.close()
            }
            drawPath(path, flames)
            drawCircle(GameTheme.frameFlame, s * 0.43f, style = Stroke(s * 0.05f))
        }
    }
}

private fun DrawScope.metal(light: Color, deep: Color) {
    val s = size.minDimension
    drawCircle(Brush.sweepGradient(listOf(light, deep, light, deep, light), center), s * 0.445f, style = Stroke(s * 0.09f))
    drawCircle(deep, s * 0.402f, style = Stroke(s * 0.012f))
}

private fun DrawScope.twinkle(at: Offset, r: Float, color: Color) {
    val path = Path().apply {
        moveTo(at.x, at.y - r)
        quadraticTo(at.x, at.y, at.x + r, at.y)
        quadraticTo(at.x, at.y, at.x, at.y + r)
        quadraticTo(at.x, at.y, at.x - r, at.y)
        quadraticTo(at.x, at.y, at.x, at.y - r)
        close()
    }
    drawPath(path, color)
}

private const val FACE_SHARE = 0.8f
private const val GOLD = 1
private const val SILVER = 2
private const val RAINBOW = 3
private const val NEON = 4
private const val SPARKLE = 5
private const val FLAMES = 6
