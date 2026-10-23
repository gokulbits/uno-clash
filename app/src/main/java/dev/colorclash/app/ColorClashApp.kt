package dev.colorclash.app

import android.app.Application
import android.content.Context
import dev.colorclash.app.data.Prefs
import dev.colorclash.app.session.SessionManager

class ColorClashApp : Application() {
    lateinit var prefs: Prefs
        private set
    lateinit var sessions: SessionManager
        private set

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        sessions = SessionManager(this, prefs)
    }
}

val Context.colorClash: ColorClashApp get() = applicationContext as ColorClashApp
