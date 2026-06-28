package com.lfcreative.lfscan.ui.screen

import android.Manifest
import android.content.Context
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lfcreative.lfscan.data.model.Location
import com.lfcreative.lfscan.ui.theme.Blue
import com.lfcreative.lfscan.ui.theme.Green
import com.lfcreative.lfscan.ui.theme.Grey
import com.lfcreative.lfscan.ui.theme.Purple
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    mode: String,
    scannerType: String,
    viewModel: ScanViewModel,
    onBack: () -> Unit,
    onCommitSuccess: (Int) -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val currentMember by viewModel.currentMember.collectAsState(initial = null)

    var hasCameraPermission by remember { mutableStateOf(false) }
    var hasFineLocation by remember { mutableStateOf(false) }
    var flashColor by remember { mutableStateOf<Color?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    val modeAccent = modeColor(mode)

    // Permissions
    val cameraPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    val locationPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasFineLocation = granted }

    LaunchedEffect(Unit) {
        if (scannerType == "camera") cameraPermLauncher.launch(Manifest.permission.CAMERA)
        if (mode == "check_in") viewModel.loadLocations()
        if (mode == "update") locationPermLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
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
                if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER, 5_000L, 5f, listener
                    )
                }
                if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    locationManager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER, 5_000L, 5f, listener
                    )
                }
            } catch (_: SecurityException) {}
            onDispose { locationManager.removeUpdates(listener) }
        } else {
            onDispose {}
        }
    }

    // Flash effect + audio feedback
    LaunchedEffect(state.flash) {
        when (state.flash) {
            ScanFlash.FOUND -> {
                vibrate(context)
                SoundManager.playGoodScan()
                flashColor = Color(0x9900C853)
                delay(400)
                flashColor = null
                viewModel.clearFlash()
            }
            ScanFlash.NOT_FOUND -> {
                SoundManager.playUnknownScan()
                flashColor = Color(0x99FF1744)
                delay(400)
                flashColor = null
                viewModel.clearFlash()
            }
            ScanFlash.DUPLICATE -> {
                SoundManager.playDuplicateScan()
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
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Column {
                    state.commitError?.let { err ->
                        Text(
                            err,
                            color = Color(0xFFEF4444),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }

                    // Location picker — check_in
                    if (mode == "check_in") {
                        LocationPicker(
                            locations = state.locations,
                            selectedId = state.locationId,
                            onSelect = viewModel::setLocation,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }

                    // GPS status row — update
                    if (mode == "update") {
                        GpsStatusRow(state)
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
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            ) {
                when (scannerType) {
                    "camera" -> CameraTopZone(
                        hasCameraPermission = hasCameraPermission,
                        flashColor = flashColor,
                        onBarcodeDetected = viewModel::processScannedCode
                    )
                    "external", "internal" -> CounterTopZone(
                        count = state.scannedItems.size,
                        flashColor = flashColor,
                        modeAccent = modeAccent,
                        onCodeReceived = viewModel::processScannedCode
                    )
                    else -> ManualTopZone(onSubmit = viewModel::processScannedCode)
                }
            }

            // ── SCANNED ITEMS LIST (scrollable) ─────────────────────────────
            Box(
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
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 12.dp, vertical = 8.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(state.scannedItems, key = { it.rawCode }) { item ->
                            ScannedItemCard(
                                item = item,
                                onRemove = { viewModel.removeItem(item.rawCode) }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Top zone composables ────────────────────────────────────────────────────

@Composable
private fun CameraTopZone(
    hasCameraPermission: Boolean,
    flashColor: Color?,
    onBarcodeDetected: (String) -> Unit
) {
    var torchEnabled by remember { mutableStateOf(false) }

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
        // Torch toggle — top-right corner of the preview
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            contentAlignment = Alignment.TopEnd
        ) {
            IconButton(
                onClick = { torchEnabled = !torchEnabled },
                modifier = Modifier
                    .size(40.dp)
                    .background(Color(0x66000000), CircleShape)
            ) {
                Icon(
                    imageVector = if (torchEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                    contentDescription = if (torchEnabled) "Turn torch off" else "Turn torch on",
                    tint = if (torchEnabled) Color(0xFFFFCA28) else Color.White,
                    modifier = Modifier.size(20.dp)
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
private fun CounterTopZone(
    count: Int,
    flashColor: Color?,
    modeAccent: Color,
    onCodeReceived: (String) -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var inputBuffer by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.hide()
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // Circle with count
        Box(
            modifier = Modifier
                .size(180.dp)
                .clip(CircleShape)
                .border(4.dp, modeAccent, CircleShape)
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    count.toString(),
                    fontSize = 52.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF111827)
                )
                Text("scanned", fontSize = 13.sp, color = Color(0xFF9CA3AF))
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

    // Hidden text field captures hardware scanner input
    BasicTextField(
        value = inputBuffer,
        onValueChange = { newVal ->
            val newlineIdx = newVal.indexOfFirst { it == '\n' || it == '\r' }
            if (newlineIdx >= 0) {
                val code = newVal.substring(0, newlineIdx).trim()
                if (code.isNotEmpty()) onCodeReceived(code)
                inputBuffer = ""
            } else {
                inputBuffer = newVal
            }
        },
        modifier = Modifier
            .size(1.dp)
            .alpha(0f)
            .focusRequester(focusRequester),
        textStyle = TextStyle.Default,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.None
        )
    )
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
private fun ScannedItemCard(item: ScannedItem, onRemove: () -> Unit) {
    val unknown = item.isUnknown
    val leftBarColor = if (unknown) Color(0xFFef4444) else when (item.asset?.status) {
        "available"    -> Color(0xFF4ade80)
        "checked_out"  -> Color(0xFFf59e0b)
        "lost"         -> Color(0xFFef4444)
        "under_repair" -> Color(0xFF9ca3af)
        else           -> Color(0xFF9ca3af)
    }
    val displayId = item.rawCode.let { if (it.length > 4) it.take(4) + ".." else it }
    val cardBorder = if (unknown)
        BorderStroke(1.5.dp, Color(0xFFef4444))
    else
        BorderStroke(1.dp, Color(0xFFE5E7EB))

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = cardBorder,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
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

            // Name + type / unknown label
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 10.dp)
            ) {
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
                    color = Color(0xFF6B7280),
                    maxLines = 1
                )
            }

            // Remove button
            IconButton(onClick = onRemove, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Remove",
                    tint = Color(0xFF9CA3AF),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// ── Bottom bar helpers ──────────────────────────────────────────────────────

@Composable
private fun GpsStatusRow(state: ScanUiState) {
    val gpsText = when {
        state.gpsLat != null && state.gpsAddress != null ->
            "GPS: ${state.gpsAddress}"
        state.gpsLat != null ->
            "GPS: ${"%.5f".format(state.gpsLat)}, ${"%.5f".format(state.gpsLng)}"
        else -> "GPS: Detecting..."
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.LocationOn,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = if (state.gpsLat != null) Color(0xFF22C55E) else Color(0xFF9CA3AF)
        )
        Spacer(Modifier.width(6.dp))
        Text(gpsText, fontSize = 13.sp, color = Color(0xFF6B7280))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocationPicker(
    locations: List<Location>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = locations.find { it.id == selectedId }?.name ?: "Select location"

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Check-in location") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            if (locations.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("No locations set up yet", color = Color(0xFF9CA3AF)) },
                    onClick = { expanded = false }
                )
            }
            locations.forEach { loc ->
                DropdownMenuItem(
                    text = { Text(loc.name) },
                    onClick = { onSelect(loc.id); expanded = false }
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
    else        -> Grey
}

fun modeTitle(mode: String) = when (mode) {
    "check_out" -> "Check Out"
    "check_in"  -> "Check In"
    "update"    -> "Update"
    else        -> "Inquiry"
}

// ── Vibration ───────────────────────────────────────────────────────────────

@Suppress("DEPRECATION")
private fun vibrate(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager =
            context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        manager.defaultVibrator.vibrate(
            VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE)
        )
    } else {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        vibrator.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}
