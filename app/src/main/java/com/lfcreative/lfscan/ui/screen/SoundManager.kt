package com.lfcreative.lfscan.ui.screen

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.lfcreative.lfscan.R

object SoundManager {

    private var soundPool: SoundPool? = null
    private var scanSoundId: Int = 0
    private var failSoundId: Int = 0
    private var successSoundId: Int = 0

    fun init(context: Context) {
        if (soundPool != null) return
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val pool = SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(attributes)
            .build()

        val appContext = context.applicationContext
        scanSoundId = pool.load(appContext, R.raw.scan, 1)
        failSoundId = pool.load(appContext, R.raw.fail, 1)
        successSoundId = pool.load(appContext, R.raw.success, 1)

        soundPool = pool
    }

    /** Successful scan (not duplicate) -> plays scan.mp3 */
    fun playGoodScan(context: Context? = null) {
        context?.let { init(it) }
        val pool = soundPool ?: return
        if (scanSoundId != 0) {
            pool.play(scanSoundId, 1.0f, 1.0f, 1, 0, 1.0f)
        }
    }

    /** Scan that is invalid (not duplicate) -> plays fail.mp3 */
    fun playUnknownScan(context: Context? = null) {
        context?.let { init(it) }
        val pool = soundPool ?: return
        if (failSoundId != 0) {
            pool.play(failSoundId, 1.0f, 1.0f, 1, 0, 1.0f)
        }
    }

    /** Duplicate scan -> NO SOUND (silent) */
    fun playDuplicateScan() {
        // Intentionally silent per requirements
    }

    /** Played after commit button is pressed -> plays success.mp3 */
    fun playSuccessCommit(context: Context? = null) {
        context?.let { init(it) }
        val pool = soundPool ?: return
        if (successSoundId != 0) {
            pool.play(successSoundId, 1.0f, 1.0f, 1, 0, 1.0f)
        }
    }
}
