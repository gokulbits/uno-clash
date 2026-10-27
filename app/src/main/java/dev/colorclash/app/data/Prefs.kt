package dev.colorclash.app.data

import android.content.Context
import java.io.File
import dev.colorclash.protocol.AUTO_AVATAR_COLOR
import dev.colorclash.protocol.Codec
import dev.colorclash.protocol.GameSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch

/** A guest seat we can get back into after the app was killed in the background. */
data class SavedSession(val host: String, val port: Int, val roomCode: String, val token: String, val savedAtMillis: Long)

/** Everyone sees this. [photo] is a small base64 JPEG shown instead of the avatar. */
data class Profile(
    val name: String,
    val avatar: Int,
    val avatarColor: Int = AUTO_AVATAR_COLOR,
    val avatarFrame: Int = 0,
    val photo: String? = null,
)

/** How the game looks on this phone only. */
data class LookSettings(val table: Int = 0, val cardBack: Int = 0)

data class FeedbackSettings(
    val sound: Boolean = true,
    val music: Boolean = true,
    val haptics: Boolean = true,
    /** Say card effects and picked colors out loud. */
    val voice: Boolean = true,
)

data class PlaySettings(
    /** First tap picks a card up, second tap plays it. Dragging up still plays straight away. */
    val tapToSelect: Boolean = false,
    val sortByNumber: Boolean = false,
    /** Mark each color with its own shape, so no card depends on telling colors apart. */
    val colorShapes: Boolean = false,
)

data class Stats(val gamesPlayed: Int = 0, val gamesWon: Int = 0, val roundsWon: Int = 0, val bestRound: Int = 0)

/**
 * Everything this phone remembers. The small values are read eagerly; the avatar photo is up to
 * 24 KB of base64, so it is read on [io] and folded in when it arrives rather than sitting on the
 * cold-start path. Writes to the photo file go the same way.
 */
class Prefs(context: Context, private val io: CoroutineScope) {
    private val sp = context.getSharedPreferences("color_clash", Context.MODE_PRIVATE)
    // The photo lives in its own file rather than bloating the preferences.
    private val photoFile = File(context.filesDir, PHOTO_FILE)

    private val _profile = MutableStateFlow(
        Profile(
            sp.getString(KEY_NAME, null) ?: "",
            sp.getInt(KEY_AVATAR, 0),
            sp.getInt(KEY_AVATAR_COLOR, AUTO_AVATAR_COLOR),
            sp.getInt(KEY_AVATAR_FRAME, 0),
            photo = null,
        ),
    )
    val profile: StateFlow<Profile> = _profile.asStateFlow()

    /** Cancelled by [updateProfile], so a slow read can't bring back a photo that was just changed. */
    private val photoLoad: Job = io.launch {
        val stored = runCatching { photoFile.takeIf { it.exists() }?.readText() }.getOrNull() ?: return@launch
        _profile.update { if (it.photo == null) it.copy(photo = stored) else it }
    }

    private val _looks = MutableStateFlow(LookSettings(sp.getInt(KEY_TABLE, 0), sp.getInt(KEY_CARD_BACK, 0)))
    val looks: StateFlow<LookSettings> = _looks.asStateFlow()

    private val _feedback = MutableStateFlow(
        FeedbackSettings(
            sp.getBoolean(KEY_SOUND, true),
            sp.getBoolean(KEY_MUSIC, true),
            sp.getBoolean(KEY_HAPTICS, true),
            sp.getBoolean(KEY_VOICE, true),
        ),
    )
    val feedback: StateFlow<FeedbackSettings> = _feedback.asStateFlow()

    private val _play = MutableStateFlow(
        PlaySettings(sp.getBoolean(KEY_TAP_TO_SELECT, false), sp.getBoolean(KEY_SORT_BY_NUMBER, false), sp.getBoolean(KEY_COLOR_SHAPES, false)),
    )
    val play: StateFlow<PlaySettings> = _play.asStateFlow()

    private val _stats = MutableStateFlow(
        Stats(sp.getInt(KEY_GAMES, 0), sp.getInt(KEY_WINS, 0), sp.getInt(KEY_ROUNDS_WON, 0), sp.getInt(KEY_BEST_ROUND, 0)),
    )
    val stats: StateFlow<Stats> = _stats.asStateFlow()

    fun updatePlay(settings: PlaySettings) {
        _play.value = settings
        sp.edit()
            .putBoolean(KEY_TAP_TO_SELECT, settings.tapToSelect)
            .putBoolean(KEY_SORT_BY_NUMBER, settings.sortByNumber)
            .putBoolean(KEY_COLOR_SHAPES, settings.colorShapes)
            .apply()
    }

