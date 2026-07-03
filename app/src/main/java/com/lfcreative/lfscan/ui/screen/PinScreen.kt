package com.lfcreative.lfscan.ui.screen

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.nfc.NfcAdapter
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.util.Consumer
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun PinScreen(
    onSuccess: () -> Unit,
    viewModel: PinViewModel = hiltViewModel()
) {
    val activity = LocalContext.current as ComponentActivity
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val nfcManager = remember { NfcManager(activity as Activity) }
    val vibrationManager = remember { VibrationManager(activity) }
    val snackbarHostState = remember { SnackbarHostState() }

    val uiState by viewModel.uiState.collectAsState()
    var pin by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }
    var showBarcodeScanner by remember { mutableStateOf(false) }
    var showNfcDialog by remember { mutableStateOf(false) }
    // Tracks which auth method was last used so failure handling can differ
    var authMethod by remember { mutableStateOf("pin") }
    val shakeOffset = remember { Animatable(0f) }

    // PendingIntent delivered back to this activity when an NFC tag is scanned
    val nfcPendingIntent = remember {
        val i = Intent(activity, activity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        else
            PendingIntent.FLAG_UPDATE_CURRENT
        PendingIntent.getActivity(activity, 0, i, flags)
    }
    val nfcTechLists = remember {
        arrayOf(
            arrayOf(android.nfc.tech.NfcA::class.java.name),
            arrayOf(android.nfc.tech.NfcB::class.java.name),
            arrayOf(android.nfc.tech.NfcF::class.java.name),
            arrayOf(android.nfc.tech.NfcV::class.java.name),
            arrayOf(android.nfc.tech.Ndef::class.java.name),
            arrayOf(android.nfc.tech.NdefFormatable::class.java.name)
        )
    }

    // Keep a snapshot-stable reference to showNfcDialog for use inside lambdas
    val currentShowNfcDialog = rememberUpdatedState(showNfcDialog)

    // ── NFC foreground dispatch — enable only while dialog is visible ──────
    DisposableEffect(showNfcDialog, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    if (showNfcDialog) {
                        nfcManager.enableForegroundDispatch(nfcPendingIntent, null, nfcTechLists)
                    }
                }
                Lifecycle.Event.ON_PAUSE -> nfcManager.disableForegroundDispatch()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        // If already resumed when the dialog opens, enable immediately
        if (showNfcDialog &&
            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            nfcManager.enableForegroundDispatch(nfcPendingIntent, null, nfcTechLists)
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            nfcManager.disableForegroundDispatch()
        }
    }

    // ── NFC intent listener (Activity.onNewIntent → here) ─────────────────
    DisposableEffect(Unit) {
        val listener = Consumer<Intent> { intent ->
            if (!currentShowNfcDialog.value) return@Consumer
            val isNfcAction = intent.action in listOf(
                NfcAdapter.ACTION_NDEF_DISCOVERED,
                NfcAdapter.ACTION_TECH_DISCOVERED,
                NfcAdapter.ACTION_TAG_DISCOVERED
            )
            if (!isNfcAction) return@Consumer

            val text = nfcManager.readNdefText(intent)
            if (text != null && text.isNotBlank()) {
                authMethod = "nfc"
                viewModel.confirmPin(text.trim())
            } else {
                scope.launch {
                    vibrationManager.unknownScan()
                    SoundManager.playUnknownScan()
                    snackbarHostState.showSnackbar("Unrecognised tag format")
                }
            }
        }
        activity.addOnNewIntentListener(listener)
        onDispose { activity.removeOnNewIntentListener(listener) }
    }

    // ── Auth result handling ───────────────────────────────────────────────
    LaunchedEffect(uiState) {
        when (uiState) {
            is PinUiState.Success -> {
                if (authMethod != "pin") {
                    vibrationManager.goodScan()
                    SoundManager.playGoodScan()
                }
                onSuccess()
            }
            is PinUiState.InvalidPin -> {
                if (authMethod == "pin") {
                    repeat(4) {
                        shakeOffset.animateTo(14f, animationSpec = tween(50))
                        shakeOffset.animateTo(-14f, animationSpec = tween(50))
                    }
                    shakeOffset.animateTo(0f, animationSpec = tween(50))
                    pin = ""
                    showError = true
                    delay(2000)
                    showError = false
                } else {
                    // Barcode/NFC failure — snackbar; NFC keeps dialog open for retry
                    vibrationManager.unknownScan()
                    SoundManager.playUnknownScan()
                    snackbarHostState.showSnackbar("Invalid credentials")
                }
                authMethod = "pin"
                viewModel.resetState()
            }
            is PinUiState.Error -> {
                val msg = (uiState as PinUiState.Error).message
                if (authMethod == "pin") {
                    repeat(4) {
                        shakeOffset.animateTo(14f, animationSpec = tween(50))
                        shakeOffset.animateTo(-14f, animationSpec = tween(50))
                    }
                    shakeOffset.animateTo(0f, animationSpec = tween(50))
                    pin = ""
                    showError = true
                    delay(2000)
                    showError = false
                } else {
                    snackbarHostState.showSnackbar(msg)
                }
                authMethod = "pin"
                viewModel.resetState()
            }
            else -> {}
        }
    }

    // ── Content ────────────────────────────────────────────────────────────
    Box(modifier = Modifier.fillMaxSize()) {
        if (showBarcodeScanner) {
            LoginBarcodeScanner(
                onCancel = { showBarcodeScanner = false },
                onCodeDetected = { code ->
                    showBarcodeScanner = false
                    authMethod = "barcode"
                    viewModel.confirmPin(code)
                }
            )
        } else {
            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) }
            ) { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    if (uiState is PinUiState.Loading) {
                        CircularProgressIndicator()
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(32.dp),
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Text("LF Scan", fontSize = 28.sp, fontWeight = FontWeight.Bold)

                            // 4-cell PIN display
                            Row(
                                modifier = Modifier.offset {
                                    IntOffset(shakeOffset.value.roundToInt(), 0)
                                },
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                repeat(4) { index ->
                                    Box(
                                        modifier = Modifier
                                            .size(52.dp)
                                            .border(
                                                width = 2.dp,
                                                color = when {
                                                    showError -> Color(0xFFEF4444)
                                                    index < pin.length -> Color(0xFF374151)
                                                    else -> Color(0xFFD1D5DB)
                                                },
                                                shape = RoundedCornerShape(10.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (index < pin.length) {
                                            Box(
                                                modifier = Modifier
                                                    .size(14.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFF111827))
                                            )
                                        }
                                    }
                                }
                            }

                            if (showError) {
                                Text(
                                    text = if (uiState is PinUiState.Error)
                                        (uiState as PinUiState.Error).message
                                    else "Invalid PIN",
                                    color = Color(0xFFEF4444),
                                    fontSize = 14.sp
                                )
                            } else {
                                Spacer(Modifier.height(20.dp))
                            }

                            PinNumpad(
                                onDigit = { digit -> if (pin.length < 4) pin += digit },
                                onBackspace = { if (pin.isNotEmpty()) pin = pin.dropLast(1) },
                                onConfirm = {
                                    if (pin.length == 4) {
                                        authMethod = "pin"
                                        viewModel.confirmPin(pin)
                                    }
                                }
                            )

                            // ── Alternative auth buttons ───────────────
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedButton(
                                    onClick = { showBarcodeScanner = true },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        Icons.Default.QrCodeScanner,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("Scan Barcode")
                                }

                                if (nfcManager.isNfcSupported) {
                                    OutlinedButton(
                                        onClick = {
                                            if (!nfcManager.isNfcEnabled) {
                                                scope.launch {
                                                    val result = snackbarHostState.showSnackbar(
                                                        message = "Please enable NFC in settings",
                                                        actionLabel = "Open Settings"
                                                    )
                                                    if (result == SnackbarResult.ActionPerformed) {
                                                        activity.startActivity(
                                                            Intent(Settings.ACTION_NFC_SETTINGS)
                                                        )
                                                    }
                                                }
                                            } else {
                                                showNfcDialog = true
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            Icons.Default.Nfc,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text("Scan NFC")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (showNfcDialog) {
                NfcScanDialog(onDismiss = { showNfcDialog = false })
            }
        }
    }
}

// ── Login barcode scanner ─────────────────────────────────────────────────────

@Composable
private fun LoginBarcodeScanner(
    onCancel: () -> Unit,
    onCodeDetected: (String) -> Unit
) {
    var hasCameraPermission by remember { mutableStateOf(false) }
    val onCodeDetectedState = rememberUpdatedState(onCodeDetected)

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        permLauncher.launch(Manifest.permission.CAMERA)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (hasCameraPermission) {
            CameraPreview(
                modifier = Modifier.fillMaxSize(),
                onBarcodeDetected = { code -> onCodeDetectedState.value(code) }
            )
            // Scanning reticle
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(240.dp, 140.dp)
                        .border(2.dp, Color.White, RoundedCornerShape(8.dp))
                )
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Camera permission required", color = Color.White)
            }
        }

        // Cancel — top left
        IconButton(
            onClick = onCancel,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Cancel",
                tint = Color.White
            )
        }
    }
}

