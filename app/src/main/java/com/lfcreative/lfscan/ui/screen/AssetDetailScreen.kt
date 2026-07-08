package com.lfcreative.lfscan.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.lfcreative.lfscan.data.model.Asset
import com.lfcreative.lfscan.data.model.Container
import com.lfcreative.lfscan.data.model.InventoryEvent
import com.lfcreative.lfscan.data.model.Location
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetDetailScreen(
    onBack: () -> Unit,
    showCreatedMessage: Boolean = false,
    viewModel: AssetDetailViewModel = hiltViewModel()
) {
    val asset by viewModel.asset.collectAsState()
    val location by viewModel.location.collectAsState()
    val locations by viewModel.locations.collectAsState()
    val container by viewModel.container.collectAsState()
    val events by viewModel.events.collectAsState()
    val locationMap by viewModel.locationMap.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isEditing by viewModel.isEditing.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val currentMember by viewModel.currentMember.collectAsState(initial = null)

    var showRemoveFromContainerDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.snackbarMessage.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    LaunchedEffect(Unit) {
        if (showCreatedMessage) snackbarHostState.showSnackbar("Asset created")
    }

    // Edit form local state — reset each time we enter edit mode
    var editName by remember { mutableStateOf("") }
    var editType by remember { mutableStateOf("") }
    var editSize by remember { mutableStateOf("M") }
    var editCost by remember { mutableStateOf("Med") }
    var editStatus by remember { mutableStateOf("available") }
    var editLocationId by remember { mutableStateOf<String?>(null) }
    var editNotes by remember { mutableStateOf("") }

    LaunchedEffect(isEditing) {
        if (isEditing) {
            editName = asset?.name ?: ""
            editType = asset?.type ?: ""
            editSize = asset?.size ?: "M"
            editCost = asset?.cost ?: "Med"
            editStatus = asset?.status ?: "available"
            editLocationId = asset?.currentLocationId
            editNotes = asset?.notes ?: ""
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        asset?.assetId ?: "Loading…",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (asset != null) {
                        IconButton(
                            onClick = { viewModel.toggleEdit() },
                            enabled = isEditing || asset?.containerLocked != true
                        ) {
                            Icon(
                                if (isEditing) Icons.Default.Close else Icons.Default.Edit,
                                contentDescription = if (isEditing) "Cancel edit" else "Edit asset"
                            )
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        when {
            isLoading && asset == null -> {
                Box(
                    Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
            }
            asset == null -> {
                Box(
                    Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) { Text("Asset not found", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            else -> {
                val a = asset!!
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    // ── Section 1: Photo Gallery ───────────────────────
                    item { PhotoGallery(a.photoUrls) }

                    // ── Section 2: Status Banner ───────────────────────
                    item { StatusBanner(a.status, a.containerLocked, a.rentalDueDate) }

                    // ── Container-locked warning ────────────────────────
                    if (a.containerLocked) {
                        item {
                            ContainerLockedWarning(
                                containerCode = container?.containerId ?: "",
                                onRemoveClick = { showRemoveFromContainerDialog = true }
                            )
                        }
                    }

                    // ── Section 3: Info grid OR edit form ──────────────
                    if (!isEditing) {
                        item { InfoGrid(asset = a, location = location, container = container) }
                    } else {
                        item {
                            EditForm(
                                name = editName, onNameChange = { editName = it },
                                type = editType, onTypeChange = { editType = it },
                                size = editSize, onSizeChange = { editSize = it },
                                cost = editCost, onCostChange = { editCost = it },
                                status = editStatus, onStatusChange = { editStatus = it },
                                locationId = editLocationId,
                                onLocationChange = { editLocationId = it },
                                locations = locations,
                                notes = editNotes, onNotesChange = { editNotes = it }
                            )
                        }
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { viewModel.cancelEdit() },
                                    modifier = Modifier.weight(1f)
                                ) { Text("Cancel") }
                                Button(
                                    onClick = {
                                        currentMember?.let { member ->
                                            viewModel.saveAsset(
                                                a.copy(
                                                    name = editName,
                                                    type = editType.ifBlank { null },
                                                    size = editSize,
                                                    cost = editCost,
                                                    status = editStatus,
                                                    currentLocationId = editLocationId,
                                                    notes = editNotes.ifBlank { null }
                                                ),
                                                member
                                            )
                                        }
                                    },
                                    enabled = !isSaving && currentMember != null,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    if (isSaving) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            color = Color.White,
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Text("Save", fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }

                    // ── Section 4: Activity Timeline ───────────────────
                    item {
                        Text(
                            "Activity",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            modifier = Modifier.padding(
                                start = 16.dp, end = 16.dp,
                                top = 20.dp, bottom = 8.dp
                            )
                        )
                    }

                    when {
                        isLoading -> item {
                            Box(
                                Modifier.fillMaxWidth().padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) { CircularProgressIndicator(modifier = Modifier.size(24.dp)) }
                        }
                        events.isEmpty() -> item {
                            Text(
                                "No activity yet",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                        else -> itemsIndexed(events) { index, event ->
                            EventRow(
                                event = event,
                                locationMap = locationMap,
                                isLast = index == events.lastIndex
                            )
                        }
                    }
                }
            }
        }
    }

    if (showRemoveFromContainerDialog) {
        val containerCode = container?.containerId ?: ""
        AlertDialog(
            onDismissRequest = { showRemoveFromContainerDialog = false },
            title = { Text("Remove from container $containerCode?") },
            text = { Text("This asset will be unlocked and can be individually checked in/out.") },
            confirmButton = {
                TextButton(onClick = {
                    showRemoveFromContainerDialog = false
                    currentMember?.let { viewModel.removeFromContainer(it) }
                }) { Text("Remove", color = Color(0xFFEF4444)) }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveFromContainerDialog = false }) { Text("Cancel") }
            }
        )
    }
}

// ── Photo Gallery ───────────────────────────────────────────────────────────

@Composable
private fun PhotoGallery(photoUrls: List<String>) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
    ) {
        if (photoUrls.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.CameraAlt,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("No photos", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                }
            }
        } else {
            val pagerState = rememberPagerState(pageCount = { photoUrls.size })
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                AsyncImage(
                    model = photoUrls[page],
                    contentDescription = "Asset photo ${page + 1}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (photoUrls.size > 1) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    repeat(photoUrls.size) { idx ->
                        Box(
                            modifier = Modifier
                                .size(if (pagerState.currentPage == idx) 8.dp else 6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (pagerState.currentPage == idx) Color.White
                                    else Color.White.copy(alpha = 0.5f)
                                )
                        )
                    }
                }
            }
        }
    }
}

// ── Status Banner ────────────────────────────────────────────────────────────

@Composable
private fun StatusBanner(status: String, containerLocked: Boolean = false, rentalDueDate: String? = null) {
    val overdue = status == "rented" && isRentalOverdue(rentalDueDate)
    val color = when (status) {
        "available"   -> Color(0xFF4ade80)
        "checked_out" -> Color(0xFFf59e0b)
        "rented"      -> if (overdue) Color(0xFFef4444) else Color(0xFFa855f7)
        "lost"        -> Color(0xFFef4444)
        "repair"      -> Color(0xFF9ca3af)
        else          -> Color(0xFF9ca3af)
    }
    val label = when (status) {
        "available"   -> "AVAILABLE"
        "checked_out" -> "CHECKED OUT"
        "rented"      -> if (overdue) "RENTED (OVERDUE)" else "RENTED"
        "lost"        -> "LOST"
        "repair"      -> "UNDER REPAIR"
        else          -> status.uppercase()
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(color)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        if (containerLocked) {
            Icon(
                Icons.Default.Lock,
                contentDescription = "Locked inside a container",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp)
                    .size(18.dp)
            )
        }
    }
}

// ── Container-locked warning ─────────────────────────────────────────────────

@Composable
private fun ContainerLockedWarning(containerCode: String, onRemoveClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            "This asset is inside container $containerCode. Check in/out the container to " +
                "move this asset, or remove it from the container first.",
            fontSize = 13.sp,
            color = Color(0xFF92400E),
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFFEF3C7), RoundedCornerShape(8.dp))
                .padding(12.dp)
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onRemoveClick,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
            border = BorderStroke(1.dp, Color(0xFFEF4444)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Remove from Container")
        }
    }
}

