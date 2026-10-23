package dev.colorclash.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.colorclash.app.ui.AppRoot
import dev.colorclash.app.ui.theme.ColorClashTheme
import dev.colorclash.protocol.JoinLink

class MainActivity : ComponentActivity() {
    private var incomingLink by mutableStateOf<JoinLink?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) incomingLink = linkFrom(intent)
        setContent {
            ColorClashTheme {
                AppRoot(colorClash, incomingLink, onLinkHandled = { incomingLink = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        linkFrom(intent)?.let { incomingLink = it }
    }

    private fun linkFrom(intent: Intent?): JoinLink? = intent?.dataString?.let(JoinLink::parse)
}
