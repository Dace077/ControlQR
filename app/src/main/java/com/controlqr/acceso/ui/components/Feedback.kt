package com.controlqr.acceso.ui.components

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Aviso sonoro y háptico al escanear.
 *
 * En una caseta el vigilante casi nunca está viendo la pantalla cuando acerca el código:
 * el tono y la vibración son la confirmación real de que el pase pasó o fue rechazado.
 */
object ScanFeedback {

    fun granted(context: Context) {
        beep(ToneGenerator.TONE_PROP_BEEP, 180)
        vibrate(context, longArrayOf(0, 60))
    }

    fun denied(context: Context) {
        beep(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 600)
        vibrate(context, longArrayOf(0, 180, 120, 180))
    }

    private fun beep(tone: Int, durationMs: Int) {
        runCatching {
            val generator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90)
            generator.startTone(tone, durationMs)
            android.os.Handler(android.os.Looper.getMainLooper())
                .postDelayed({ runCatching { generator.release() } }, (durationMs + 200).toLong())
        }
    }

    private fun vibrate(context: Context, pattern: LongArray) {
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                manager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, -1)
            }
        }
    }
}
