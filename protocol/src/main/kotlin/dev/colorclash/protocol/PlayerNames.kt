package dev.colorclash.protocol

const val MAX_NAME_CHARS = 16

/** Cuts to [max] without leaving half an emoji behind, which would draw as a box on every phone. */
fun String.clipped(max: Int = MAX_NAME_CHARS): String {
    if (length <= max) return this
    return substring(0, if (this[max - 1].isHighSurrogate()) max - 1 else max)
}

/**
 * What the host will show as a name, blank if nothing's left. Control characters go, and so do the
 * direction overrides that would flip how the rest of someone's row reads. Joiners stay: emoji
 * like the pirate flag are built with them.
 */
fun cleanPlayerName(raw: String): String =
    raw.filterNot { it.isISOControl() || it in DIRECTION_OVERRIDES }.trim().clipped()

private val DIRECTION_OVERRIDES = setOf(
    '‎', '‏', '‪', '‫', '‬', '‭', '‮', '⁦', '⁧', '⁨', '⁩',
)
