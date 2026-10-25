package dev.colorclash.app.ui.room

import dev.colorclash.app.ui.theme.GAME_TITLE

/** What a room is called where people see it: "Sam's Clash". */
fun clashName(hostName: String): String = if (hostName.isBlank()) GAME_TITLE else "${hostName.trim()}'s Clash"
