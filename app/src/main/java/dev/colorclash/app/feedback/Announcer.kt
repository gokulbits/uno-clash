package dev.colorclash.app.feedback

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dev.colorclash.app.data.FeedbackSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * Calls out card effects through the phone's text-to-speech. Silent without an engine.
 * [onSpeaking] hears when a call-out starts and stops, so the music can make room for it.
 */
class Announcer(
    context: Context,
    private val settings: StateFlow<FeedbackSettings>,
    io: CoroutineScope,
    private val onSpeaking: (Boolean) -> Unit = {},
) {
    @Volatile private var ready = false
    @Volatile private var tts: TextToSpeech? = null

    init {
        // The init callback lands on the main thread, and picking a language is blocking IPC to
        // the engine, right as the app is trying to draw its first frame.
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) io.launch { runCatching { start() } }
        }
    }

    private fun start() {
        val engine = tts ?: return
        val locale = Locale.getDefault().takeIf { engine.isLanguageAvailable(it) >= TextToSpeech.LANG_AVAILABLE } ?: Locale.US
        engine.setLanguage(locale)
        engine.setSpeechRate(1.1f)
        engine.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        // Counted, because a flushed call-out can report stopping after the next one has started.
        val speaking = AtomicInteger()
        fun ended() = onSpeaking(speaking.updateAndGet { (it - 1).coerceAtLeast(0) } > 0)
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = onSpeaking(speaking.incrementAndGet() > 0)
            override fun onDone(utteranceId: String?) = ended()
            override fun onStop(utteranceId: String?, interrupted: Boolean) = ended()
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = ended()
        })
        ready = true
    }

    fun say(text: String) {
        if (!ready || !settings.value.voice) return
        // Flush: if the table moves fast, the latest call-out is the one worth hearing.
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, LOUD, UTTERANCE_ID)
    }

    /** Lets go of the bound engine process. There is no app-level teardown hook, so this is for tests. */
    fun shutdown() {
        ready = false
        tts?.let { runCatching { it.shutdown() } }
        tts = null
    }

    private companion object {
        const val UTTERANCE_ID = "announce"
        val LOUD = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1f) }
    }
}
