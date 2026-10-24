package dev.colorclash.protocol

import dev.colorclash.rules.Action
import dev.colorclash.rules.CardColor
import dev.colorclash.rules.PlayerAction
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface ClientMessage {
    /**
     * First message on every connection. Keep this shape stable across versions so an old APK
     * can always be told it's out of date.
     */
    @Serializable
    @SerialName("hello")
    data class Hello(
        val protocolVersion: Int,
        val roomCode: String,
        val name: String,
        val avatar: Int = 0,
        /** Set when rejoining a seat. */
        val sessionToken: String? = null,
    ) : ClientMessage

    @Serializable
    @SerialName("ready")
    data class SetReady(val ready: Boolean) : ClientMessage

    @Serializable
    @SerialName("intent")
    data class Intent(val intent: PlayerIntent) : ClientMessage

    @Serializable
    @SerialName("ping")
    data class Ping(val nonce: Long) : ClientMessage

    @Serializable
    @SerialName("leave")
    data object Leave : ClientMessage

    /** Back at the table after timing out too often; the bot hands the seat back. */
    @Serializable
    @SerialName("resume")
    data object Resume : ClientMessage

    /** An emoji reaction or quick chat phrase, by index into the app's list of [REACTION_COUNT]. */
    @Serializable
    @SerialName("react")
    data class React(val reaction: Int) : ClientMessage
}

/** How many reactions there are (emoji, then quick chat phrases). The host drops anything outside this range. */
const val REACTION_COUNT = 20

/** What a player wants to do. The seat comes from the connection, never from the client. */
@Serializable
sealed interface PlayerIntent {
    @Serializable @SerialName("play")
    data class Play(val cardId: Int) : PlayerIntent

    @Serializable @SerialName("color")
    data class ChooseColor(val color: CardColor) : PlayerIntent

    @Serializable @SerialName("draw")
    data object Draw : PlayerIntent

    @Serializable @SerialName("pass")
    data object Pass : PlayerIntent

    @Serializable @SerialName("lastCard")
    data object CallLastCard : PlayerIntent

    @Serializable @SerialName("catch")
    data class Catch(val target: Int) : PlayerIntent

    @Serializable @SerialName("challenge")
    data object Challenge : PlayerIntent

    @Serializable @SerialName("swap")
    data class SwapWith(val target: Int) : PlayerIntent
}

fun PlayerIntent.toAction(seat: Int): PlayerAction = when (this) {
    is PlayerIntent.Play -> Action.PlayCard(seat, cardId)
    is PlayerIntent.ChooseColor -> Action.ChooseColor(seat, color)
    PlayerIntent.Draw -> Action.Draw(seat)
    PlayerIntent.Pass -> Action.Pass(seat)
    PlayerIntent.CallLastCard -> Action.CallLastCard(seat)
    is PlayerIntent.Catch -> Action.CatchLastCard(seat, target)
    PlayerIntent.Challenge -> Action.ChallengeDrawFour(seat)
    is PlayerIntent.SwapWith -> Action.SwapWith(seat, target)
}

enum class JoinError { VERSION_MISMATCH, WRONG_ROOM, ROOM_FULL, GAME_IN_PROGRESS, BAD_REQUEST }

@Serializable
sealed interface ServerMessage {
    @Serializable
    @SerialName("welcome")
    data class Welcome(val seat: Int, val sessionToken: String, val roomCode: String) : ServerMessage

    @Serializable
    @SerialName("joinRejected")
    data class JoinRejected(val error: JoinError, val message: String) : ServerMessage

    @Serializable
    @SerialName("lobby")
    data class Lobby(val view: LobbyView) : ServerMessage

    /** Sent after every change. [events] is empty for a plain resync. */
    @Serializable
    @SerialName("game")
    data class Game(val view: PlayerView, val events: List<EventView> = emptyList()) : ServerMessage

    @Serializable
    @SerialName("actionRejected")
    data class ActionRejected(val reason: String) : ServerMessage

    @Serializable
    @SerialName("pong")
    data class Pong(val nonce: Long) : ServerMessage

    /** Someone at the table reacted. Sent to everyone, the sender included. */
    @Serializable
    @SerialName("reaction")
    data class Reaction(val seat: Int, val reaction: Int) : ServerMessage

    /** Host shut the room down. */
    @Serializable
    @SerialName("closed")
    data class Closed(val reason: String) : ServerMessage
}
