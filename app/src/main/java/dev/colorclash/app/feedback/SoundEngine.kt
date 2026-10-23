package dev.colorclash.app.feedback

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import dev.colorclash.app.R
import dev.colorclash.app.data.FeedbackSettings
import kotlinx.coroutines.flow.StateFlow

enum class Sfx(val res: Int) {
    SHUFFLE(R.raw.sfx_shuffle),
    DEAL(R.raw.sfx_deal),
    FLIP(R.raw.sfx_flip),
    DRAW(R.raw.sfx_draw),
    SKIP(R.raw.sfx_skip),
    REVERSE(R.raw.sfx_reverse),
    WILD(R.raw.sfx_wild),
    LAST_CARD(R.raw.sfx_last_card),
    PENALTY(R.raw.sfx_penalty),
    WIN(R.raw.sfx_win),
    LOSE(R.raw.sfx_lose),
    YOUR_TURN(R.raw.sfx_turn),
}

/** Short effects through SoundPool, plus the optional music loop. Both follow the player's settings. */
class SoundEngine(private val context: Context, private val settings: StateFlow<FeedbackSettings>) {
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
    private val pool = SoundPool.Builder().setMaxStreams(MAX_STREAMS).setAudioAttributes(attributes).build()
    private val ids = Sfx.entries.associateWith { pool.load(context, it.res, 1) }
    private var music: MediaPlayer? = null
    private var musicWanted = false

    fun play(sfx: Sfx, volume: Float = 1f) {
        if (!settings.value.sound) return
        ids[sfx]?.let { pool.play(it, volume, volume, 1, 0, 1f) }
    }

    /** Call when a screen that wants music comes and goes, and when the app goes to the background. */
    fun setMusicWanted(wanted: Boolean) {
        musicWanted = wanted
        refreshMusic()
    }

    fun refreshMusic() {
        val shouldPlay = musicWanted && settings.value.music
        if (shouldPlay) {
            val player = music ?: MediaPlayer.create(context, R.raw.music_loop)?.apply {
                isLooping = true
                setVolume(MUSIC_VOLUME, MUSIC_VOLUME)
            }.also { music = it }
            if (player != null && !player.isPlaying) player.start()
        } else {
            music?.takeIf { it.isPlaying }?.pause()
        }
    }

    private companion object {
        const val MAX_STREAMS = 6
        const val MUSIC_VOLUME = 0.35f
    }
}
