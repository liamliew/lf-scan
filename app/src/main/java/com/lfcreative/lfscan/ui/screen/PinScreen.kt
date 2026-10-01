package com.lfcreative.lfscan.ui.screen

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.nfc.NfcAdapter
import android.os.Build
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import com.lfcreative.lfscan.ui.theme.LocalExtendedColors
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.util.Consumer
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lfcreative.lfscan.MainActivity
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
    val isOnline by viewModel.isOnline.collectAsState()
    // Step 1 = ID entry, Step 2 = password entry (skipped entirely if the member has no password)
    var step by remember { mutableStateOf(1) }
    var id by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var memberName by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf("") }
    var showScanTypeChooser by remember { mutableStateOf(false) }
    // Which barcode input method is active: null (none), "camera", or "scanner" (hardware wedge)
    var loginScanMode by remember { mutableStateOf<String?>(null) }
    var showNfcDialog by remember { mutableStateOf(false) }
    // Tracks which auth method was last used so failure handling can differ (Step 1 only)
    var authMethod by remember { mutableStateOf("id") }
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
            if (!text.isNullOrBlank()) {
                authMethod = "nfc"
                viewModel.submitId(text.trim())
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

    // ── DataWedge intent scanning — additional input method alongside the camera
    // scanner above. Registers with MainActivity while this screen is visible. The same
    // physical trigger feeds Step 1's ID field or Step 2's password field depending on
    // whichever step is currently showing, since only one handler can be registered at a time.
    val currentStep = rememberUpdatedState(step)
    DisposableEffect(Unit) {
        MainActivity.isPinScreenActive = true
        MainActivity.onPinBarcodeScanned = { scannedValue ->
            if (currentStep.value == 1) {
                authMethod = "barcode"
                viewModel.submitId(scannedValue)
            } else {
                viewModel.setPasswordFromScan(scannedValue)
            }
        }
        onDispose {
            MainActivity.isPinScreenActive = false
            MainActivity.onPinBarcodeScanned = null
        }
    }

    // ── Auth result handling ───────────────────────────────────────────────
    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is PinUiState.AwaitingPassword -> {
                memberName = state.member.name
                step = 2
            }
            is PinUiState.Success -> {
                if (authMethod != "id") {
                    vibrationManager.goodScan()
                    SoundManager.playGoodScan()
                }
                (activity as? MainActivity)?.resetInactivityTimer()
                onSuccess()
            }
            is PinUiState.InvalidId -> {
                if (authMethod == "id") {
                    repeat(4) {
                        shakeOffset.animateTo(14f, animationSpec = tween(50))
                        shakeOffset.animateTo(-14f, animationSpec = tween(50))
                    }
                    shakeOffset.animateTo(0f, animationSpec = tween(50))
                    id = ""
                    errorText = "ID not found"
                    showError = true
                    delay(2000)
                    showError = false
                } else {
                    // Barcode/NFC failure — snackbar; NFC keeps dialog open for retry
                    vibrationManager.unknownScan()
                    SoundManager.playUnknownScan()
                    snackbarHostState.showSnackbar("Invalid credentials")
                }
                authMethod = "id"
                viewModel.resetState()
            }
            is PinUiState.InvalidPassword -> {
                repeat(4) {
                    shakeOffset.animateTo(14f, animationSpec = tween(50))
                    shakeOffset.animateTo(-14f, animationSpec = tween(50))
                }
                shakeOffset.animateTo(0f, animationSpec = tween(50))
                password = ""
                errorText = "Incorrect password"
                showError = true
                delay(2000)
                showError = false
                viewModel.resetState()
            }
            is PinUiState.Error -> {
                val msg = state.message
                if (step == 1 && authMethod != "id") {
                    vibrationManager.unknownScan()
                    SoundManager.playUnknownScan()
                    snackbarHostState.showSnackbar(msg)
                } else {
                    repeat(4) {
                        shakeOffset.animateTo(14f, animationSpec = tween(50))
                        shakeOffset.animateTo(-14f, animationSpec = tween(50))
                    }
                    shakeOffset.animateTo(0f, animationSpec = tween(50))
                    if (step == 1) id = "" else password = ""
                    errorText = msg
                    showError = true
                    delay(2000)
                    showError = false
                }
                authMethod = "id"
                viewModel.resetState()
            }
            else -> {}
        }
    }

    // ── Content ────────────────────────────────────────────────────────────
    Box(modifier = Modifier.fillMaxSize()) {
        if (loginScanMode != null) {
            when (loginScanMode) {
                "camera" -> LoginBarcodeScanner(
                    onCancel = { loginScanMode = null },
                    onCodeDetected = { code ->
                        loginScanMode = null
                        authMethod = "barcode"
                        viewModel.submitId(code)
                    }
                )
                "scanner" -> LoginHardwareScanner(
                    onCancel = { loginScanMode = null },
                    onCodeDetected = { code ->
                        loginScanMode = null
                        authMethod = "barcode"
                        viewModel.submitId(code)
                    }
                )
            }
        } else {
            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) }
            ) { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.Center
                ) {
                    if (uiState is PinUiState.Loading) {
                        CircularProgressIndicator()
                    } else {
                        AnimatedContent(
                            targetState = step,
                            transitionSpec = {
                                (slideInVertically(animationSpec = tween(200)) { height -> height / 3 } +
                                    fadeIn(animationSpec = tween(200))) togetherWith
                                    (slideOutVertically(animationSpec = tween(200)) { height -> -height / 3 } +
                                        fadeOut(animationSpec = tween(200)))
                            },
                            label = "pinStep"
                        ) { targetStep ->
                            if (targetStep == 1) {
                                IdStepContent(
                                    isOnline = isOnline,
                                    id = id,
                                    showError = showError,
                                    errorText = errorText,
                                    shakeOffset = shakeOffset,
                                    onDigit = { digit -> if (id.length < 4) id += digit },
                                    onBackspace = { if (id.isNotEmpty()) id = id.dropLast(1) },
                                    onConfirm = {
                                        if (id.length == 4) {
                                            authMethod = "id"
                                            viewModel.submitId(id)
                                        }
                                    },
                                    onScanBarcode = { showScanTypeChooser = true },
                                    nfcSupported = nfcManager.isNfcSupported,
                                    onScanNfc = {
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
                                    }
                                )
                            } else {
                                PasswordStepContent(
                                    isOnline = isOnline,
                                    memberName = memberName,
                                    password = password,
                                    showError = showError,
                                    errorText = errorText,
                                    shakeOffset = shakeOffset,
                                    onDigit = { digit -> password += digit },
                                    onBackspace = { if (password.isNotEmpty()) password = password.dropLast(1) },
                                    onConfirm = {
                                        if (password.isNotEmpty()) viewModel.submitPassword(password)
                                    },
                                    onBack = {
                                        step = 1
                                        password = ""
                                        showError = false
                                        viewModel.backToIdStep()
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (showNfcDialog) {
                NfcScanDialog(onDismiss = { showNfcDialog = false })
            }

            if (showScanTypeChooser) {
                ScanTypeChooserDialog(
                    onDismiss = { showScanTypeChooser = false },
                    onCameraSelected = {
                        showScanTypeChooser = false
                        loginScanMode = "camera"
                    },
                    onScannerSelected = {
                        showScanTypeChooser = false
                        loginScanMode = "scanner"
                    }
                )
            }
        }
    }
}

// ── Step 1: ID entry ───────────────────────────────────────────────────────────

@Composable
private fun IdStepContent(
    isOnline: Boolean,
    id: String,
    showError: Boolean,
    errorText: String,
    shakeOffset: Animatable<Float, AnimationVector1D>,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onConfirm: () -> Unit,
    onScanBarcode: () -> Unit,
    nfcSupported: Boolean,
    onScanNfc: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(32.dp),
        modifier = Modifier.padding(24.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("LFC Asset Services", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Enter your ID", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!isOnline) {
                Spacer(Modifier.height(10.dp))
                Surface(
                    color = LocalExtendedColors.current.amber.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(0.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudOff,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = LocalExtendedColors.current.amber
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Offline Mode Enabled",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = LocalExtendedColors.current.amber
                        )
                    }
                }
            }
        }

        // 4-cell ID display
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
                                showError -> LocalExtendedColors.current.red
                                index < id.length -> MaterialTheme.colorScheme.onSurface
                                else -> MaterialTheme.colorScheme.outline
                            },
                            shape = RoundedCornerShape(10.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (index < id.length) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onSurface)
                        )
                    }
                }
            }
        }

        if (showError) {
            Text(errorText, color = LocalExtendedColors.current.red, fontSize = 14.sp)
        } else {
            Spacer(Modifier.height(20.dp))
        }

        PinNumpad(onDigit = onDigit, onBackspace = onBackspace, onConfirm = onConfirm)

        // ── Alternative auth buttons ───────────────
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onScanBarcode,
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

            if (nfcSupported) {
                OutlinedButton(
                    onClick = onScanNfc,
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

// ── Step 2: password entry ─────────────────────────────────────────────────────

@Composable
private fun PasswordStepContent(
    isOnline: Boolean,
    memberName: String,
    password: String,
    showError: Boolean,
    errorText: String,
    shakeOffset: Animatable<Float, AnimationVector1D>,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onConfirm: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.padding(24.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Welcome, $memberName", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("Enter your password", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!isOnline) {
                Spacer(Modifier.height(10.dp))
                Surface(
                    color = LocalExtendedColors.current.amber.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(0.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudOff,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = LocalExtendedColors.current.amber
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Offline Mode Enabled",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = LocalExtendedColors.current.amber
                        )
                    }
                }
            }
        }

        OutlinedTextField(
            value = password,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            isError = showError,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .offset { IntOffset(shakeOffset.value.roundToInt(), 0) }
        )

        if (showError) {
            Text(errorText, color = LocalExtendedColors.current.red, fontSize = 14.sp)
        } else {
            Spacer(Modifier.height(20.dp))
        }

        PinNumpad(onDigit = onDigit, onBackspace = onBackspace, onConfirm = onConfirm)

        TextButton(onClick = onBack) { Text("← Back") }
    }
}

