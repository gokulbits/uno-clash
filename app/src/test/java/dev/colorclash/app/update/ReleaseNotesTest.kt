package dev.colorclash.app.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ReleaseNotesTest {

    @Test
    fun `headings, bullets, numbers and paragraphs come out as blocks`() {
        val blocks = parseReleaseNotes(
            """
            ## What's new

            ### Rules popup
            - Shows the **room's** rules
              - nested *detail*
            1. First
            2) Second

            Some words
            that wrap.

            ---
            **Full Changelog**: https://github.com/x/y/compare/v1...v2
            """.trimIndent(),
        )
        assertEquals(
            listOf(
                NoteBlock.Heading(2, listOf(NoteSpan("What's new"))),
                NoteBlock.Heading(3, listOf(NoteSpan("Rules popup"))),
                NoteBlock.Bullet(0, listOf(NoteSpan("Shows the "), NoteSpan("room's", bold = true), NoteSpan(" rules"))),
                NoteBlock.Bullet(1, listOf(NoteSpan("nested "), NoteSpan("detail", italic = true))),
                NoteBlock.Numbered("1", 0, listOf(NoteSpan("First"))),
                NoteBlock.Numbered("2", 0, listOf(NoteSpan("Second"))),
                NoteBlock.Paragraph(listOf(NoteSpan("Some words that wrap."))),
            ),
            blocks,
        )
    }

    @Test
    fun `github's generated notes lose the credits and the compare link`() {
        val blocks = parseReleaseNotes(
            "## What's Changed\r\n* Faaa bubble by @gokulbits in https://github.com/x/y/pull/7\r\n\r\n" +
                "**Full Changelog**: https://github.com/x/y/compare/v1.0.1...v1.0.2",
        )
        assertEquals(listOf(NoteBlock.Heading(2, listOf(NoteSpan("What's Changed"))), NoteBlock.Bullet(0, listOf(NoteSpan("Faaa bubble")))), blocks)
        // Nothing but the footer: nothing to show, not an empty heading.
        assertTrue(parseReleaseNotes("## What's Changed\n\n**Full Changelog**: https://x").isEmpty())
    }

    @Test
    fun `inline code, links and escapes`() {
        assertEquals(
            listOf(NoteSpan("Run "), NoteSpan("gradlew test", code = true), NoteSpan(" and see "), NoteSpan("the docs", bold = true), NoteSpan(" *now*")),
            parseInline("Run `gradlew test` and see **[the docs](https://example.com)** \\*now\\*"),
        )
        assertEquals("alt text", parseInline("![alt text](https://x/y.png)").plain())
        // Lone markers stay as they are.
        assertEquals("5 * 3 and a ** b", parseInline("5 * 3 and a ** b").plain())
        assertEquals("snake_case_name", parseInline("snake_case_name").plain())
    }

    @Test
    fun `fenced code is kept as written`() {
        val blocks = parseReleaseNotes("Before\n```\nval x = 1\n  indented\n```\nAfter")
        assertIs<NoteBlock.Code>(blocks[1])
        assertEquals("val x = 1\n  indented", (blocks[1] as NoteBlock.Code).text)
        assertEquals(3, blocks.size)
    }

    @Test
    fun `html comments and quotes are tidied away`() {
        val blocks = parseReleaseNotes("<!-- hidden -->\n> Quoted **bit**")
        assertEquals(listOf(NoteBlock.Paragraph(listOf(NoteSpan("Quoted "), NoteSpan("bit", bold = true)))), blocks)
    }

    @Test
    fun `a bullet wrapped over several lines stays one bullet`() {
        val blocks = parseReleaseNotes(
            """
            ## UNO Flip

            - **112 double-sided cards.** The light side plays in red, yellow,
              green and blue;
            the dark side in pink, teal, orange and purple.
            - Flip scores on its own ladder.

            A paragraph of its own,
            wrapped as well.
            """.trimIndent(),
        )
        assertEquals(
            listOf("UNO Flip", "112 double-sided cards. The light side plays in red, yellow, green and blue; the dark side in pink, teal, orange and purple.", "Flip scores on its own ladder.", "A paragraph of its own, wrapped as well."),
            blocks.map { block ->
                when (block) {
                    is NoteBlock.Heading -> block.text.plain()
                    is NoteBlock.Bullet -> block.text.plain()
                    is NoteBlock.Paragraph -> block.text.plain()
                    else -> error("unexpected $block")
                }
            },
        )
        assertEquals(1, (blocks[1] as NoteBlock.Bullet).text.count { it.bold })
    }

    @Test
    fun `a wrapped numbered item keeps its continuation and loses the credit`() {
        val blocks = parseReleaseNotes("1. Fix the thing\n   properly by @someone in https://github.com/x/y/pull/7")
        assertEquals(listOf(NoteBlock.Numbered("1", 0, listOf(NoteSpan("Fix the thing properly")))), blocks)
    }

    @Test
    fun `the 1_0_9 notes read the way they were written`() {
        val blocks = parseReleaseNotes(
            """
            ## What's new

            ### Stacked draws are called out properly
            - The banner and the voice now announce the **whole stack**, not just the last card played.

            ## Compatibility
            - The ready-reset on rule changes needs the **host** to be on 1.0.9.

            **Full Changelog**: https://github.com/gokulbits/uno-clash/compare/v1.0.8...v1.0.9
            """.trimIndent(),
        )
        assertEquals(5, blocks.size)
        assertEquals("whole stack", (blocks[2] as NoteBlock.Bullet).text.single { it.bold }.text)
        assertEquals("Compatibility", (blocks[3] as NoteBlock.Heading).text.plain())
    }
}