// ── NFC scan dialog ───────────────────────────────────────────────────────────

@Composable
private fun NfcScanDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Nfc,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = Color(0xFF374151)
            )
        },
        title = {
            Text("Scan NFC", fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        },
        text = {
            Text(
                "Hold your card or tag to the back of your phone",
                textAlign = TextAlign.Center,
                color = Color(0xFF6B7280),
                fontSize = 14.sp
            )
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// ── Numpad ────────────────────────────────────────────────────────────────────

@Composable
private fun PinNumpad(
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onConfirm: () -> Unit
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("back", "0", "ok")
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { key ->
                    PinKey(
                        key = key,
                        onDigit = onDigit,
                        onBackspace = onBackspace,
                        onConfirm = onConfirm
                    )
                }
            }
        }
    }
}

@Composable
private fun PinKey(
    key: String,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onConfirm: () -> Unit
) {
    val keySize = 72.dp
    when (key) {
        "back" -> FilledTonalIconButton(
            onClick = onBackspace,
            modifier = Modifier.size(keySize)
        ) {
            Icon(Icons.Default.Backspace, contentDescription = "Delete")
        }
        "ok" -> FilledIconButton(
            onClick = onConfirm,
            modifier = Modifier.size(keySize)
        ) {
            Icon(Icons.Default.Check, contentDescription = "Confirm")
        }
        else -> FilledTonalButton(
            onClick = { onDigit(key) },
            modifier = Modifier.size(keySize),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(0.dp)
        ) {
            Text(key, fontSize = 22.sp, fontWeight = FontWeight.Medium)
        }
    }
}
