package dev.colorclash.app.ui.settings

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A square in the source image's pixels. */
data class CropBox(val left: Int, val top: Int, val size: Int)

/**
 * How the cropper shows a photo in its square [viewport]: [zoom] is on top of the fit that just
 * covers the viewport, [panX]/[panY] move the image in viewport pixels from centred.
 */
data class CropView(val zoom: Float = 1f, val panX: Float = 0f, val panY: Float = 0f)

private fun scaleFor(width: Int, height: Int, viewport: Float, zoom: Float) = viewport / min(width, height) * zoom

/** Keeps the zoom sane and the image covering the whole viewport, so the circle never shows a gap. */
fun CropView.clamped(width: Int, height: Int, viewport: Float): CropView {
    val z = zoom.coerceIn(1f, MAX_ZOOM)
    val scale = scaleFor(width, height, viewport, z)
    val slackX = max(0f, (width * scale - viewport) / 2)
    val slackY = max(0f, (height * scale - viewport) / 2)
    return CropView(z, panX.coerceIn(-slackX, slackX), panY.coerceIn(-slackY, slackY))
}

fun cropBox(width: Int, height: Int, viewport: Float, view: CropView): CropBox {
    val v = view.clamped(width, height, viewport)
    val scale = scaleFor(width, height, viewport, v.zoom)
    val size = (viewport / scale).roundToInt().coerceIn(1, min(width, height))
    val left = (((width * scale - viewport) / 2 - v.panX) / scale).roundToInt().coerceIn(0, width - size)
    val top = (((height * scale - viewport) / 2 - v.panY) / scale).roundToInt().coerceIn(0, height - size)
    return CropBox(left, top, size)
}

const val MAX_ZOOM = 5f

/**
 * Whether a photo from another phone is worth decoding. Ours are 160px; a few KB of hostile JPEG can
 * claim to be tens of thousands of pixels a side and take this phone's whole heap to unpack.
 */
fun photoFits(width: Int, height: Int): Boolean = width in 1..MAX_PHOTO_SIDE && height in 1..MAX_PHOTO_SIDE

private const val MAX_PHOTO_SIDE = 512
