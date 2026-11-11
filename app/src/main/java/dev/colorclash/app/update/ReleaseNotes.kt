package dev.colorclash.app.update

/**
 * Release notes are GitHub markdown. This reads the parts that show up in our notes (headings,
 * bullets, numbered lists, bold, italic, code and links) without pulling in a markdown library.
 * Links keep their text only: the notes come off the network, so nothing in them gets to open a page.
 */
sealed interface NoteBlock {
    data class Heading(val level: Int, val text: List<NoteSpan>) : NoteBlock
    data class Bullet(val depth: Int, val text: List<NoteSpan>) : NoteBlock
    data class Numbered(val number: String, val depth: Int, val text: List<NoteSpan>) : NoteBlock
    data class Paragraph(val text: List<NoteSpan>) : NoteBlock
    data class Code(val text: String) : NoteBlock
}

data class NoteSpan(val text: String, val bold: Boolean = false, val italic: Boolean = false, val code: Boolean = false)

/** Plenty for a dialog; a release that writes an essay gets cut off with a pointer to the rest. */
const val MAX_NOTE_BLOCKS = 40
private const val MAX_NOTE_CHARS = 8_000

private val HEADING = Regex("""^(#{1,6})\s+(.*?)\s*#*\s*$""")
private val BULLET = Regex("""^(\s*)[-*+]\s+(.*)$""")
private val NUMBERED = Regex("""^(\s*)(\d{1,3})[.)]\s+(.*)$""")
private val RULE = Regex("""^\s*([-*_])(\s*\1){2,}\s*$""")
private val HTML_COMMENT = Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL)

fun parseReleaseNotes(body: String): List<NoteBlock> {
    val lines = body.take(MAX_NOTE_CHARS).replace(HTML_COMMENT, "").replace("\r\n", "\n").lines()
    val blocks = mutableListOf<NoteBlock>()
    // The block being built. Notes come hard-wrapped, so a plain line carries on whatever is open:
    // a bullet keeps its wrapped lines instead of shedding them as paragraphs of their own.
    val open = StringBuilder()
    var wrap: ((List<NoteSpan>) -> NoteBlock)? = null
    fun endBlock() {
        val text = open.toString().trim()
        val make = wrap
        open.clear()
        wrap = null
        if (text.isBlank()) return
        blocks += if (make == null) NoteBlock.Paragraph(parseInline(text)) else make(parseInline(withoutCredit(text)))
    }
    fun startItem(item: String, make: (List<NoteSpan>) -> NoteBlock) {
        endBlock()
        wrap = make
        open.append(item)
    }

    var i = 0
    while (i < lines.size) {
        val raw = lines[i++]
        val line = raw.trimEnd()
        if (line.trimStart().startsWith("```")) {
            endBlock()
            val code = mutableListOf<String>()
            while (i < lines.size && !lines[i].trimStart().startsWith("```")) code += lines[i++].trimEnd()
            i++ // the closing fence
            if (code.any { it.isNotBlank() }) blocks += NoteBlock.Code(code.joinToString("\n").trimEnd())
            continue
        }
        // A quote reads as plain text here.
        val text = line.trimStart().let { if (it.startsWith(">")) it.removePrefix(">").trimStart() else line }
        when {
            text.isBlank() || RULE.matches(text) -> endBlock()
            // GitHub's generated footer; the dialog has no use for a compare link.
            text.trimStart().startsWith("**Full Changelog**") -> endBlock()
            HEADING.matches(text.trimStart()) -> {
                endBlock()
                val (hashes, title) = HEADING.find(text.trimStart())!!.destructured
                blocks += NoteBlock.Heading(hashes.length, parseInline(title))
            }
            BULLET.matches(text) -> {
                val (indent, item) = BULLET.find(text)!!.destructured
                val depth = depthOf(indent)
                startItem(item) { NoteBlock.Bullet(depth, it) }
            }
            NUMBERED.matches(text) -> {
                val (indent, number, item) = NUMBERED.find(text)!!.destructured
                val depth = depthOf(indent)
                startItem(item) { NoteBlock.Numbered(number, depth, it) }
            }
            else -> {
                if (open.isNotEmpty()) open.append(' ')
                open.append(text.trim())
            }
        }
    }
    endBlock()

    // A heading with nothing under it (say, a generated "What's Changed" over no pull requests) says
    // nothing. One straight over a smaller heading is a section with subsections, so it stays.
    return blocks.filterIndexed { index, block ->
        if (block !is NoteBlock.Heading) return@filterIndexed true
        when (val next = blocks.getOrNull(index + 1)) {
            null -> false
            is NoteBlock.Heading -> next.level > block.level
            else -> true
        }
    }
}