// ── Login barcode scanner ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Scan to Sign In", color = Color.White, fontWeight = FontWeight.SemiBold)
                },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black),
                windowInsets = WindowInsets.statusBars
            )
        },
        containerColor = Color.Black
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
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
                            .border(2.dp, Color.White, RoundedCornerShape(0.dp))
                    )
                }
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Camera permission required", color = Color.White)
                }
            }
        }
    }
}

// ── Scan type chooser ──────────────────────────────────────────────────────────

@Composable
private fun ScanTypeChooserDialog(
    onDismiss: () -> Unit,
    onCameraSelected: () -> Unit,
    onScannerSelected: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Scan Barcode", fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                ScanTypeOption(
                    icon = Icons.Default.CameraAlt,
                    label = "Camera",
                    onClick = onCameraSelected
                )
                ScanTypeOption(
                    icon = Icons.Default.QrCodeScanner,
                    label = "Scanner",
                    onClick = onScannerSelected
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ScanTypeOption(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
        Text(label, fontSize = 16.sp)
    }
}

// ── Login hardware scanner (external/internal wedge scanner) ──────────────────

@Composable
private fun LoginHardwareScanner(
    onCancel: () -> Unit,
    onCodeDetected: (String) -> Unit
) {
    val activity = LocalContext.current as? Activity
    val focusRequester = remember { FocusRequester() }
    val interactionSource = remember { MutableInteractionSource() }
    var inputBuffer by remember { mutableStateOf("") }
    var isFocused by remember { mutableStateOf(false) }
    val onCodeDetectedState = rememberUpdatedState(onCodeDetected)

    // Suppress the soft keyboard while capturing raw hardware-scanner key events
    DisposableEffect(Unit) {
        val previousMode = activity?.window?.attributes?.softInputMode
        activity?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        onDispose {
            activity?.window?.setSoftInputMode(
                previousMode ?: WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED
            )
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    LaunchedEffect(isFocused) { if (!isFocused) focusRequester.requestFocus() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.QrCodeScanner,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
            Text("Ready to scan", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
            Text(
                "Scan a barcode with your scanner",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }

        // Invisible focusable target that captures hardware scanner input via raw key events.
        // Unlike a text field, a plain focusable never triggers the IME.
        Box(
            modifier = Modifier
                .size(1.dp)
                .alpha(0f)
                .focusRequester(focusRequester)
                .onFocusChanged { state -> isFocused = state.isFocused }
                .focusable(interactionSource = interactionSource)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                        }
                    }
                }
                .onKeyEvent { keyEvent ->
                    if (keyEvent.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (keyEvent.key) {
                        Key.Enter, Key.NumPadEnter -> {
                            val code = inputBuffer.trim()
                            if (code.isNotEmpty()) onCodeDetectedState.value(code)
                            inputBuffer = ""
                        }
                        else -> {
                            val codePoint = keyEvent.utf16CodePoint
                            if (codePoint > 0) {
                                inputBuffer += String(Character.toChars(codePoint))
                            }
                        }
                    }
                    true
                }
        )

        IconButton(
            onClick = onCancel,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Cancel",
                tint = MaterialTheme.colorScheme.onSurface
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
                tint = MaterialTheme.colorScheme.onSurface
            )
        },
        title = {
            Text("Scan NFC", fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        },
        text = {
            Text(
                "Hold your card or tag to the back of your phone",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
internal fun PinNumpad(
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
            Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Delete")
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
            shape = RoundedCornerShape(0.dp),
            contentPadding = PaddingValues(0.dp)
        ) {
            Text(key, fontSize = 22.sp, fontWeight = FontWeight.Medium)
        }
    }
}
