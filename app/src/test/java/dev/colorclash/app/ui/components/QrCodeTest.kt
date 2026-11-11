package dev.colorclash.app.ui.components

import com.google.zxing.BinaryBitmap
import com.google.zxing.LuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import dev.colorclash.protocol.JoinLink
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The mark in the middle of the lobby QR covers real modules, so the thing that matters is that a
 * scanner still reads it. These render the code the way the screen does — logo hole and all — and
 * put it through ZXing's own decoder.
 */
class QrCodeTest {
    private val longest = JoinLink(
        hosts = List(8) { "192.168.${it + 10}.${it + 100}" },
        port = 47474,
        roomCode = "ZXCVB",
    ).toUri()

    @Test
    fun `a join link still decodes with the logo over the middle`() {
        assertEquals(longest, decode(render(longest)))
    }

    @Test
    fun `the shortest link decodes too`() {
        val short = JoinLink(hosts = listOf("10.0.0.2"), port = 1234, roomCode = "ABCD").toUri()
        assertEquals(short, decode(render(short)))
    }

    @Test
    fun `the logo only covers modules the error correction can spare`() {
        val matrix = qrMatrix(longest)
        val covered = QR_LOGO_FRACTION * 1.2f // the paper around the card is what actually hides modules
        val hidden = (covered * covered).toDouble()
        // Level H rebuilds about 30% of the code; anything near that and scans start failing.
        assertTrue(hidden < 0.1, "The mark hides ${(hidden * 100).roundToInt()}% of the code")
        assertTrue(matrix.width >= 21, "Not a real QR matrix")
    }

    /** The code as the screen draws it: dark modules, a quiet zone, and paper punched out of the middle. */
    private fun render(text: String, scale: Int = 6, quiet: Int = 4): Array<BooleanArray> {
        val matrix = qrMatrix(text)
        val side = (matrix.width + quiet * 2) * scale
        val dark = Array(side) { BooleanArray(side) }
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                if (!matrix[x, y]) continue
                for (dy in 0 until scale) {
                    for (dx in 0 until scale) {
                        dark[(y + quiet) * scale + dy][(x + quiet) * scale + dx] = true
                    }
                }
            }
        }
        val hole = (matrix.width * scale * QR_LOGO_FRACTION * 1.2f).roundToInt()
        val from = (side - hole) / 2
        for (y in from until from + hole) {
            for (x in from until from + hole) dark[y][x] = false
        }
        return dark
    }

    private fun decode(dark: Array<BooleanArray>): String {
        val source = object : LuminanceSource(dark.size, dark.size) {
            override fun getRow(y: Int, row: ByteArray?): ByteArray {
                val out = row?.takeIf { it.size >= width } ?: ByteArray(width)
                for (x in 0 until width) out[x] = if (dark[y][x]) 0 else 255.toByte()
                return out
            }

            override fun getMatrix(): ByteArray {
                val out = ByteArray(width * height)
                for (y in 0 until height) getRow(y, ByteArray(width)).copyInto(out, y * width)
                return out
            }
        }
        return QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source))).text
    }
}
