package dev.colorclash.app.feedback

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import dev.colorclash.app.R
import dev.colorclash.app.data.FeedbackSettings
import kotlinx.coroutines.flow.StateFlow
import java.util.EnumMap

/**
 * [minGapMillis] is how long this effect refuses to start again — for the reaction clips, their own
 * length, or six phones' worth of them turn into noise. Card sounds have no gap and overlap freely.
 * [ducksMusic] drops the music under a clip that has someone talking in it.
 */
enum class Sfx(val res: Int, val minGapMillis: Long = 0, val ducksMusic: Boolean = false) {
    SHUFFLE(R.raw.sfx_shuffle),
    DEAL(R.raw.sfx_deal),
    FLIP(R.raw.sfx_flip),
    LAND(R.raw.sfx_land),
    DRAW(R.raw.sfx_draw),
    SKIP(R.raw.sfx_skip),
    REVERSE(R.raw.sfx_reverse),
    WILD(R.raw.sfx_wild),
    LAST_CARD(R.raw.sfx_last_card),
    PENALTY(R.raw.sfx_penalty),
    WIN(R.raw.sfx_win),
    LOSE(R.raw.sfx_lose),
    YOUR_TURN(R.raw.sfx_turn),
    POP(R.raw.sfx_pop, 250),
    TICK(R.raw.sfx_tick),
    // Not generated: the owner's own clips. Gaps are each clip's length, so one can't overlap itself.
    FAAA(R.raw.sfx_faaa, 2_600, ducksMusic = true),
    SUS(R.raw.sfx_sus, 4_700, ducksMusic = true),
    BRUH(R.raw.sfx_bruh, 900, ducksMusic = true),
    EH_EH_EH(R.raw.sfx_eh_eh_eh, 3_500, ducksMusic = true),
    QUACK(R.raw.sfx_quack, 1_200),
    PUNCH(R.raw.sfx_punch, 1_600),
    RIZZ(R.raw.sfx_rizz, 1_900, ducksMusic = true),
    SHOCKED(R.raw.sfx_shocked, 1_500, ducksMusic = true),
    WOWW(R.raw.sfx_woww, 4_300, ducksMusic = true),
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
    private val main = Handler(Looper.getMainLooper())
    private val lastPlayed = EnumMap<Sfx, Long>(Sfx::class.java)
    private var duckedUntil = 0L

    fun play(sfx: Sfx, volume: Float = 1f) {
        if (!settings.value.sound) return
        val now = SystemClock.uptimeMillis()
        if (!canPlayAgain(lastPlayed[sfx], now, sfx.minGapMillis)) return
        lastPlayed[sfx] = now
        ids[sfx]?.let { pool.play(it, volume, volume, 1, 0, 1f) }
        if (sfx.ducksMusic) duckUnder(sfx.minGapMillis)
    }

    /** Hold the music down until the clip is done; a later clip extends the dip, never cuts it short. */
    private fun duckUnder(millis: Long) {
        val until = SystemClock.uptimeMillis() + millis
        duckedUntil = maxOf(duckedUntil, until)
        duckMusic(true)
        main.postDelayed({ if (SystemClock.uptimeMillis() >= duckedUntil) duckMusic(false) }, millis + 50)
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

    /** Turns the music right down while a card effect is being called out, then back up. Any thread. */
    fun duckMusic(ducked: Boolean) {
        main.post {
            val volume = if (ducked) DUCKED_VOLUME else MUSIC_VOLUME
            music?.setVolume(volume, volume)
        }
    }

    private companion object {
        // Dealing overlaps a lot of short sounds.
        const val MAX_STREAMS = 8
        const val MUSIC_VOLUME = 0.2f
        const val DUCKED_VOLUME = 0.05f
    }
}
