package com.lfcreative.lfscan.ui.screen

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lfcreative.lfscan.data.model.Asset
import com.lfcreative.lfscan.data.model.Location
import com.lfcreative.lfscan.ui.theme.Blue
import com.lfcreative.lfscan.ui.theme.Green
import com.lfcreative.lfscan.ui.theme.Grey
import com.lfcreative.lfscan.ui.theme.Purple
import com.lfcreative.lfscan.ui.theme.StatusAvailableBg
import com.lfcreative.lfscan.ui.theme.StatusAvailableText
import com.lfcreative.lfscan.ui.theme.StatusCheckedOutBg
import com.lfcreative.lfscan.ui.theme.StatusCheckedOutText
import com.lfcreative.lfscan.ui.theme.StatusLostBg
import com.lfcreative.lfscan.ui.theme.StatusLostText
import com.lfcreative.lfscan.ui.theme.StatusUnderRepairBg
import com.lfcreative.lfscan.ui.theme.StatusUnderRepairText
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    mode: String,
    viewModel: ScanViewModel,
    onBack: () -> Unit,
    onCommitSuccess: (Int) -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val currentMember by viewModel.currentMember.collectAsState(initial = null)

    var hasCameraPermission by remember { mutableStateOf(false) }
    var flashColor by remember { mutableStateOf<Color?>(null) }
    var selectedAsset by remember { mutableStateOf<Asset?>(null) }

    val modeColor = modeColor(mode)
    val modeTitle = modeTitle(mode)

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.CAMERA)
        if (mode == "check_in") viewModel.loadLocations()
    }

    LaunchedEffect(state.flash) {
        when (state.flash) {
            ScanFlash.FOUND -> {
                vibrate(context)
                flashColor = Color(0x9900C853)
                delay(400)
                flashColor = null
                viewModel.clearFlash()
            }
            ScanFlash.NOT_FOUND -> {
                flashColor = Color(0x99FF1744)
                Toast.makeText(context, "Asset not found", Toast.LENGTH_SHORT).show()
                delay(400)
                flashColor = null
                viewModel.clearFlash()
            }
            ScanFlash.DUPLICATE -> {
                flashColor = Color(0x99FFCA28)
                delay(300)
                flashColor = null
                viewModel.clearFlash()
            }
            ScanFlash.NONE -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(modeTitle, color = modeColor, fontWeight = FontWeight.SemiBold)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
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
                    Button(
                        onClick = {
                            currentMember?.let { member ->
                                viewModel.commit(mode, member) { count ->
                                    onCommitSuccess(count)
                                }
                            }
                        },
                        enabled = state.scannedItems.isNotEmpty() && !state.isCommitting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = modeColor)
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
            // Camera preview with flash overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            ) {
                if (hasCameraPermission) {
                    CameraPreview(
                        modifier = Modifier.fillMaxSize(),
                        onBarcodeDetected = viewModel::processBarcode
                    )
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

            // Location picker (check_in only)
            if (mode == "check_in") {
                LocationPicker(
                    locations = state.locations,
                    selectedId = state.locationId,
                    onSelect = viewModel::setLocation,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // GPS placeholder (update only)
            if (mode == "update") {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    color = Color(0xFFF3F4F6),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        "GPS: will be captured on commit",
                        modifier = Modifier.padding(12.dp),
                        color = Color(0xFF6B7280),
                        fontSize = 13.sp
                    )
                }
            }

            // Scanned items list
            if (state.scannedItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Scan a barcode to get started",
                        color = Color(0xFF9CA3AF),
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(state.scannedItems, key = { it.asset.assetId }) { item ->
                        ScannedItemRow(
                            item = item,
                            onRemove = { viewModel.removeItem(item.asset.assetId) },
                            onClick = if (mode == "inquiry") {
                                {
                                    selectedAsset = item.asset
                                    viewModel.loadLastEvent(item.asset.id)
                                }
                            } else null
                        )
                        HorizontalDivider(color = Color(0xFFF3F4F6))
                    }
                }
            }
        }
    }

    // Asset detail bottom sheet (inquiry mode)
    selectedAsset?.let { asset ->
        AssetDetailSheet(
            asset = asset,
            lastEvent = state.lastEvent,
            onDismiss = { selectedAsset = null }
        )
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

@Composable
private fun ScannedItemRow(
    item: ScannedItem,
    onRemove: () -> Unit,
    onClick: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.asset.assetId,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp
            )
            Text(
                item.asset.name,
                fontSize = 13.sp,
                color = Color(0xFF374151)
            )
        }
        StatusBadge(status = item.asset.status)
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Remove",
                tint = Color(0xFF9CA3AF),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun StatusBadge(status: String, modifier: Modifier = Modifier) {
    val (bg, fg) = when (status) {
        "available"    -> StatusAvailableBg    to StatusAvailableText
        "checked_out"  -> StatusCheckedOutBg   to StatusCheckedOutText
        "lost"         -> StatusLostBg         to StatusLostText
        "under_repair" -> StatusUnderRepairBg  to StatusUnderRepairText
        else           -> Color(0xFFF3F4F6)    to Color(0xFF374151)
    }
    val label = status.split("_").joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
    Surface(color = bg, shape = RoundedCornerShape(4.dp), modifier = modifier) {
        Text(
            label,
            color = fg,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssetDetailSheet(
    asset: Asset,
    lastEvent: com.lfcreative.lfscan.data.model.InventoryEvent?,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                asset.assetId,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            Text(asset.name, fontSize = 16.sp, fontWeight = FontWeight.Medium)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Status: ", fontSize = 14.sp, color = Color(0xFF6B7280))
                StatusBadge(status = asset.status)
            }

            DetailRow("Type", asset.type)
            DetailRow("Current assignee", asset.currentUserName ?: "—")
            DetailRow(
                "Last event",
                lastEvent?.let { "${it.eventType.replace("_", " ")} by ${it.performedByName}" }
                    ?: "No events recorded"
            )

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row {
        Text("$label: ", fontSize = 14.sp, color = Color(0xFF6B7280))
        Text(value, fontSize = 14.sp, color = Color(0xFF111827))
    }
}

private fun modeColor(mode: String) = when (mode) {
    "check_out" -> Blue
    "check_in"  -> Green
    "update"    -> Purple
    else        -> Grey
}

private fun modeTitle(mode: String) = when (mode) {
    "check_out" -> "Check Out"
    "check_in"  -> "Check In"
    "update"    -> "Update"
    else        -> "Inquiry"
}

@Suppress("DEPRECATION")
private fun vibrate(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        manager.defaultVibrator.vibrate(
            VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE)
        )
    } else {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        vibrator.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}