    fun updateStats(change: (Stats) -> Stats) {
        val stats = _stats.updateAndGet(change)
        sp.edit()
            .putInt(KEY_GAMES, stats.gamesPlayed)
            .putInt(KEY_WINS, stats.gamesWon)
            .putInt(KEY_ROUNDS_WON, stats.roundsWon)
            .putInt(KEY_BEST_ROUND, stats.bestRound)
            .apply()
    }

    fun updateProfile(profile: Profile) {
        val previous = _profile.value.photo
        _profile.value = profile
        if (profile.photo != previous) {
            photoLoad.cancel()
            // Up to 24 KB written to disk: not on whichever thread the settings screen ran on.
            io.launch {
                runCatching { if (profile.photo == null) photoFile.delete() else photoFile.writeText(profile.photo) }
            }
        }
        sp.edit()
            .putString(KEY_NAME, profile.name)
            .putInt(KEY_AVATAR, profile.avatar)
            .putInt(KEY_AVATAR_COLOR, profile.avatarColor)
            .putInt(KEY_AVATAR_FRAME, profile.avatarFrame)
            .apply()
    }

    fun updateLooks(looks: LookSettings) {
        _looks.value = looks
        sp.edit().putInt(KEY_TABLE, looks.table).putInt(KEY_CARD_BACK, looks.cardBack).apply()
    }

    fun updateFeedback(settings: FeedbackSettings) {
        _feedback.value = settings
        sp.edit()
            .putBoolean(KEY_SOUND, settings.sound)
            .putBoolean(KEY_MUSIC, settings.music)
            .putBoolean(KEY_HAPTICS, settings.haptics)
            .putBoolean(KEY_VOICE, settings.voice)
            .apply()
    }

    /** The house rules this phone last hosted with; the next room it opens starts with them. */
    var lobbySettings: GameSettings
        get() = sp.getString(KEY_LOBBY_SETTINGS, null)
            ?.let { runCatching { Codec.json.decodeFromString(GameSettings.serializer(), it) }.getOrNull() }
            ?: GameSettings()
        set(value) {
            sp.edit().putString(KEY_LOBBY_SETTINGS, Codec.json.encodeToString(GameSettings.serializer(), value)).apply()
        }

    /**
     * The seat token is the only credential here, so it goes through [SecretStore] rather than
     * sitting in the preferences in the clear. A token that won't decrypt (a restored backup, a
     * cleared keystore, or one written before this changed) just means no rejoin offer.
     */
    var lastSession: SavedSession?
        get() {
            val host = sp.getString(KEY_HOST, null) ?: return null
            val code = sp.getString(KEY_CODE, null) ?: return null
            val token = sp.getString(KEY_TOKEN, null)?.let(SecretStore::decrypt) ?: return null
            return SavedSession(host, sp.getInt(KEY_PORT, 0), code, token, sp.getLong(KEY_SAVED_AT, 0))
        }
        set(value) {
            val edit = sp.edit()
            val sealed = value?.let { SecretStore.encrypt(it.token) }
            if (value == null || sealed == null) {
                // Better no rejoin than a token we couldn't protect.
                edit.remove(KEY_HOST).remove(KEY_PORT).remove(KEY_CODE).remove(KEY_TOKEN).remove(KEY_SAVED_AT)
            } else {
                edit.putString(KEY_HOST, value.host).putInt(KEY_PORT, value.port).putString(KEY_CODE, value.roomCode)
                    .putString(KEY_TOKEN, sealed).putLong(KEY_SAVED_AT, value.savedAtMillis)
            }
            edit.apply()
        }

    private companion object {
        const val PHOTO_FILE = "avatar_photo.txt"
        const val KEY_NAME = "name"
        const val KEY_AVATAR = "avatar"
        const val KEY_AVATAR_COLOR = "avatar_color"
        const val KEY_AVATAR_FRAME = "avatar_frame"
        const val KEY_TABLE = "table"
        const val KEY_LOBBY_SETTINGS = "lobby_settings"
        const val KEY_CARD_BACK = "card_back"
        const val KEY_SOUND = "sound"
        const val KEY_MUSIC = "music"
        const val KEY_HAPTICS = "haptics"
        const val KEY_VOICE = "voice"
        const val KEY_TAP_TO_SELECT = "tap_to_select"
        const val KEY_SORT_BY_NUMBER = "sort_by_number"
        const val KEY_COLOR_SHAPES = "color_shapes"
        const val KEY_GAMES = "stats_games"
        const val KEY_WINS = "stats_wins"
        const val KEY_ROUNDS_WON = "stats_rounds_won"
        const val KEY_BEST_ROUND = "stats_best_round"
        const val KEY_HOST = "last_host"
        const val KEY_PORT = "last_port"
        const val KEY_CODE = "last_code"
        const val KEY_TOKEN = "last_token"
        const val KEY_SAVED_AT = "last_saved_at"
    }
}
