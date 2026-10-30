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
        /**
         * Set when rejoining a seat: a nonce this phone made up, and an HMAC of it under the seat's
         * token. The client never sends the token, and the host honours each nonce once, so replaying
         * a captured rejoin gets nowhere. The host does hand the token out in [ServerMessage.Welcome]
         * over plain ws://, so this is no defence against someone sniffing the Wi-Fi from the start.
         */
        val rejoinNonce: String? = null,
        val rejoinProof: String? = null,
        val avatarColor: Int = AUTO_AVATAR_COLOR,
        val avatarFrame: Int = 0,
        /** Small base64 JPEG shown instead of the avatar, at most [MAX_PHOTO_CHARS]. */
        val photo: String? = null,
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

    /** Index into the app's reactions: emoji, then quick chat. */
    @Serializable
    @SerialName("react")
    data class React(val reaction: Int) : ClientMessage
}

/** The host drops reactions outside this range. */
const val REACTION_COUNT = 29

/**
 * Reactions make noise, so the host rations them: a few in a row for a burst of banter, then one
 * every [REACTION_GAP_MILLIS]. The app greys the button out on the same numbers, so a dropped
 * reaction isn't the first a player hears about it.
 */
const val REACTION_GAP_MILLIS = 2_000L
const val REACTION_BURST = 3
const val REACTION_WINDOW_MILLIS = 8_000L

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

    /** Sent once rather than with every view; [SeatInfo.photoId] says whose it is. */
    @Serializable
    @SerialName("photo")
    data class Photo(val id: String, val data: String) : ServerMessage

    @Serializable
    @SerialName("closed")
    data class Closed(val reason: String) : ServerMessage
}
