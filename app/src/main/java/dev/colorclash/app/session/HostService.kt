package dev.colorclash.app.session

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import dev.colorclash.app.MainActivity
import dev.colorclash.app.R

/**
 * Keeps the host's process (and the game server living in it) alive with the screen off.
 * The server itself is owned by [SessionManager]; this only holds the foreground slot and the locks.
 */
class HostService : Service() {
    private val wifiLocks = mutableListOf<WifiManager.WifiLock>()
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val roomCode = intent?.getStringExtra(EXTRA_ROOM).orEmpty()
        val notification = buildNotification(roomCode)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        acquireLocks()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        wifiLocks.forEach { if (it.isHeld) it.release() }
        wifiLocks.clear()
        wakeLock?.takeIf { it.isHeld }?.release()
        super.onDestroy()
    }

    /**
     * LOW_LATENCY only counts while the screen is on and we're in front. HIGH_PERF covers screen-off
     * up to Android 13; from 14 it does nothing and the foreground service has to carry it alone.
     */
    @Suppress("DEPRECATION")
    private fun acquireLocks() {
        if (wifiLocks.isEmpty()) {
            val wifi = applicationContext.getSystemService(WifiManager::class.java)
            val modes = buildList {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(WifiManager.WIFI_MODE_FULL_LOW_LATENCY)
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) add(WifiManager.WIFI_MODE_FULL_HIGH_PERF)
            }
            modes.forEach { mode ->
                wifiLocks += wifi.createWifiLock(mode, "ColorClash:host:$mode").apply {
                    setReferenceCounted(false)
                    acquire()
                }
            }
        }
        if (wakeLock == null) {
            val power = getSystemService(PowerManager::class.java)
            wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ColorClash:host").apply {
                setReferenceCounted(false)
                acquire(MAX_HOLD_MILLIS)
            }
        }
    }

    private fun buildNotification(roomCode: String): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.host_channel_name), NotificationManager.IMPORTANCE_LOW),
        )
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_cards)
            .setContentTitle(getString(R.string.host_notification_title))
            .setContentText(getString(R.string.host_notification_text, roomCode))
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "hosting"
        private const val NOTIFICATION_ID = 1
        private const val EXTRA_ROOM = "room"
        private const val MAX_HOLD_MILLIS = 4L * 60 * 60 * 1000

        fun start(context: Context, roomCode: String) {
            context.startForegroundService(Intent(context, HostService::class.java).putExtra(EXTRA_ROOM, roomCode))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, HostService::class.java))
        }
    }
}
