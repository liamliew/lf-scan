package com.lfcreative.lfscan.ui.screen

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class VibrationManager(context: Context) {

    @Suppress("DEPRECATION")
    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java).defaultVibrator
    } else {
        context.getSystemService(Vibrator::class.java)!!
    }

    fun goodScan() {
        vibrator.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    // duplicateScan: no vibration

    fun unknownScan() {
        vibrator.vibrate(
            VibrationEffect.createWaveform(
                longArrayOf(0, 50, 40, 50, 40, 50, 40, 50),
                -1
            )
        )
    }
}
