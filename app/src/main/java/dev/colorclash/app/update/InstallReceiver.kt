package dev.colorclash.app.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.core.content.IntentCompat
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Where a staged install reports back. The system asks the user to confirm first, and that prompt
 * has to be started from here; on success the app is replaced, so only failures are worth passing on.
 */
class InstallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java) ?: return
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(confirm) }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit
            else -> _failures.tryEmit(
                intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)?.takeIf { it.isNotBlank() }
                    ?: "The update wasn't installed.",
            )
        }
    }

    companion object {
        private val _failures = MutableSharedFlow<String>(extraBufferCapacity = 4)
        val failures: SharedFlow<String> = _failures.asSharedFlow()

        fun statusSender(context: Context, sessionId: Int): IntentSender {
            val intent = Intent(context, InstallReceiver::class.java).setAction(ACTION_STATUS)
            var flags = PendingIntent.FLAG_UPDATE_CURRENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) flags = flags or PendingIntent.FLAG_MUTABLE
            return PendingIntent.getBroadcast(context, sessionId, intent, flags).intentSender
        }

        private const val ACTION_STATUS = "dev.colorclash.INSTALL_STATUS"
    }
}
