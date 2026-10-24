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
        const val KEY_HOST = "last_host"
        const val KEY_PORT = "last_port"
        const val KEY_CODE = "last_code"
        const val KEY_TOKEN = "last_token"
        const val KEY_SAVED_AT = "last_saved_at"
    }
}
