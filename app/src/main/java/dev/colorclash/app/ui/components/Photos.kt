package dev.colorclash.app.ui.components

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Base64

fun decodePhoto(data: String): ImageBitmap? = runCatching {
    val bytes = Base64.getDecoder().decode(data)
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
}.getOrNull()

@Composable
fun rememberPhoto(data: String?): ImageBitmap? = remember(data) { data?.let(::decodePhoto) }

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
