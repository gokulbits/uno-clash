package dev.colorclash.app.feedback

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import dev.colorclash.app.data.FeedbackSettings
import kotlinx.coroutines.flow.StateFlow

enum class Buzz { TICK, BUMP, HEAVY, SUCCESS }

class Haptics(context: Context, private val settings: StateFlow<FeedbackSettings>) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }

    fun buzz(kind: Buzz) {
        if (!settings.value.haptics) return
        val v = vibrator?.takeIf { it.hasVibrator() } ?: return
        val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            when (kind) {
                Buzz.TICK -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                Buzz.BUMP -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                Buzz.HEAVY -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                Buzz.SUCCESS -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
            }
        } else {
            when (kind) {
                Buzz.TICK -> VibrationEffect.createOneShot(10, 80)
                Buzz.BUMP -> VibrationEffect.createOneShot(20, 160)
                Buzz.HEAVY -> VibrationEffect.createOneShot(60, 255)
                Buzz.SUCCESS -> VibrationEffect.createWaveform(longArrayOf(0, 30, 60, 30), -1)
            }
        }
        v.vibrate(effect)
    }
}
