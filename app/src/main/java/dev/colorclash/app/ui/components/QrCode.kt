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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import dev.colorclash.app.ui.theme.GameTheme

/** Draws [text] as a QR code straight onto a canvas, no bitmap involved. */
@Composable
fun QrCode(text: String, modifier: Modifier = Modifier) {
    val matrix = remember(text) {
        QRCodeWriter().encode(
            text, BarcodeFormat.QR_CODE, 0, 0,
            mapOf(EncodeHintType.MARGIN to 0, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M),
        )
    }
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
                onDrawBehind { drawPath(modules, GameTheme.qrInk) }
            },
    )
}
