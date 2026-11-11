package dev.colorclash.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import dev.colorclash.app.ui.theme.GameTheme

/**
 * The lobby QR. Encoded at the highest error correction the format has, which is what pays for the
 * mark sitting over the middle of it: a scanner rebuilds the modules underneath from the rest.
 */
fun qrMatrix(text: String): BitMatrix = QRCodeWriter().encode(
    text,
    BarcodeFormat.QR_CODE,
    0,
    0,
    mapOf(EncodeHintType.MARGIN to 0, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H),
)

/**
 * How much of the code's width the mark covers. Level H can rebuild up to 30% of the modules, and
 * this is a fifth of the width — a twenty-fifth of the area — so it stays well inside that even
 * once the paper around it is counted. Raise it and codes start failing to scan.
 */
const val QR_LOGO_FRACTION = 0.2f

/** Draws [text] as a QR code straight onto a canvas, no bitmap involved. */
@Composable
fun QrCode(text: String, modifier: Modifier = Modifier) {
    val matrix = remember(text) { qrMatrix(text) }
    Spacer(
        modifier
            .aspectRatio(1f)
            .clip(GameTheme.tileShape)
            .background(GameTheme.qrPaper)
            .padding(12.dp)
            // One path, made once for the size: this used to be a draw call per module, up to a few thousand.
            .drawWithCache {
                val cell = size.minDimension / matrix.width
                val modules = Path()
                for (y in 0 until matrix.height) {
                    for (x in 0 until matrix.width) {
                        if (matrix[x, y]) modules.addRect(Rect(Offset(x * cell, y * cell), Size(cell, cell)))
                    }
                }
                onDrawBehind {
                    drawPath(modules, GameTheme.qrInk)
                    logo(cell * matrix.width)
                }
            },
    )
}

/** A card sitting on the middle of the code, with the four colors on it. */
private fun DrawScope.logo(side: Float) {
    val card = side * QR_LOGO_FRACTION
    // Paper around the card, so no module is left half covered.
    val paper = card * 1.2f
    val corner = CornerRadius(card * 0.22f)
    drawRoundRect(
        GameTheme.qrPaper,
        Offset(center.x - paper / 2, center.y - paper / 2),
        Size(paper, paper),
        CornerRadius(paper * 0.22f),
    )
    val at = Offset(center.x - card / 2, center.y - card / 2)
    drawRoundRect(GameTheme.qrPaper, at, Size(card, card), corner)
    drawRoundRect(GameTheme.qrInk, at, Size(card, card), corner, style = Stroke(card * 0.08f))
    val r = card * 0.17f
    val near = card * 0.31f
    val far = card * 0.69f
    drawCircle(GameTheme.red, r, Offset(at.x + near, at.y + near))
    drawCircle(GameTheme.yellow, r, Offset(at.x + far, at.y + near))
    drawCircle(GameTheme.blue, r, Offset(at.x + near, at.y + far))
    drawCircle(GameTheme.green, r, Offset(at.x + far, at.y + far))
}
