package dev.colorclash.app

import android.app.Application
import android.content.Context
import dev.colorclash.app.data.Prefs
import dev.colorclash.app.feedback.Feedback
import dev.colorclash.app.feedback.Haptics
import dev.colorclash.app.feedback.SoundEngine
import dev.colorclash.app.session.SessionManager
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class ColorClashApp : Application() {
    lateinit var prefs: Prefs
        private set
    lateinit var sessions: SessionManager
        private set
    lateinit var sound: SoundEngine
        private set
    lateinit var feedback: Feedback
        private set

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        sessions = SessionManager(this, prefs)
        sound = SoundEngine(this, prefs.feedback)
        feedback = Feedback(sound, Haptics(this, prefs.feedback))
        // Music follows the setting as soon as it's flipped.
        MainScope().launch { prefs.feedback.collect { sound.refreshMusic() } }
    }
}

val Context.colorClash: ColorClashApp get() = applicationContext as ColorClashApp
