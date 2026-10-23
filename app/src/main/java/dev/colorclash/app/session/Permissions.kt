package dev.colorclash.app.session

import android.Manifest
import android.os.Build

object Permissions {
    private const val ANDROID_17 = 37

    /** Needed before talking to other phones. Only Android 17+ asks the user for this. */
    fun network(): List<String> = buildList {
        if (Build.VERSION.SDK_INT >= ANDROID_17) add(Manifest.permission.ACCESS_LOCAL_NETWORK)
    }

    /** Hosting also shows an ongoing notification while the server runs. */
    fun hosting(): List<String> = network() + buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
    }
}
