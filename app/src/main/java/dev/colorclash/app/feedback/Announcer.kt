package dev.colorclash.app.feedback

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import dev.colorclash.app.data.FeedbackSettings
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/** Calls out card effects through the phone's text-to-speech. Silent without an engine. */
class Announcer(context: Context, private val settings: StateFlow<FeedbackSettings>) {
    @Volatile private var ready = false
    private var tts: TextToSpeech? = null

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) start()
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
        ready = true
    }

    fun say(text: String) {
        if (!ready || !settings.value.voice) return
        // Flush: if the table moves fast, the latest call-out is the one worth hearing.
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
    }

    private companion object {
        const val UTTERANCE_ID = "announce"
    }
}
