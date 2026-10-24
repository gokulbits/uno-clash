package dev.colorclash.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A guest seat we can get back into after the app was killed in the background. */
data class SavedSession(val host: String, val port: Int, val roomCode: String, val token: String, val savedAtMillis: Long)

data class Profile(val name: String, val avatar: Int)

data class FeedbackSettings(
    val sound: Boolean = true,
    val music: Boolean = true,
    val haptics: Boolean = true,
    /** Say card effects and picked colors out loud. */
    val voice: Boolean = true,
)

/** How you like to play, kept per phone. */
data class PlaySettings(
    /** First tap picks a card up, second tap plays it. Dragging up still plays straight away. */
    val tapToSelect: Boolean = false,
    /** Sort the hand by number rather than by color. */
    val sortByNumber: Boolean = false,
    /** Mark each color with its own shape, so no card depends on telling colors apart. */
    val colorShapes: Boolean = false,
)

/** Your record on this phone. */
data class Stats(val gamesPlayed: Int = 0, val gamesWon: Int = 0, val roundsWon: Int = 0, val bestRound: Int = 0)

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("color_clash", Context.MODE_PRIVATE)

    private val _profile = MutableStateFlow(Profile(sp.getString(KEY_NAME, null) ?: "", sp.getInt(KEY_AVATAR, 0)))
    val profile: StateFlow<Profile> = _profile.asStateFlow()

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
        val stats = change(_stats.value)
        _stats.value = stats
        sp.edit()
            .putInt(KEY_GAMES, stats.gamesPlayed)
            .putInt(KEY_WINS, stats.gamesWon)
            .putInt(KEY_ROUNDS_WON, stats.roundsWon)
            .putInt(KEY_BEST_ROUND, stats.bestRound)
            .apply()
    }

    fun updateProfile(profile: Profile) {
        _profile.value = profile
        sp.edit().putString(KEY_NAME, profile.name).putInt(KEY_AVATAR, profile.avatar).apply()
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

    var lastSession: SavedSession?
        get() {
            val host = sp.getString(KEY_HOST, null) ?: return null
            val code = sp.getString(KEY_CODE, null) ?: return null
            val token = sp.getString(KEY_TOKEN, null) ?: return null
            return SavedSession(host, sp.getInt(KEY_PORT, 0), code, token, sp.getLong(KEY_SAVED_AT, 0))
        }
        set(value) {
            val edit = sp.edit()
            if (value == null) {
                edit.remove(KEY_HOST).remove(KEY_PORT).remove(KEY_CODE).remove(KEY_TOKEN).remove(KEY_SAVED_AT)
            } else {
                edit.putString(KEY_HOST, value.host).putInt(KEY_PORT, value.port).putString(KEY_CODE, value.roomCode)
                    .putString(KEY_TOKEN, value.token).putLong(KEY_SAVED_AT, value.savedAtMillis)
            }
            edit.apply()
        }

    private companion object {
        const val KEY_NAME = "name"
        const val KEY_AVATAR = "avatar"
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
