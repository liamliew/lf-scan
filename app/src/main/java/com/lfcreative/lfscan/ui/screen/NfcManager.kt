package com.lfcreative.lfscan.ui.screen

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter

class NfcManager(private val activity: Activity) {

    private val nfcAdapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(activity)

    val isNfcSupported: Boolean get() = nfcAdapter != null
    val isNfcEnabled: Boolean get() = nfcAdapter?.isEnabled == true

    fun enableForegroundDispatch(
        pendingIntent: PendingIntent,
        filters: Array<IntentFilter>?,
        techLists: Array<Array<String>>?
    ) {
        nfcAdapter?.enableForegroundDispatch(activity, pendingIntent, filters, techLists)
    }

    fun disableForegroundDispatch() {
        try {
            nfcAdapter?.disableForegroundDispatch(activity)
        } catch (_: Exception) {
            // Ignore if called outside onPause/onResume window
        }
    }

    /**
     * Extracts a text string from the first NDEF record in the given intent.
     * Returns null if the intent carries no NDEF data.
     */
    @Suppress("DEPRECATION")
    fun readNdefText(intent: Intent): String? {
        val rawMessages = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
            ?: return null
        val messages = rawMessages.map { it as NdefMessage }
        val firstRecord = messages.firstOrNull()?.records?.firstOrNull() ?: return null

        return when {
            // Standard NDEF text record (TNF_WELL_KNOWN + RTD_TEXT)
            firstRecord.tnf == NdefRecord.TNF_WELL_KNOWN &&
            firstRecord.type.contentEquals(NdefRecord.RTD_TEXT) -> {
                val payload = firstRecord.payload
                val langCodeLen = payload[0].toInt() and 0x3F
                val encoding = if ((payload[0].toInt() and 0x80) != 0) Charsets.UTF_16 else Charsets.UTF_8
                String(payload, 1 + langCodeLen, payload.size - 1 - langCodeLen, encoding)
            }
            // Fallback: return raw payload as string
            else -> String(firstRecord.payload).trim()
        }
    }
}