// ── Info Grid ────────────────────────────────────────────────────────────────

@Composable
private fun InfoGrid(asset: Asset, location: Location?, container: Container? = null) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        InfoRow("ID", asset.assetId, monospace = true)
        InfoRow("Name", asset.name)
        InfoRow("Type", asset.type?.takeIf { it.isNotBlank() } ?: "—")
        InfoRow("Size", when (asset.size) {
            "S" -> "Small"; "M" -> "Medium"; "L" -> "Large"; else -> asset.size
        })
        InfoRow("Cost", when (asset.cost) {
            "Low" -> "Low"; "Med" -> "Medium"; "High" -> "High"; else -> asset.cost
        })
        InfoRow("Location", location?.name ?: "—")
        InfoRow(
            "Container",
            container?.let { "${it.name} (${it.containerId})" } ?: "—"
        )
        InfoRow("With", asset.currentUserName ?: "—")
        if (asset.status == "rented") {
            val overdue = isRentalOverdue(asset.rentalDueDate)
            InfoRow("Contact", asset.renterContact?.takeIf { it.isNotBlank() } ?: "—")
            InfoRow(
                "Due Back",
                formatRentalDueDate(asset.rentalDueDate) + if (overdue) " (Overdue)" else "",
                valueColor = if (overdue) Color(0xFFef4444) else null
            )
        }
        InfoRow("Last Seen", formatTimestamp(asset.updatedAt))

        // Notes — full width
        Spacer(Modifier.height(4.dp))
        Text("Notes", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Text(
            asset.notes?.takeIf { it.isNotBlank() } ?: "—",
            fontSize = 14.sp,
            modifier = Modifier.fillMaxWidth()
        )
        HorizontalDivider(
            modifier = Modifier.padding(top = 10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String, monospace: Boolean = false, valueColor: Color? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(90.dp)
        )
        Text(
            value,
            fontSize = 14.sp,
            color = valueColor ?: Color.Unspecified,
            modifier = Modifier.weight(1f),
            fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
}

// ── Edit Form ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditForm(
    name: String, onNameChange: (String) -> Unit,
    type: String, onTypeChange: (String) -> Unit,
    size: String, onSizeChange: (String) -> Unit,
    cost: String, onCostChange: (String) -> Unit,
    status: String, onStatusChange: (String) -> Unit,
    locationId: String?, onLocationChange: (String?) -> Unit,
    locations: List<Location>,
    notes: String, onNotesChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = name, onValueChange = onNameChange,
            label = { Text("Name") }, singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = type, onValueChange = onTypeChange,
            label = { Text("Type") }, singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        // Size
        Text("Size", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val sizeOptions = listOf("S" to "Small", "M" to "Medium", "L" to "Large")
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            sizeOptions.forEachIndexed { idx, (key, label) ->
                SegmentedButton(
                    selected = size == key,
                    onClick = { onSizeChange(key) },
                    shape = SegmentedButtonDefaults.itemShape(index = idx, count = sizeOptions.size)
                ) { Text(label, fontSize = 13.sp) }
            }
        }

        // Cost
        Text("Cost", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val costOptions = listOf("Low" to "Low", "Med" to "Medium", "High" to "High")
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            costOptions.forEachIndexed { idx, (key, label) ->
                SegmentedButton(
                    selected = cost == key,
                    onClick = { onCostChange(key) },
                    shape = SegmentedButtonDefaults.itemShape(index = idx, count = costOptions.size)
                ) { Text(label, fontSize = 13.sp) }
            }
        }

        // Status dropdown
        var statusExpanded by remember { mutableStateOf(false) }
        val statusOptions = listOf(
            "available"   to "Available",
            "checked_out" to "Checked Out",
            "lost"        to "Lost",
            "repair"      to "Under Repair"
        )
        ExposedDropdownMenuBox(
            expanded = statusExpanded,
            onExpandedChange = { statusExpanded = !statusExpanded }
        ) {
            OutlinedTextField(
                value = statusOptions.find { it.first == status }?.second ?: status,
                onValueChange = {},
                readOnly = true,
                label = { Text("Status") },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = statusExpanded)
                },
                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
            )
            ExposedDropdownMenu(
                expanded = statusExpanded,
                onDismissRequest = { statusExpanded = false }
            ) {
                statusOptions.forEach { (key, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = { onStatusChange(key); statusExpanded = false }
                    )
                }
            }
        }

        // Location dropdown
        var locationExpanded by remember { mutableStateOf(false) }
        val selectedLocationName = locations.find { it.id == locationId }?.name ?: "No location"
        ExposedDropdownMenuBox(
            expanded = locationExpanded,
            onExpandedChange = { locationExpanded = !locationExpanded }
        ) {
            OutlinedTextField(
                value = selectedLocationName,
                onValueChange = {},
                readOnly = true,
                label = { Text("Location") },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = locationExpanded)
                },
                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
            )
            ExposedDropdownMenu(
                expanded = locationExpanded,
                onDismissRequest = { locationExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("No location", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    onClick = { onLocationChange(null); locationExpanded = false }
                )
                locations.forEach { loc ->
                    DropdownMenuItem(
                        text = { Text(loc.name) },
                        onClick = { onLocationChange(loc.id); locationExpanded = false }
                    )
                }
            }
        }

        OutlinedTextField(
            value = notes, onValueChange = onNotesChange,
            label = { Text("Notes") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ── Activity Timeline ─────────────────────────────────────────────────────────
// eventDotColor/eventDescription/formatTimestamp are also used by ActivityScreen's global feed.

internal fun eventDotColor(eventType: String): Color = when (eventType) {
    "checkin"  -> Color(0xFF4ade80)
    "checkout" -> Color(0xFFf59e0b)
    "location" -> Color(0xFFA855F7)
    "lost"     -> Color(0xFFef4444)
    "created"  -> Color(0xFF3B82F6)
    "edited"   -> Color(0xFF9ca3af)
    "repair"   -> Color(0xFFf97316)
    "rented"   -> Color(0xFFa855f7)
    else       -> Color(0xFF9ca3af)
}

internal fun eventDescription(event: InventoryEvent, locationMap: Map<String, String>): String {
    val locationName = event.locationId?.let { locationMap[it] }
    return when (event.eventType) {
        "checkin"  -> "Checked in" + (locationName?.let { " to $it" } ?: "")
        "checkout" -> "Checked out by ${event.performedByName ?: "unknown"}"
        "location" -> "Location updated" + (locationName?.let { " to $it" } ?: "")
        "lost"     -> "Marked as lost by ${event.performedByName ?: "unknown"}"
        "created"  -> "Added to inventory by ${event.performedByName ?: "unknown"}"
        "edited"   -> "Details edited by ${event.performedByName ?: "unknown"}"
        "repair"   -> "Sent for repair by ${event.performedByName ?: "unknown"}"
        "inquiry"  -> "Looked up by ${event.performedByName ?: "unknown"}"
        "rented"   -> "Rented out by ${event.performedByName ?: "unknown"}"
        else       -> event.eventType
    }
}

@Composable
private fun EventRow(
    event: InventoryEvent,
    locationMap: Map<String, String>,
    isLast: Boolean
) {
    val dotColor = eventDotColor(event.eventType)
    val description = eventDescription(event, locationMap)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(start = 16.dp, end = 16.dp)
    ) {
        // Left track: dot + vertical connecting line
        Column(
            modifier = Modifier
                .width(32.dp)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
        }

        // Event content
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(
                    start = 10.dp,
                    top = 0.dp,
                    bottom = if (isLast) 8.dp else 20.dp
                )
        ) {
            Text(description, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(
                formatTimestamp(event.createdAt),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            event.gpsAddress?.let { addr ->
                Text(
                    "📍 $addr",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontStyle = FontStyle.Italic
                )
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

internal fun formatTimestamp(iso: String?): String {
    if (iso == null) return "—"
    return try {
        val zdt = ZonedDateTime.parse(iso)
        val formatter = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.getDefault())
        zdt.format(formatter)
    } catch (_: Exception) {
        iso
    }
}
