package dev.colorclash.app.ui.room

import dev.colorclash.app.ui.theme.GAME_TITLE
import dev.colorclash.protocol.LobbyView
import dev.colorclash.rules.GameEngine

/** What a room is called where people see it: "Sam's Clash". */
fun clashName(hostName: String): String = if (hostName.isBlank()) GAME_TITLE else "${hostName.trim()}'s Clash"

/** The host's start button, which says what it's waiting for when it can't start yet. */
fun startLabel(lobby: LobbyView): String = when {
    lobby.canStart -> "Start game"
    lobby.seats.size < GameEngine.MIN_PLAYERS -> "Add a bot or wait for a friend"
    else -> "Waiting for everyone to be ready"
}
