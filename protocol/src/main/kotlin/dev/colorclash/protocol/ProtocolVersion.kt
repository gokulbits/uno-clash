package dev.colorclash.protocol

/** Bump whenever a message shape changes so mismatched APKs refuse to connect. */
const val PROTOCOL_VERSION = 5

/** Avatar color meaning "whatever color goes with this avatar". */
const val AUTO_AVATAR_COLOR = -1

/** Longest photo the host passes on, in base64 characters (about 18 KB of JPEG). */
const val MAX_PHOTO_CHARS = 24_000

const val DEFAULT_PORT = 47474
const val WS_PATH = "/play"
