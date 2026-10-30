package dev.colorclash.app.ui.components

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import dev.colorclash.app.ui.settings.photoFits
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Base64

/** The size is read from the header first: these come off the network, and the host only checks for a JPEG. */
fun decodePhoto(data: String): ImageBitmap? = runCatching {
    val bytes = Base64.getDecoder().decode(data)
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (!photoFits(bounds.outWidth, bounds.outHeight)) return null
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
}.getOrNull()

/** Decoded off the main thread; null until it's ready. */
@Composable
fun rememberPhoto(data: String?): ImageBitmap? {
    val decoded by produceState<ImageBitmap?>(null, data) {
        value = data?.let { withContext(Dispatchers.Default) { decodePhoto(it) } }
    }
    return decoded
}

/**
 * Each photo is decoded once, off the main thread, and only the ones still at the table are kept.
 * A 160px ARGB bitmap is about 100 KB and the cache this replaces never dropped one, nor did it
 * decode anywhere but inside composition.
 */
@Composable
fun rememberSeatPhotos(photos: Map<String, String>): Map<String, ImageBitmap> {
    var decoded by remember { mutableStateOf(emptyMap<String, ImageBitmap>()) }
    LaunchedEffect(photos) {
        val known = decoded
        decoded = withContext(Dispatchers.Default) {
            photos.mapNotNull { (id, data) -> (known[id] ?: decodePhoto(data))?.let { id to it } }.toMap()
        }
    }
    return decoded
}
