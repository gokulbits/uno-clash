package dev.colorclash.app.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UpdatesTest {

    private fun releaseJson(
        tag: String = "v1.0.2",
        draft: Boolean = false,
        prerelease: Boolean = false,
        assets: String = """[{"name": "color-clash-1.0.2.apk", "size": 13107200,
            "browser_download_url": "https://github.com/x/y/releases/download/v1.0.2/color-clash-1.0.2.apk"}]""",
        body: String = "## What's Changed\n* Faaa bubble by @gokulbits in https://github.com/x/y/pull/7\n\n**Full Changelog**: https://github.com/x/y/compare/v1.0.1...v1.0.2",
    ) = """
        {"tag_name": "$tag", "draft": $draft, "prerelease": $prerelease,
         "body": ${'"'}${body.replace("\n", "\n")}${'"'}, "assets": $assets}
    """.trimIndent()

    @Test
    fun `reads version, apk and notes out of a release`() {
        val release = parseLatestRelease(releaseJson())!!
        assertEquals("1.0.2", release.version)
        assertEquals(13107200L, release.apkBytes)
        assertTrue(release.apkUrl.endsWith("color-clash-1.0.2.apk"))
        assertEquals("• Faaa bubble", release.notes)
    }

    @Test
    fun `drafts, pre-releases and releases without an apk are skipped`() {
        assertNull(parseLatestRelease(releaseJson(draft = true)))
        assertNull(parseLatestRelease(releaseJson(prerelease = true)))
        assertNull(parseLatestRelease(releaseJson(assets = "[]")))
        assertNull(parseLatestRelease(releaseJson(assets = """[{"name": "notes.txt", "size": 12, "browser_download_url": "https://x/notes.txt"}]""")))
    }

    @Test
    fun `garbage json is not an update`() {
        assertNull(parseLatestRelease(""))
        assertNull(parseLatestRelease("""{"message": "Not Found"}"""))
        assertNull(parseLatestRelease("<html>rate limited</html>"))
    }

    @Test
    fun `version numbers compare by part, not as text`() {
        assertEquals(listOf(1, 0, 2), versionParts("v1.0.2"))
        assertEquals(listOf(0, 0, 47), versionParts("0.0.47-1a2b3c4"))
        assertNull(versionParts("nightly"))

        assertTrue(isNewer("1.0.2", "1.0.10"))
        assertTrue(isNewer("1.0.2", "1.1"))
        assertTrue(isNewer("0.5.0", "1.0.0"))
        assertFalse(isNewer("1.0.2", "1.0.2"))
        assertFalse(isNewer("1.0.2", "1.0.1"))
        assertFalse(isNewer("1.0.2", "1.0"))
    }

    @Test
    fun `an unreadable version on either side never prompts`() {
        assertFalse(isNewer(null, "1.0.2"))
        assertFalse(isNewer("nightly", "1.0.2"))
        assertFalse(isNewer("1.0.2", "latest"))
    }

    @Test
    fun `sizes and progress are shown in human terms`() {
        assertEquals("", sizeLabel(0))
        assertEquals("640 KB", sizeLabel(655_360))
        assertEquals("12.5 MB", sizeLabel(13_107_200))

        assertEquals(0, percentOf(5, 0))
        assertEquals(50, percentOf(50, 100))
        assertEquals(100, percentOf(200, 100))
    }
}
