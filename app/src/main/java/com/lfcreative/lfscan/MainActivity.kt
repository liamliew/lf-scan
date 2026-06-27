package com.lfcreative.lfscan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.lfcreative.lfscan.ui.navigation.LFScanNavGraph
import com.lfcreative.lfscan.ui.theme.LFScanTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LFScanTheme {
                LFScanNavGraph()
            }
        }
    }
}
