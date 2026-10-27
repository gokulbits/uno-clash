package dev.colorclash.app.feedback

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import dev.colorclash.app.R
import dev.colorclash.app.data.FeedbackSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.EnumMap

/**
 * [minGapMillis] is how long this effect refuses to start again — for the reaction clips, their own
 * length, or six phones' worth of them turn into noise. Card sounds have no gap and overlap freely.
 * [ducksMusic] drops the music under a clip that has someone talking in it. [streamed] clips get
 * their own player: SoundPool keeps samples decoded in memory and quietly truncates anything past
 * about a megabyte, which is a few seconds of stereo.
 */
enum class Sfx(
    val res: Int,
    val minGapMillis: Long = 0,
    val ducksMusic: Boolean = false,
    val streamed: Boolean = false,
) {
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
    SUS(R.raw.sfx_sus, 4_700, ducksMusic = true, streamed = true),
    BRUH(R.raw.sfx_bruh, 900, ducksMusic = true),
    EH_EH_EH(R.raw.sfx_eh_eh_eh, 7_200, ducksMusic = true, streamed = true),
    QUACK(R.raw.sfx_quack, 1_200),
    PUNCH(R.raw.sfx_punch, 1_600),
    RIZZ(R.raw.sfx_rizz, 1_900, ducksMusic = true),
    SHOCKED(R.raw.sfx_shocked, 1_500, ducksMusic = true),
    WOWW(R.raw.sfx_woww, 4_300, ducksMusic = true, streamed = true),
}

/**
 * Short effects through SoundPool, plus the optional music loop. Both follow the player's settings.
 * Players and samples are given back when the app goes to the background or the system asks for
 * room: a paused MediaPlayer holds a decoder and an AudioTrack for the whole process otherwise.
 * [pool], [music] and [clips] are main-thread only; preparing a player is not, because that opens
 * the resource and decodes inline.
 */
class SoundEngine(private val context: Context, private val settings: StateFlow<FeedbackSettings>) : ComponentCallbacks2 {
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val main = Handler(Looper.getMainLooper())

    private var pool: SoundPool? = null
    private var ids: Map<Sfx, Int> = emptyMap()
    private var music: MediaPlayer? = null
    private var musicJob: Job? = null
    private var musicWanted = false
    private val lastPlayed = EnumMap<Sfx, Long>(Sfx::class.java)
    private var duckedUntil = 0L
    /** Streamed clips in flight; main thread only. */
    private val clips = mutableListOf<MediaPlayer>()

    init {
        loadSamples()
    }

    fun play(sfx: Sfx, volume: Float = 1f) {
        if (!settings.value.sound) return
        val now = SystemClock.uptimeMillis()
        if (!canPlayAgain(lastPlayed[sfx], now, sfx.minGapMillis)) return
        lastPlayed[sfx] = now
        if (sfx.streamed) {
            stream(sfx, volume)
        } else {
            val active = pool ?: loadSamples()
            ids[sfx]?.let { active.play(it, volume, volume, 1, 0, 1f) }
        }
        if (sfx.ducksMusic) duckUnder(sfx.minGapMillis)
    }

    /** SoundPool decodes on its own worker, so this doesn't block; the first play after a reload can be silent. */
    private fun loadSamples(): SoundPool {
        val fresh = SoundPool.Builder().setMaxStreams(MAX_STREAMS).setAudioAttributes(attributes).build()
        ids = Sfx.entries.filterNot { it.streamed }.associateWith { fresh.load(context, it.res, 1) }
        pool = fresh
        return fresh
    }

    /**
     * A clip too long for the pool, on its own player. Prepared off the main thread: the old
     * MediaPlayer.create() ran the resource open and prepare inline and dropped frames mid-game.
     */
    private fun stream(sfx: Sfx, volume: Float) {
        io.launch {
            val player = runCatching { prepared(sfx.res) { setVolume(volume, volume) } }.getOrNull() ?: return@launch
            withContext(Dispatchers.Main) {
                if (!settings.value.sound) return@withContext runCatching { player.release() }.let { }
                player.setOnCompletionListener { finished(it) }
                // Without this a clip that fails would sit in `clips` forever, holding a codec.
                player.setOnErrorListener { failed, _, _ -> finished(failed); true }
                clips += player
                player.start()
            }
        }
    }

    private fun prepared(res: Int, setUp: MediaPlayer.() -> Unit): MediaPlayer = MediaPlayer().apply {
        setAudioAttributes(attributes)
        context.resources.openRawResourceFd(res).use { fd ->
            setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
            prepare()
        }
        setUp()
    }

    /** Main thread only, like [clips]. */
    private fun finished(player: MediaPlayer) {
        clips -= player
        runCatching { player.release() }
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
        if (musicWanted && settings.value.music) startMusic() else releaseMusic()
    }

    private fun startMusic() {
        music?.let {
            if (!it.isPlaying) it.start()
            return
        }
        if (musicJob?.isActive == true) return
        musicJob = io.launch {
            val player = runCatching {
                prepared(R.raw.music_loop) {
                    isLooping = true
                    setVolume(MUSIC_VOLUME, MUSIC_VOLUME)
                }
            }.getOrNull() ?: return@launch
            withContext(Dispatchers.Main) {
                // The app may have gone to the background while this was preparing.
                if (!musicWanted || !settings.value.music) return@withContext runCatching { player.release() }.let { }
                music?.let { runCatching { it.release() } }
                music = player
                player.start()
            }
        }
    }

    /** Released rather than paused; the loop just starts from the top next time, which nobody notices. */
    private fun releaseMusic() {
        musicJob?.cancel()
        musicJob = null
        music?.let { runCatching { it.release() } }
        music = null
    }

    /** Turns the music right down while a card effect is being called out, then back up. Any thread. */
    fun duckMusic(ducked: Boolean) {
        main.post {
            val volume = if (ducked) DUCKED_VOLUME else MUSIC_VOLUME
            music?.setVolume(volume, volume)
        }
    }

    /** Hands everything back. There is no reliable app-level teardown on Android, so this is for tests. */
    fun release() {
        main.post {
            releaseMusic()
            clips.toList().forEach { finished(it) }
            pool?.release()
            pool = null
            ids = emptyMap()
        }
    }

    /**
     * Only UI_HIDDEN and BACKGROUND still arrive on current Android; the finer levels are
     * deprecated and no longer delivered. Out of sight means the whole-player decoders go back;
     * the pooled samples stay, because the host keeps playing with the screen off.
     */
    override fun onTrimMemory(level: Int) {
        if (level < ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) return
        main.post {
            releaseMusic()
            clips.toList().forEach { finished(it) }
        }
    }

    /** Last call before the process is killed: the decoded samples go too, and reload on next play. */
    @Deprecated("Deprecated in Java")
    override fun onLowMemory() {
        main.post {
            releaseMusic()
            clips.toList().forEach { finished(it) }
            pool?.release()
            pool = null
            ids = emptyMap()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) = Unit

    private companion object {
        // Dealing overlaps a lot of short sounds.
        const val MAX_STREAMS = 8
        const val MUSIC_VOLUME = 0.2f
        const val DUCKED_VOLUME = 0.05f
    }
}
