package com.lfcreative.lfscan.ui.screen

import android.Manifest
import android.content.Context
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lfcreative.lfscan.MainActivity
import com.lfcreative.lfscan.data.model.Asset
import com.lfcreative.lfscan.ui.theme.Amber
import com.lfcreative.lfscan.ui.theme.Blue
import com.lfcreative.lfscan.ui.theme.Green
import com.lfcreative.lfscan.ui.theme.Grey
import com.lfcreative.lfscan.ui.theme.Purple
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    mode: String,
    scannerType: String,
    viewModel: ScanViewModel,
    onBack: () -> Unit,
    onCommitSuccess: (Int) -> Unit,
    onNavigateToAsset: (String) -> Unit,
    locationId: String? = null,
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val currentMember by viewModel.currentMember.collectAsState(initial = null)
    val pendingContainerConfirmation by viewModel.pendingContainerConfirmation.collectAsState()

    var hasCameraPermission by remember { mutableStateOf(false) }
    var hasFineLocation by remember { mutableStateOf(false) }
    var gpsError by remember { mutableStateOf(false) }
    var flashColor by remember { mutableStateOf<Color?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    val modeAccent = modeColor(mode)

    var userTorchEnabled by remember { mutableStateOf(false) }
    val vibrationManager = remember { VibrationManager(context) }
    val dataWedgeManager = remember { DataWedgeManager(context) }

    // DataWedge status bar state (INTERNAL scanner mode only)
    val scannerStatus by MainActivity.scannerStatus.collectAsState()
    val lastScannedData by MainActivity.lastScannedData.collectAsState()
    val scanMode by viewModel.scanMode.collectAsState()

    // ── Internal-scanner-only settings: illumination + soft scan trigger ───────
    var illuminationDefaultSetting by remember { mutableStateOf("on") }
    var illuminationEnabled by remember { mutableStateOf(true) }
    var scanTriggerMode by remember { mutableStateOf("hold") }
    var isScanActive by remember { mutableStateOf(false) }

    LaunchedEffect(currentMember, scannerType) {
        val employeeId = currentMember?.id ?: return@LaunchedEffect
        if (scannerType != "internal") return@LaunchedEffect

        illuminationDefaultSetting = settingsViewModel.getSetting(employeeId, "illumination_default", "on")
        illuminationEnabled = when (illuminationDefaultSetting) {
            "off" -> false
            "last_choice" -> settingsViewModel.getSetting(employeeId, "illumination_last", "true").toBoolean()
            else -> true
        }
        dataWedgeManager.enableIllumination(illuminationEnabled)

        scanTriggerMode = settingsViewModel.getSetting(employeeId, "scan_trigger_mode", "hold")
    }

    // Internal (TC15) scanner input arrives via a DataWedge Intent broadcast received by
    // MainActivity's scanReceiver (configured in MainActivity.configureDataWedge) — not via
    // key events. Wire the callback in while this screen has a hardware-scanner top zone
    // showing so MainActivity knows where to deliver decoded barcodes. This also registers for
    // SCANNER_STATUS notifications and pre-warms the internal scanner (IDLE -> WAITING) on
    // mount, so it's ready before the user touches the trigger.
    //
    // Navigating back from here returns to LocationScanScreen (Check In's location picker),
    // which re-mounts and re-claims these same MainActivity globals via cross-fade — same as
    // the forward hand-off from LocationScanScreen into this screen. So teardown here (stopping
    // the beam, resetting decoders, suspending the scanner, clearing the globals) must also be
    // gated on still owning the handler, or it would clobber LocationScanScreen's fresh
    // registration and leave its trigger looking "enabled" but dead.
    //
    // NOTE: External Bluetooth scanners that emulate keyboard (HID) input are NOT currently
    // supported. That path previously relied on MainActivity.onKeyDown, which was removed in
    // favor of DataWedge Intent Output. Supporting HID scanners again would require
    // reintroducing onKeyDown or a hidden-TextField key-capture approach alongside this one.
    val onHardwareScan = if (mode == "inquiry") viewModel::processInquiryScan else viewModel::processScannedCode
    DisposableEffect(scannerType, onHardwareScan) {
        if (scannerType == "internal") {
            dataWedgeManager.registerForScannerStatus()
            dataWedgeManager.resumeScanner()
        }
        if (scannerType == "external" || scannerType == "internal") {
            MainActivity.onBarcodeScanned = onHardwareScan
            MainActivity.isScannerScreenActive = true
        }
        onDispose {
            if (MainActivity.onBarcodeScanned === onHardwareScan) {
                if (scannerType == "internal") {
                    dataWedgeManager.stopScan()
                    dataWedgeManager.resetScanMode()
                    dataWedgeManager.suspendScanner()
                    dataWedgeManager.unregisterForScannerStatus()
                }
                MainActivity.isScannerScreenActive = false
                MainActivity.onBarcodeScanned = null
            }
        }
    }

    // Permissions
    val cameraPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    val locationPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasFineLocation = granted
        if (!granted) gpsError = true
    }

    LaunchedEffect(Unit) {
        viewModel.setModeAndScannerType(mode, scannerType)
        if (mode == "check_in" && locationId != null) viewModel.setLocation(locationId)
        if (scannerType == "camera") cameraPermLauncher.launch(Manifest.permission.CAMERA)
        if (mode == "update") locationPermLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        if (scannerType == "internal") {
            val savedMode = viewModel.loadSavedScanMode()
            dataWedgeManager.setScanMode(savedMode)
        }
    }

    // GPS tracking for update mode
    DisposableEffect(mode, hasFineLocation) {
        if (mode == "update" && hasFineLocation) {
            val locationManager =
                context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val listener = LocationListener { loc ->
                viewModel.updateGps(loc.latitude, loc.longitude)
            }
            try {
                val gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                val networkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
                gpsError = !gpsEnabled && !networkEnabled
                if (gpsEnabled) {
                    locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER, 5_000L, 5f, listener
                    )
                }
                if (networkEnabled) {
                    locationManager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER, 5_000L, 5f, listener
                    )
                }
            } catch (_: SecurityException) {
                gpsError = true
            }
            onDispose { locationManager.removeUpdates(listener) }
        } else {
            if (mode == "update" && !hasFineLocation) gpsError = true
            onDispose {}
        }
    }

    // Flash effect + audio + vibration feedback (all in parallel)
    LaunchedEffect(state.flash) {
        when (state.flash) {
            ScanFlash.FOUND -> {
                launch(Dispatchers.Default) { vibrationManager.goodScan() }
                launch(Dispatchers.Default) { SoundManager.playGoodScan() }
                flashColor = Color(0x9900C853)
                delay(400)
                flashColor = null
                viewModel.clearFlash()
            }
            ScanFlash.NOT_FOUND -> {
                launch(Dispatchers.Default) { vibrationManager.unknownScan() }
                launch(Dispatchers.Default) { SoundManager.playUnknownScan() }
                flashColor = Color(0x99FF1744)
                delay(400)
                flashColor = null
                viewModel.clearFlash()
            }
            ScanFlash.DUPLICATE -> {
                launch(Dispatchers.Default) { SoundManager.playDuplicateScan() }
                // no vibration, no torch flash for duplicate
                flashColor = Color(0x99FFCA28)
                delay(300)
                flashColor = null
                viewModel.clearFlash()
            }
            ScanFlash.NONE -> {}
        }
    }

    // Snackbar events
    LaunchedEffect(Unit) {
        viewModel.snackbarMessage.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    // Inquiry mode: navigate to asset detail on successful scan
    LaunchedEffect(Unit) {
        viewModel.navigateToAsset.collect { assetId ->
            onNavigateToAsset(assetId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(modeTitle(mode), color = modeAccent, fontWeight = FontWeight.SemiBold)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (scannerType == "internal") {
                        ScanModeButton(
                            currentMode = scanMode,
                            onModeSelected = { newMode ->
                                viewModel.setScanMode(newMode, context)
                            }
                        )
                        IconButton(onClick = {
                            val newState = !illuminationEnabled
                            illuminationEnabled = newState
                            dataWedgeManager.enableIllumination(newState)
                            if (illuminationDefaultSetting == "last_choice") {
                                currentMember?.let { member ->
                                    settingsViewModel.updateSetting(
                                        member.id, "illumination_last", newState.toString()
                                    )
                                }
                            }
                        }) {
                            Icon(
                                if (illuminationEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                contentDescription = if (illuminationEnabled) "Turn illumination off" else "Turn illumination on"
                            )
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (mode != "inquiry") Surface(
                shadowElevation = 8.dp,
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                Column {
                    state.commitError?.let { err ->
                        Text(
                            err,
                            color = Color(0xFFEF4444),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }

                    // Compact status bar — check_in, check_out, update, rent_out
                    if (mode == "check_in" || mode == "check_out" || mode == "update" || mode == "rent_out") {
                        ScannerStatusBar(
                            mode = mode,
                            scannerType = scannerType,
                            gpsLat = state.gpsLat,
                            gpsError = gpsError,
                            validCount = state.scannedItems.count { !it.isUnknown },
                            invalidCount = state.scannedItems.count { it.isUnknown },
                            scannerStatus = scannerStatus,
                            scanMode = scanMode,
                            lastScannedData = lastScannedData
                        )
                    }

                    Button(
                        onClick = {
                            currentMember?.let { member ->
                                viewModel.commitSession(mode, member) { count ->
                                    onCommitSuccess(count)
                                }
                            }
                        },
                        enabled = state.scannedItems.isNotEmpty() && !state.isCommitting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = modeAccent)
                    ) {
                        if (state.isCommitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                "Commit ${state.scannedItems.size} item${if (state.scannedItems.size != 1) "s" else ""}",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ── TOP ZONE (fixed height, never scrolls) ──────────────────────
            Box(
                modifier = if (mode == "inquiry")
                    Modifier.fillMaxWidth().weight(1f).clipToBounds()
                else
                    Modifier.fillMaxWidth().height(220.dp).clipToBounds()
            ) {
                when (scannerType) {
                    "camera" -> CameraTopZone(
                        hasCameraPermission = hasCameraPermission,
                        flashColor = flashColor,
                        torchEnabled = userTorchEnabled,
                        onTorchToggle = { userTorchEnabled = !userTorchEnabled },
                        onBarcodeDetected = if (mode == "inquiry") viewModel::processInquiryScan
                                            else viewModel::processScannedCode
                    )
                    "external", "internal" -> CounterTopZone(
                        count = state.scannedItems.size,
                        flashColor = flashColor,
                        modeAccent = modeAccent,
                        scannerType = scannerType,
                        scanTriggerMode = scanTriggerMode,
                        isScanActive = isScanActive,
                        onScanActiveChange = { isScanActive = it },
                        dataWedgeManager = dataWedgeManager
                    )
                    else -> ManualTopZone(onSubmit = viewModel::processScannedCode)
                }
            }

            // ── SCANNED ITEMS LIST (scrollable) — hidden in inquiry mode ────
            if (mode != "inquiry") Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (state.scannedItems.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "Scan a barcode to get started",
                            color = Color(0xFF9CA3AF),
                            fontSize = 14.sp
                        )
                    }
                } else {
                    val listState = rememberLazyListState()
                    val itemCount by remember { derivedStateOf { state.scannedItems.size } }
                    LaunchedEffect(itemCount) {
                        if (itemCount > 0) listState.animateScrollToItem(0)
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 12.dp,
                            end = 12.dp,
                            top = 8.dp,
                            bottom = WindowInsets.navigationBars.asPaddingValues()
                                .calculateBottomPadding() + 80.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(state.scannedItems, key = { _, item -> item.rawCode }) { index, item ->
                            val supportsExpansion = (mode == "check_out" || mode == "update" || mode == "rent_out") && item.container != null
                            ScannedItemCard(
                                item = item,
                                isNewest = index == 0,
                                onRemove = { viewModel.removeItem(item.rawCode) },
                                isExpanded = supportsExpansion && item.rawCode == state.expandedContainerRawCode,
                                onToggleExpand = if (supportsExpansion) {
                                    { viewModel.toggleContainerExpanded(item.rawCode) }
                                } else null,
                                onRemovePendingAsset = { assetId ->
                                    viewModel.removePendingAssetFromContainer(item.rawCode, assetId)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    pendingContainerConfirmation?.let {
        AlertDialog(
            onDismissRequest = {
                viewModel.cancelAddContainer()
            },
            title = { Text("Multiple Containers") },
            text = {
                Text(
                    "You've already scanned a container in this session. " +
                        "Scanning multiple containers is unusual. Add this container anyway?"
                )
            },
            confirmButton = {
                Button(onClick = { viewModel.confirmAddContainer() }) {
                    Text("Add Anyway")
                }
            },
            dismissButton = {
                Button(
                    onClick = { viewModel.cancelAddContainer() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

// ── Top zone composables ────────────────────────────────────────────────────

@Composable
private fun CameraTopZone(
    hasCameraPermission: Boolean,
    flashColor: Color?,
    torchEnabled: Boolean,
    onTorchToggle: () -> Unit,
    onBarcodeDetected: (String) -> Unit
) {
    if (hasCameraPermission) {
        CameraPreview(
            modifier = Modifier.fillMaxSize(),
            torchEnabled = torchEnabled,
            onBarcodeDetected = onBarcodeDetected
        )
        // Reticle overlay
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(240.dp, 140.dp)
                    .border(2.dp, Color.White, RoundedCornerShape(8.dp))
            )
        }
        // Manual torch toggle — top-right corner of the preview, inside preview bounds
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            contentAlignment = Alignment.TopEnd
        ) {
            IconButton(
                onClick = onTorchToggle,
                modifier = Modifier
                    .background(
                        color = Color.Black.copy(alpha = 0.4f),
                        shape = CircleShape
                    )
                    .padding(8.dp)
            ) {
                Icon(
                    imageVector = if (torchEnabled) Icons.Default.FlashOff else Icons.Default.FlashOn,
                    contentDescription = if (torchEnabled) "Turn torch off" else "Turn torch on",
                    tint = Color.White
                )
            }
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1F2937)),
            contentAlignment = Alignment.Center
        ) {
            Text("Camera permission required", color = Color.White)
        }
    }
    flashColor?.let { color ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(color)
        )
    }
}

@Composable
internal fun CounterTopZone(
    count: Int,
    flashColor: Color?,
    modeAccent: Color,
    scannerType: String,
    scanTriggerMode: String,
    isScanActive: Boolean,
    onScanActiveChange: (Boolean) -> Unit,
    dataWedgeManager: DataWedgeManager
) {
    // Hardware-scanner input is captured at the Activity level via a DataWedge Intent
    // broadcast (see MainActivity), not here. On "internal" the circle doubles as the
    // soft scan trigger; on "external" it stays a passive count display.
    val isTrigger = scannerType == "internal"
    val borderAlpha by animateFloatAsState(
        targetValue = if (isTrigger && isScanActive) 1f else 0.55f,
        label = "scanTriggerBorderAlpha"
    )

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // Circle with count — the scan trigger button when scannerType == "internal"
        Box(
            modifier = Modifier
                .size(180.dp)
                .clip(CircleShape)
                .border(4.dp, modeAccent.copy(alpha = borderAlpha), CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .then(
                    if (isTrigger) {
                        Modifier.pointerInput(scanTriggerMode) {
                            if (scanTriggerMode == "toggle") {
                                detectTapGestures(onTap = {
                                    if (isScanActive) {
                                        dataWedgeManager.stopScan()
                                        onScanActiveChange(false)
                                    } else {
                                        dataWedgeManager.startScan()
                                        onScanActiveChange(true)
                                    }
                                })
                            } else {
                                detectTapGestures(onPress = {
                                    // Scanner may still be waking up (IDLE) right after screen
                                    // open; a START_SCANNING sent before it reaches WAITING is
                                    // silently dropped. Poll briefly, but never block the user.
                                    val startTime = System.currentTimeMillis()
                                    while (MainActivity.scannerStatus.value != "WAITING" &&
                                        System.currentTimeMillis() - startTime < 300L
                                    ) {
                                        delay(10L)
                                    }
                                    dataWedgeManager.startScan()
                                    onScanActiveChange(true)
                                    tryAwaitRelease()
                                    dataWedgeManager.stopScan()
                                    onScanActiveChange(false)
                                })
                            }
                        }
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    count.toString(),
                    fontSize = 52.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (isTrigger) {
                    Text(
                        "SCAN",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = modeAccent
                    )
                } else {
                    Text("scanned", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            flashColor?.let { color ->
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(color)
                )
            }
        }
    }
}

@Composable
private fun ManualTopZone(onSubmit: (String) -> Unit) {
    var text by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Enter Asset ID") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    val code = text.trim()
                    if (code.isNotEmpty()) {
                        onSubmit(code)
                        text = ""
                    }
                })
            )
            Button(
                onClick = {
                    val code = text.trim()
                    if (code.isNotEmpty()) {
                        onSubmit(code)
                        text = ""
                    }
                }
            ) {
                Text("Add")
            }
        }
    }
}

// ── Scanned item card ───────────────────────────────────────────────────────

@Composable
private fun ScannedItemCard(
    item: ScannedItem,
    isNewest: Boolean,
    onRemove: () -> Unit,
    isExpanded: Boolean = false,
    onToggleExpand: (() -> Unit)? = null,
    onRemovePendingAsset: (String) -> Unit = {}
) {
    val unknown = item.isUnknown
    val isContainer = item.container != null
    val leftBarColor = when {
        isContainer -> Color(0xFF3B82F6)
        unknown     -> Color(0xFFef4444)
        else -> when (item.asset?.status) {
            "available"    -> Color(0xFF4ade80)
            "checked_out"  -> Color(0xFFf59e0b)
            "lost"         -> Color(0xFFef4444)
            "repair"       -> Color(0xFF9ca3af)
            else           -> Color(0xFF9ca3af)
        }
    }
    val displayId = item.rawCode.let { if (it.length > 4) it.take(4) + ".." else it }
    val cardBorder = if (unknown)
        BorderStroke(1.5.dp, Color(0xFFef4444))
    else
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = cardBorder,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .let { m -> onToggleExpand?.let { m.clickable(onClick = it) } ?: m },
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Colored left bar
                Box(
                    modifier = Modifier
                        .width(6.dp)
                        .fillMaxHeight()
                        .background(leftBarColor)
                )

                // ID (monospace, large, bold)
                Text(
                    displayId,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 12.dp, bottom = 12.dp)
                )

                // Name + type / unknown label / container summary
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 10.dp)
                ) {
                    if (isContainer) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Inventory2,
                                contentDescription = null,
                                tint = Color(0xFF3B82F6),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                item.container?.container?.name ?: "",
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        val totalCount = (item.container?.assetCount ?: 0) + item.pendingAssets.size
                        Text(
                            "$totalCount asset${if (totalCount != 1) "s" else ""} inside",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    } else {
                        Text(
                            if (unknown) "Unknown Item" else item.asset?.name ?: "",
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            color = if (unknown) Color(0xFFef4444) else Color.Unspecified,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            if (unknown) "Not found in system" else item.asset?.type ?: "",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }

                // Expand/collapse chevron — containers only, check_out/update modes only
                if (onToggleExpand != null) {
                    IconButton(onClick = onToggleExpand, modifier = Modifier.size(40.dp)) {
                        Icon(
                            if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isExpanded) "Minimize" else "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Remove button
                IconButton(onClick = onRemove, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Remove",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            if (isExpanded && item.container != null) {
                ExpandedContainerContents(
                    existingAssets = item.container.assets,
                    pendingAssets = item.pendingAssets,
                    onRemovePending = onRemovePendingAsset
                )
            }

            if (isNewest && item.asset != null) {
                ExpandedItemDetails(item.asset)
            }
        }
    }
}

// ── Expanded container contents (check_out/update modes) ───────────────────

@Composable
private fun ExpandedContainerContents(
    existingAssets: List<Asset>,
    pendingAssets: List<Asset>,
    onRemovePending: (String) -> Unit
) {
    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        existingAssets.forEach { asset ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    asset.name,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "already inside",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        pendingAssets.forEach { asset ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    asset.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Green,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "just scanned",
                    fontSize = 11.sp,
                    color = Green,
                    modifier = Modifier.padding(end = 6.dp)
                )
                IconButton(
                    onClick = { onRemovePending(asset.assetId) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Remove",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
        if (existingAssets.isEmpty() && pendingAssets.isEmpty()) {
            Text(
                "No items yet — scan an asset to add it here",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 6.dp)
            )
        }
    }
}

// ── Expanded newest-item details ────────────────────────────────────────────

@Composable
private fun ExpandedItemDetails(asset: Asset?) {
    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        ExpandedDetailItem(label = "Cost") { CostTierBadge(asset?.cost) }
        ExpandedDetailItem(label = "Last User") {
            Text(
                userInitials(asset?.currentUserName),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        ExpandedDetailItem(label = "Last Seen") {
            Text(
                formatShortDate(asset?.updatedAt),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun ExpandedDetailItem(label: String, value: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        value()
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CostTierBadge(cost: String?) {
    val (label, color) = when (cost) {
        "Low"  -> "L" to Color(0xFF4ade80)
        "Med"  -> "M" to Color(0xFFf59e0b)
        "High" -> "H" to Color(0xFFef4444)
        else   -> null
    } ?: (null to null)

    if (label != null && color != null) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(color),
            contentAlignment = Alignment.Center
        ) {
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    } else {
        Text("—", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    }
}

private fun userInitials(name: String?): String {
    if (name.isNullOrBlank()) return "—"
    return name.trim()
        .split(Regex("\\s+"))
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .take(2)
        .joinToString("")
}

private fun formatShortDate(iso: String?): String {
    if (iso.isNullOrBlank()) return "—"
    return try {
        ZonedDateTime.parse(iso).format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
    } catch (_: Exception) {
        "—"
    }
}

// ── Bottom bar helpers ──────────────────────────────────────────────────────

@Composable
private fun ScannerStatusBar(
    mode: String,
    scannerType: String,
    gpsLat: Double?,
    gpsError: Boolean,
    validCount: Int,
    invalidCount: Int,
    scannerStatus: String,
    scanMode: String,
    lastScannedData: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left section — INTERNAL scanner mode only
        if (scannerType == "internal") {
            ScannerStatusDot(scannerStatus)
            ScanModeBadge(scanMode)
            LastScannedDataBox(lastScannedData)
            StatusBarDivider()
        }

        // GPS status — update mode only
        if (mode == "update") {
            val (gpsLabel, gpsColor) = when {
                gpsError -> "Error" to Color(0xFFEF4444)
                gpsLat != null -> "OK" to Color(0xFF22C55E)
                else -> "Fetching" to Color(0xFF9CA3AF)
            }
            Row(
                modifier = Modifier.widthIn(min = 64.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = gpsColor
                )
                Spacer(Modifier.width(4.dp))
                Text(gpsLabel, fontSize = 11.sp, color = gpsColor)
            }
            StatusBarDivider()
        }

        // Valid (known) items
        Row(
            modifier = Modifier.widthIn(min = 32.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = "Valid items",
                modifier = Modifier.size(12.dp),
                tint = Color(0xFF22C55E)
            )
            Spacer(Modifier.width(4.dp))
            Text(validCount.toString(), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
        }

        // Invalid (unknown) items
        Row(
            modifier = Modifier.widthIn(min = 32.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Cancel,
                contentDescription = "Invalid items",
                modifier = Modifier.size(12.dp),
                tint = Color(0xFFEF4444)
            )
            Spacer(Modifier.width(4.dp))
            Text(invalidCount.toString(), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun StatusBarDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(16.dp)
            .background(MaterialTheme.colorScheme.outline)
    )
}

@Composable
private fun ScannerStatusDot(status: String) {
    val color = when (status) {
        "WAITING", "SCANNING" -> Color(0xFF4ade80)
        "IDLE" -> Color(0xFFf59e0b)
        else -> Color(0xFFef4444) // DISABLED, ERROR, UNKNOWN
    }
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
internal fun ScanModeBadge(mode: String) {
    val color = when (mode) {
        "1D" -> Color(0xFF3b82f6)
        "2D" -> Color(0xFF4ade80)
        "BULK" -> Color(0xFFf59e0b)
        "MULTI" -> Color(0xFFa855f7)
        else -> Color(0xFF9CA3AF)
    }
    Box(
        modifier = Modifier
            .widthIn(min = 40.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(color)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(mode, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

@Composable
private fun LastScannedDataBox(data: String) {
    Box(
        modifier = Modifier
            .width(96.dp)
            .height(20.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            data.ifEmpty { "—" },
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            maxLines = 1,
            softWrap = false
        )
    }
}

// ── Scanner mode selector (top bar) ─────────────────────────────────────────

internal data class ScanModeOption(val mode: String, val description: String)

internal val SCAN_MODE_OPTIONS = listOf(
    ScanModeOption("1D", "Linear barcodes only, faster decode"),
    ScanModeOption("2D", "QR, DataMatrix, PDF417, Aztec only"),
    ScanModeOption("BULK", "All decoders, continuous scanning after each decode"),
    ScanModeOption("MULTI", "Multiple barcodes per trigger press")
)

@Composable
internal fun ScanModeButton(currentMode: String, onModeSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { expanded = true }) {
            ScanModeBadge(currentMode)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            SCAN_MODE_OPTIONS.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ScanModeBadge(option.mode)
                                if (option.mode == currentMode) {
                                    Spacer(Modifier.width(6.dp))
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = "Current mode",
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                            Text(
                                option.description,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    onClick = {
                        onModeSelected(option.mode)
                        expanded = false
                    }
                )
            }
        }
    }
}

// ── Mode helpers (used by ScannerTypeSelectScreen too) ─────────────────────

fun modeColor(mode: String) = when (mode) {
    "check_out" -> Blue
    "check_in"  -> Green
    "update"    -> Purple
    "rent_out"  -> Amber
    else        -> Grey
}

fun modeTitle(mode: String) = when (mode) {
    "check_out" -> "Check Out"
    "check_in"  -> "Check In"
    "update"    -> "Update"
    "rent_out"  -> "Rent Out"
    else        -> "Inquiry"
}

