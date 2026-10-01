package com.lfcreative.lfscan.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import com.lfcreative.lfscan.ui.theme.LocalExtendedColors
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lfcreative.lfscan.data.model.Asset

private data class StatusFilter(val label: String, val key: String?)

private val statusFilters = listOf(
    StatusFilter("All",          null),
    StatusFilter("Available",    "available"),
    StatusFilter("Checked Out",  "checked_out"),
    StatusFilter("Rented",       "rented"),
    StatusFilter("Lost",         "lost"),
    StatusFilter("Under Repair", "repair")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetsScreen(
    onBack: () -> Unit,
    onNavigateToAsset: (String) -> Unit,
    onNavigateToCreateAsset: () -> Unit,
    onQuickScan: () -> Unit,
    viewModel: AssetsViewModel = hiltViewModel()
) {
    val filteredAssets by viewModel.filteredAssets.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedStatus by viewModel.selectedStatus.collectAsState()

    var searchExpanded by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(searchExpanded) {
        if (searchExpanded) focusRequester.requestFocus()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Assets", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (searchExpanded) {
                        IconButton(onClick = {
                            searchExpanded = false
                            viewModel.setSearchQuery("")
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Close search")
                        }
                    } else {
                        TextButton(onClick = onNavigateToCreateAsset) {
                            Text("+ New Asset")
                        }
                        IconButton(onClick = { searchExpanded = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ExtendedFloatingActionButton(
                    onClick = onQuickScan,
                    icon = { Icon(Icons.Default.QrCodeScanner, contentDescription = null) },
                    text = { Text("Scan") }
                )
                ExtendedFloatingActionButton(
                    onClick = onNavigateToCreateAsset,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("New Asset") }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search bar — expands below the top bar
            AnimatedVisibility(visible = searchExpanded) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    placeholder = { Text("Search by name or ID…") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .focusRequester(focusRequester),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {}),
                    colors = OutlinedTextFieldDefaults.colors(),
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Clear",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                )
            }

            // Status filter chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(statusFilters) { filter ->
                    FilterChip(
                        selected = selectedStatus == filter.key,
                        onClick = { viewModel.setStatusFilter(filter.key) },
                        label = { Text(filter.label) }
                    )
                }
            }

            // Asset list / loading / empty state
            PullToRefreshBox(
                isRefreshing = isLoading,
                onRefresh = viewModel::loadAssets,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when {
                    isLoading && filteredAssets.isEmpty() -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    filteredAssets.isEmpty() -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No assets found", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                        }
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(filteredAssets, key = { it.assetId }) { asset ->
                                AssetCard(
                                    asset = asset,
                                    onClick = { onNavigateToAsset(asset.assetId) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun AssetCard(
    asset: Asset,
    onClick: () -> Unit,
    // Opt-in only (ContainerDetailScreen's asset list) — the plain Assets list is unchanged.
    showLastKnownLocation: Boolean = false
) {
    val extendedColors = LocalExtendedColors.current
    val overdue = asset.status == "rented" && isRentalOverdue(asset.rentalDueDate)
    val leftBarColor = when (asset.status) {
        "available"   -> extendedColors.green
        "checked_out" -> extendedColors.amber
        "rented"      -> if (overdue) extendedColors.red else extendedColors.purple
        "lost"        -> extendedColors.red
        "repair"      -> extendedColors.grey
        else          -> extendedColors.grey
    }
    // The rented line below already spells out status in text for "rented"; every other status
    // only had this color bar to go on (WCAG 1.4.1 — color can't be the only cue), so give those
    // a small text badge too.
    val statusLabel = when (asset.status) {
        "available"   -> "Available"
        "checked_out" -> "Checked Out"
        "lost"        -> "Lost"
        "repair"      -> "Repair"
        "rented"      -> null
        else          -> asset.status.replaceFirstChar { it.uppercase() }
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(leftBarColor)
            )

            Text(
                asset.assetId,
                fontFamily = com.lfcreative.lfscan.ui.theme.AppMonospaceFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .widthIn(max = 110.dp)
                    .padding(start = 10.dp, end = 6.dp, top = 12.dp, bottom = 12.dp)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 10.dp, bottom = 10.dp, end = 12.dp)
            ) {
                Text(
                    asset.name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    asset.type.orEmpty(),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                if (showLastKnownLocation) {
                    asset.lastKnownAddress?.takeIf { it.isNotBlank() }?.let { address ->
                        Text(
                            address,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (asset.status == "rented") {
                    Text(
                        "Rented to ${asset.currentUserName ?: "?"} · Due ${formatRentalDueDate(asset.rentalDueDate)}" +
                            if (overdue) " (Overdue)" else "",
                        fontSize = 11.sp,
                        color = if (overdue) extendedColors.red else extendedColors.purple,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            statusLabel?.let { label ->
                Box(
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .background(leftBarColor.copy(alpha = 0.15f), RoundedCornerShape(0.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = leftBarColor)
                }
            }
        }
    }
}
