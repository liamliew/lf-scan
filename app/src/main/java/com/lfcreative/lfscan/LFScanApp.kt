package com.lfcreative.lfscan

import android.app.Application
import com.lfcreative.lfscan.ui.screen.SoundManager
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class LFScanApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SoundManager.init(this)
    }
}
