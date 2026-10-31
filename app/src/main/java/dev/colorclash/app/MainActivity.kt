package dev.colorclash.app

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import dev.colorclash.app.ui.AppRoot
import dev.colorclash.app.ui.theme.ColorClashTheme
import dev.colorclash.app.ui.theme.GameTheme
import dev.colorclash.protocol.JoinLink

class MainActivity : ComponentActivity() {
    private var incomingLink by mutableStateOf<JoinLink?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Always light icons: every table is dark, whatever the phone's own theme is set to.
        val bars = SystemBarStyle.dark(Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        // Behind the first frame, so an OLED table doesn't open on a flash of purple.
        window.setBackgroundDrawable(ColorDrawable(GameTheme.table(colorClash.prefs.looks.value.table).bottom.toArgb()))
        if (savedInstanceState == null) incomingLink = linkFrom(intent)
        setContent {
            ColorClashTheme {
                AppRoot(colorClash, incomingLink, onLinkHandled = { incomingLink = null })
            }
        }
    }

    override fun onStart() {
        super.onStart()
        colorClash.sound.setVisible(true)
    }

    override fun onStop() {
        colorClash.sound.setVisible(false)
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        linkFrom(intent)?.let { incomingLink = it }
    }

    private fun linkFrom(intent: Intent?): JoinLink? = intent?.dataString?.let(JoinLink::parse)
}
