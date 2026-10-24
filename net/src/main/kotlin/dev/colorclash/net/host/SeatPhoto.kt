package dev.colorclash.net.host

import dev.colorclash.protocol.MAX_PHOTO_CHARS
import java.security.MessageDigest
import java.util.Base64

/** A player's photo and the short id seats refer to it by. */
internal class SeatPhoto(val id: String, val data: String)

/** The photo if it's a JPEG of a sensible size, otherwise null: a bad photo just means no photo. */
internal fun checkedPhoto(data: String?): SeatPhoto? {
    if (data.isNullOrEmpty() || data.length > MAX_PHOTO_CHARS) return null
    val bytes = runCatching { Base64.getDecoder().decode(data) }.getOrNull() ?: return null
    val jpeg = bytes.size > 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()
    if (!jpeg) return null
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
    return SeatPhoto(digest.take(8).joinToString("") { "%02x".format(it) }, data)
}
