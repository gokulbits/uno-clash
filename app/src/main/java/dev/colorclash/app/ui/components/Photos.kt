package dev.colorclash.app.ui.components

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.util.Base64

fun decodePhoto(data: String): ImageBitmap? = runCatching {
    val bytes = Base64.getDecoder().decode(data)
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
}.getOrNull()

@Composable
fun rememberPhoto(data: String?): ImageBitmap? = remember(data) { data?.let(::decodePhoto) }

/** Each photo is decoded once, however often the map grows. */
@Composable
fun rememberSeatPhotos(photos: Map<String, String>): Map<String, ImageBitmap> {
    val decoded = remember { HashMap<String, ImageBitmap>() }
    return remember(photos) {
        photos.forEach { (id, data) -> if (id !in decoded) decodePhoto(data)?.let { decoded[id] = it } }
        decoded.toMap()
    }
}
