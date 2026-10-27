package dev.colorclash.app.update

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Releases are tagged vX.Y.Z and carry one color-clash-<version>.apk built by CI. */
const val RELEASE_REPO = "gokulbits/uno-clash"
const val LATEST_RELEASE_URL = "https://api.github.com/repos/$RELEASE_REPO/releases/latest"

/** The newest release on GitHub, boiled down to what the update prompt needs. */
data class Release(
    val version: String,
    val notes: String,
    val apkUrl: String,
    val apkBytes: Long,
    /** Hex SHA-256 of the APK when the release publishes one, so the download can be checked. */
    val sha256: String? = null,
)

/**
 * Picks the release apart. Null when there's nothing we can install: a draft, a pre-release, or a
 * release whose assets don't include an APK (CI failed after tagging).
 */
fun parseLatestRelease(json: String): Release? = runCatching {
    val release = Json.parseToJsonElement(json).jsonObject
    fun flag(name: String) = release[name]?.jsonPrimitive?.content == "true"
    if (flag("draft") || flag("prerelease")) return null

    val tag = release["tag_name"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: return null
    val apk = release["assets"]?.jsonArray
        ?.map { it.jsonObject }
        ?.firstOrNull { it["name"]?.jsonPrimitive?.content?.endsWith(".apk") == true }
        ?: return null
    val url = apk["browser_download_url"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: return null

    Release(
        version = tag.removePrefix("v"),
        notes = releaseNotes(release["body"]?.jsonPrimitive?.content ?: ""),
        apkUrl = url,
        apkBytes = apk["size"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
        sha256 = apk["digest"]?.jsonPrimitive?.content?.removePrefix("sha256:")?.takeIf { it.length == 64 },
    )
}.getOrNull()

/** "v1.0.2" -> [1, 0, 2]. Anything after the numbers (0.0.47-1a2b3c4) is ignored. */
fun versionParts(name: String): List<Int>? {
    val digits = name.trim().removePrefix("v").removePrefix("V").takeWhile { it.isDigit() || it == '.' }
    val parts = digits.split('.').filter { it.isNotEmpty() }.map { it.toIntOrNull() ?: return null }
    return parts.takeIf { it.isNotEmpty() }
}

/** True when [candidate] is a higher version than [current]. A version we can't read means no. */
fun isNewer(current: String?, candidate: String): Boolean {
    val have = current?.let(::versionParts) ?: return false
    val theirs = versionParts(candidate) ?: return false
    for (i in 0 until maxOf(have.size, theirs.size)) {
        val a = have.getOrElse(i) { 0 }
        val b = theirs.getOrElse(i) { 0 }
        if (a != b) return b > a
    }
    return false
}

/** GitHub's generated notes, cut down to a few bullets that fit in a dialog. */
fun releaseNotes(body: String, maxLines: Int = 6): String = body.lineSequence()
    .map { it.trim() }
    .filterNot { it.isEmpty() || it.startsWith("#") || it.startsWith("**Full Changelog**") }
    .map { it.removePrefix("* ").removePrefix("- ").substringBefore(" by @").trim() }
    .filter { it.isNotEmpty() }
    .take(maxLines)
    .joinToString("\n") { "• $it" }

/** Download size for the prompt, so nobody starts this on mobile data by accident. */
fun sizeLabel(bytes: Long): String = when {
    bytes <= 0 -> ""
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
}

fun percentOf(read: Long, total: Long): Int =
    if (total <= 0) 0 else (read * 100 / total).coerceIn(0, 100).toInt()
