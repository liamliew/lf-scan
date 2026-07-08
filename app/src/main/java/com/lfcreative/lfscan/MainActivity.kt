package com.lfcreative.lfscan

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import com.lfcreative.lfscan.data.repository.InventoryRepository
import com.lfcreative.lfscan.session.SessionDataStore
import com.lfcreative.lfscan.ui.navigation.LFScanNavGraph
import com.lfcreative.lfscan.ui.theme.LFScanTheme
import com.lfcreative.lfscan.ui.theme.ThemeMode
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var sessionDataStore: SessionDataStore
    @Inject lateinit var repository: InventoryRepository

    companion object {
        private const val TAG = "LFScan_Scanner"

        private const val DATAWEDGE_PROFILE_NAME = "LFScanProfile"
        private const val DATAWEDGE_SCAN_ACTION = "com.lfcreative.lfscan.SCAN"
        private const val DATAWEDGE_API_ACTION = "com.symbol.datawedge.api.ACTION"
        private const val DATAWEDGE_EXTRA_SET_CONFIG = "com.symbol.datawedge.api.SET_CONFIG"
        private const val DATAWEDGE_EXTRA_DATA_STRING = "com.symbol.datawedge.data_string"
        private const val DATAWEDGE_EXTRA_DECODE_DATA = "com.symbol.datawedge.decode_data"
        const val DATAWEDGE_NOTIFICATION_ACTION = "com.symbol.datawedge.api.NOTIFICATION_ACTION"
        private const val DATAWEDGE_RESULT_ACTION = "com.symbol.datawedge.api.RESULT_ACTION"

        const val ADMIN_PASSWORD = "422698"
        private const val INACTIVITY_TIMEOUT_MS = 3 * 60 * 1000L

        // Set by ScannerScreen while an external/internal hardware scanner is the active input
        var isScannerScreenActive = false
        var onBarcodeScanned: ((String) -> Unit)? = null

        // Set by PinScreen while it's visible, so a DataWedge scan can authenticate directly
        var isPinScreenActive = false
        var onPinBarcodeScanned: ((String) -> Unit)? = null

        // Set by SerialNumberScanSheet while it's open, so a DataWedge scan fills the serial field
        var isSerialScanActive = false
        var onSerialScanned: ((String) -> Unit)? = null

        // Live DataWedge scanner state, surfaced in ScannerScreen's status bar (INTERNAL only)
        var scannerStatus = MutableStateFlow("UNKNOWN")
        var lastScannedData = MutableStateFlow("")

        // Emits the failed mode (e.g. "MULTI") when a SET_SCAN_MODE_* command comes back FAILURE
        val scanModeFailure = MutableSharedFlow<String>(extraBufferCapacity = 4)

        // Set once by LFScanNavGraph's root composable: pops the back stack to the PIN screen
        // (which tears down every nested-graph ViewModel, so this doubles as "clear all state").
        var onSessionTimeout: (() -> Unit)? = null

        // Set once by LFScanNavGraph's root composable: pushes the Continue Session lock screen
        // on top of whatever is currently showing, WITHOUT popping the back stack — this is what
        // lets a re-entered password resume mid-task state (e.g. an in-progress check-in) instead
        // of discarding it like a full logout does.
        var onAutoLock: (() -> Unit)? = null

        // True while Lock Task Mode (Kiosk Mode) is engaged — read by the root composable's
        // BackHandler to decide whether to intercept back-presses with the admin unlock dialog.
        val isKioskModeActive = MutableStateFlow(false)
        val showAdminUnlockDialog = MutableStateFlow(false)
    }

    private val inactivityHandler = Handler(Looper.getMainLooper())
    private val inactivityRunnable = Runnable { lockSession() }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) lockSession()
        }
    }

    // Receives barcode scans, scanner status notifications, and command results broadcast by DataWedge
    private val scanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            when (action) {
                DATAWEDGE_SCAN_ACTION -> handleScanIntent(intent)
                DATAWEDGE_NOTIFICATION_ACTION -> handleNotificationIntent(intent)
                DATAWEDGE_RESULT_ACTION -> handleResultIntent(intent)
            }
        }

        @Suppress("DEPRECATION")
        private fun handleScanIntent(intent: Intent) {
            val decodeData = intent.getSerializableExtra(DATAWEDGE_EXTRA_DECODE_DATA) as? ArrayList<*>
            if (decodeData != null && decodeData.size > 1) {
                decodeData.forEach { item ->
                    if (item is Bundle) {
                        val barcode = item.getString(DATAWEDGE_EXTRA_DATA_STRING)?.trim()
                        if (!barcode.isNullOrEmpty() && isScannerScreenActive) {
                            Log.d(TAG, "Multi-barcode intent: $barcode")
                            lastScannedData.value = barcode
                            onBarcodeScanned?.invoke(barcode)
                        }
                    }
                }
                return
            }

            val barcode = intent.getStringExtra(DATAWEDGE_EXTRA_DATA_STRING)?.trim()
            if (barcode.isNullOrEmpty()) return

            when {
                isSerialScanActive -> {
                    Log.d(TAG, "Serial scan intent: $barcode")
                    onSerialScanned?.invoke(barcode)
                }
                isScannerScreenActive -> {
                    Log.d(TAG, "Scanner intent: $barcode")
                    lastScannedData.value = barcode
                    onBarcodeScanned?.invoke(barcode)
                }
                isPinScreenActive -> {
                    Log.d(TAG, "PIN intent: $barcode")
                    onPinBarcodeScanned?.invoke(barcode)
                }
            }
        }

        private fun handleNotificationIntent(intent: Intent) {
            val type = intent.getStringExtra("com.symbol.datawedge.api.NOTIFICATION_TYPE")
            if (type == "SCANNER_STATUS") {
                val status = intent.getStringExtra("com.symbol.datawedge.api.NOTIFICATION")
                scannerStatus.value = status ?: "UNKNOWN"
            }
        }

        private fun handleResultIntent(intent: Intent) {
            val commandId = intent.getStringExtra("COMMAND_IDENTIFIER") ?: return
            val result = intent.getStringExtra("RESULT")
            if (!commandId.startsWith("SET_SCAN_MODE_") || result != "FAILURE") return
            scanModeFailure.tryEmit(commandId.removePrefix("SET_SCAN_MODE_"))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        configureDataWedge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF), Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(screenReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
        }

        // Kiosk Mode is a per-employee setting; re-apply it for whoever was last logged in
        // (SessionDataStore persists across process death) even before they re-enter their PIN,
        // so a kiosk device stays pinned across restarts rather than briefly exposing the launcher.
        lifecycleScope.launch {
            val member = sessionDataStore.currentMember.first()
            val kioskSetting = member?.let {
                try { repository.getEmployeeSetting(it.id, "kiosk_mode") } catch (_: Exception) { null }
            }
            if (kioskSetting == "enabled") {
                isKioskModeActive.value = true
                startLockTask()
            }
        }

        setContent {
            val themeModeSetting by sessionDataStore.themeMode.collectAsState(initial = "system")
            val themeMode = when (themeModeSetting) {
                "light" -> ThemeMode.LIGHT
                "dark" -> ThemeMode.DARK
                else -> ThemeMode.SYSTEM
            }
            LFScanTheme(themeMode = themeMode) {
                LFScanNavGraph()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter().apply {
            addAction(DATAWEDGE_SCAN_ACTION)
            addAction(DATAWEDGE_NOTIFICATION_ACTION)
            addAction(DATAWEDGE_RESULT_ACTION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(scanReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(scanReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(scanReceiver)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(screenReceiver)
    }

    // Fires on any touch, key press, or motion event delivered to the window.
    override fun onUserInteraction() {
        super.onUserInteraction()
        resetInactivityTimer()
    }

    // Re-arms the 3-minute auto-logout countdown. A no-op while the PIN screen is showing,
    // since there's no active session to time out yet.
    fun resetInactivityTimer() {
        inactivityHandler.removeCallbacks(inactivityRunnable)
        if (!isPinScreenActive) {
            inactivityHandler.postDelayed(inactivityRunnable, INACTIVITY_TIMEOUT_MS)
        }
    }

    fun stopInactivityTimer() {
        inactivityHandler.removeCallbacks(inactivityRunnable)
    }

    // Logs the current employee out: clears the persisted session, clears all in-app scan state
    // (via onSessionTimeout's pop-to-PIN, which tears down the scan flow's nested ViewModels),
    // and returns to the PIN screen. Lock Task Mode is left engaged if Kiosk Mode is on — this
    // is what makes returning to the PIN screen work as a same-app "screen lock" instead of
    // exposing the launcher.
    fun logoutUser() {
        stopInactivityTimer()
        lifecycleScope.launch { sessionDataStore.clearSession() }
        onSessionTimeout?.invoke()
    }

    // Auto-lock triggered by inactivity or the screen turning off — unlike logoutUser(), the
    // session is left intact so the Continue Session screen can show who was last signed in and
    // re-validate their password, then resume exactly where they left off (nothing is popped off
    // the back stack). No-ops if there's no session to lock (e.g. screen-off firing while still
    // on the PIN screen) or if a lock/PIN screen is already showing.
    fun lockSession() {
        stopInactivityTimer()
        if (isPinScreenActive) return
        lifecycleScope.launch {
            if (sessionDataStore.currentMember.first() != null) {
                onAutoLock?.invoke()
            }
        }
    }

    // Admin password verified while trying to exit Lock Task Mode: actually releases the pin
    // and logs out (Kiosk Mode itself is left enabled in Settings for next launch).
    fun unlockKiosk() {
        isKioskModeActive.value = false
        stopLockTask()
        logoutUser()
    }

    // Configures the device's DataWedge profile to deliver scans as broadcast intents
    // instead of injected keystrokes, so scanner input never touches the IME or a focused view.
    private fun configureDataWedge() {
        sendDataWedgeConfig(
            Bundle().apply {
                putString("PLUGIN_NAME", "KEYSTROKE")
                putString("RESET_CONFIG", "true")
                putBundle("PARAM_LIST", Bundle().apply {
                    putString("keystroke_output_enabled", "false")
                })
            }
        )
        sendDataWedgeConfig(
            Bundle().apply {
                putString("PLUGIN_NAME", "INTENT")
                putString("RESET_CONFIG", "true")
                putBundle("PARAM_LIST", Bundle().apply {
                    putString("intent_output_enabled", "true")
                    putString("intent_action", DATAWEDGE_SCAN_ACTION)
                    putString("intent_delivery", "2") // Broadcast
                })
            }
        )
    }

    private fun sendDataWedgeConfig(pluginConfig: Bundle) {
        val profileConfig = Bundle().apply {
            putString("PROFILE_NAME", DATAWEDGE_PROFILE_NAME)
            putString("PROFILE_ENABLED", "true")
            putString("CONFIG_MODE", "UPDATE")
            putBundle("PLUGIN_CONFIG", pluginConfig)
            putParcelableArrayList("APP_LIST", arrayListOf(
                Bundle().apply {
                    putString("PACKAGE_NAME", packageName)
                    putStringArray("ACTIVITY_LIST", arrayOf("*"))
                }
            ))
        }
        sendBroadcast(
            Intent(DATAWEDGE_API_ACTION).apply {
                putExtra(DATAWEDGE_EXTRA_SET_CONFIG, profileConfig)
            }
        )
    }
}
