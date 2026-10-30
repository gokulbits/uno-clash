package dev.colorclash.app

import android.app.Application
import android.content.Context
import dev.colorclash.app.data.Prefs
import dev.colorclash.app.feedback.Announcer
import dev.colorclash.app.feedback.Feedback
import dev.colorclash.app.feedback.Haptics
import dev.colorclash.app.feedback.SoundEngine
import dev.colorclash.app.session.Session
import dev.colorclash.app.session.SessionManager
import dev.colorclash.app.ui.room.RoomPresenter
import dev.colorclash.app.update.Updater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
    lateinit var updater: Updater
        private set

    /** Both live as long as the process, like everything above them. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var presenter: RoomPresenter? = null

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this, io)
        sessions = SessionManager(this, prefs)
        sound = SoundEngine(this, prefs.feedback)
        feedback = Feedback(sound, Haptics(this, prefs.feedback), Announcer(this, prefs.feedback, io, sound::duckMusic))
        updater = Updater(this)
        // So the engine hears about memory pressure and hands its players back.
        registerComponentCallbacks(sound)
        // Music follows the setting as soon as it's flipped.
        scope.launch { prefs.feedback.collect { sound.refreshMusic() } }
        // The room's presenter goes when the room does.
        scope.launch {
            sessions.session.collect { if (it == null) closePresenter() }
        }
    }

    /**
     * The presenter for the room we're in. Held here rather than remembered in composition so the
     * table's history survives the game screen coming and going.
     */
    fun presenterFor(session: Session): RoomPresenter {
        presenter?.let { if (it.session === session) return it }
        closePresenter()
        return RoomPresenter(session, prefs, feedback, scope).also { presenter = it }
    }

    private fun closePresenter() {
        presenter?.close()
        presenter = null
    }
}

val Context.colorClash: ColorClashApp get() = applicationContext as ColorClashApp