private fun depthOf(indent: String) = (indent.replace("\t", "  ").length / 2).coerceAtMost(2)

/** "Fix a thing by @someone in https://github.com/.../pull/7", as GitHub generates them, loses the credit. */
private fun withoutCredit(item: String) = item.replace(Regex("""\s+by @[\w-]+ in https?://\S+$"""), "")

/** Bold, italic, `code` and links (their text only), with backslash escapes. */
fun parseInline(text: String): List<NoteSpan> {
    val spans = mutableListOf<NoteSpan>()
    val buffer = StringBuilder()
    var bold = false
    var italic = false
    fun flush() {
        if (buffer.isEmpty()) return
        val last = spans.lastOrNull()
        spans += if (last != null && last.bold == bold && last.italic == italic && !last.code) {
            spans.removeAt(spans.lastIndex)
            last.copy(text = last.text + buffer)
        } else {
            NoteSpan(buffer.toString(), bold, italic)
        }
        buffer.clear()
    }

    var i = 0
    while (i < text.length) {
        val c = text[i]
        when {
            c == '\\' && i + 1 < text.length && text[i + 1] in ESCAPABLE -> {
                buffer.append(text[i + 1])
                i += 2
            }
            c == '`' && text.indexOf('`', i + 1) > i -> {
                val end = text.indexOf('`', i + 1)
                flush()
                spans += NoteSpan(text.substring(i + 1, end), bold, italic, code = true)
                i = end + 1
            }
            (text.startsWith("**", i) || text.startsWith("__", i)) && (bold || text.indexOf(text.substring(i, i + 2), i + 2) > i + 2) -> {
                flush()
                bold = !bold
                i += 2
            }
            c == '*' && (italic || (i + 1 < text.length && !text[i + 1].isWhitespace() && text.indexOf('*', i + 1) > i + 1)) -> {
                flush()
                italic = !italic
                i++
            }
            (c == '[' || (c == '!' && text.startsWith("[", i + 1))) && linkEnd(text, i) != null -> {
                val start = if (c == '!') i + 1 else i
                val (labelEnd, end) = linkEnd(text, i)!!
                flush()
                // Inherits the bold or italic around it; a label can carry its own too.
                parseInline(text.substring(start + 1, labelEnd)).forEach { span ->
                    spans += span.copy(bold = span.bold || bold, italic = span.italic || italic)
                }
                i = end + 1
            }
            else -> {
                buffer.append(c)
                i++
            }
        }
    }
    flush()
    return spans
}

/** For "[label](url)" starting at [at]: where the label ends and where the closing bracket is. */
private fun linkEnd(text: String, at: Int): Pair<Int, Int>? {
    val open = if (text[at] == '!') at + 1 else at
    val labelEnd = text.indexOf("](", open + 1).takeIf { it > open } ?: return null
    val end = text.indexOf(')', labelEnd + 2).takeIf { it > labelEnd } ?: return null
    return labelEnd to end
}

private const val ESCAPABLE = "\\`*_[]()#+-.!>"

/** The notes as plain text, for screen readers and tests. */
fun List<NoteSpan>.plain(): String = joinToString("") { it.text }
