package com.lfcreative.lfscan.ui.screen

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lfcreative.lfscan.MainActivity
import com.lfcreative.lfscan.data.model.Asset
import com.lfcreative.lfscan.data.model.ContainerWithDetails
import com.lfcreative.lfscan.data.model.InventoryEvent
import com.lfcreative.lfscan.data.model.Location as LocationModel
import com.lfcreative.lfscan.data.model.TeamMember
import com.lfcreative.lfscan.data.offline.OfflineRepository
import com.lfcreative.lfscan.data.repository.InventoryRepository
import com.lfcreative.lfscan.session.SessionDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

data class ScannedItem(
    val asset: Asset? = null,
    val container: ContainerWithDetails? = null,
    val isUnknown: Boolean = false,
    val rawCode: String,
    val scannedAt: Long = System.currentTimeMillis(),
    // Assets scanned into this container while it was expanded, this session. Staged only —
    // not written to the DB (container_id reassigned) until the session is committed. Only
    // meaningful when container != null.
    val pendingAssets: List<Asset> = emptyList()
)

// Held while ScannerScreen asks the user to confirm scanning a second container in one session
data class PendingContainerScan(val container: ContainerWithDetails, val rawCode: String)

enum class ScanFlash { NONE, FOUND, NOT_FOUND, DUPLICATE }

// Step machine for the single-item, repeating "Check-In" mode (mode key "check_in_repeat") —
// distinct from the bulk check_in flow. Loops SCANNING_ITEM -> SCANNING_LOCATION -> auto-commit
// -> back to SCANNING_ITEM until the user taps "Done".
enum class RepeatCheckInStep { SCANNING_ITEM, SCANNING_LOCATION }

data class RepeatCheckInPair(
    val asset: Asset,
    val location: LocationModel,
    val committedAt: Long = System.currentTimeMillis(),
    // Snapshot of the asset immediately before this check-in — carried so an undo (see
    // revertRepeatCheckInPair) can restore exactly this, not just guess "available"/null.
    val previousStatus: String,
    val previousLocationId: String?,
    val previousUserId: String?,
    val previousUserName: String?,
    val previousExpectedReturnDate: String?
)

// Floating-card feedback for the single-item Check-In mode (see ScanResultCard.kt) — set on
// every item/location scan attempt, success or failure; the Composable clears it after a brief
// delay, same timing pattern as ScannerScreen's flash-then-clearFlash().
sealed class RepeatCheckInResult {
    data class Success(val pair: RepeatCheckInPair) : RepeatCheckInResult()
    data class Invalid(val message: String) : RepeatCheckInResult()
}

data class ScanUiState(
    val scannedItems: List<ScannedItem> = emptyList(),
    val committedItems: List<ScannedItem> = emptyList(),
    val skippedItems: List<ScannedItem> = emptyList(),
    val locations: List<LocationModel> = emptyList(),
    val locationId: String? = null,
    val isCommitting: Boolean = false,
    val flash: ScanFlash = ScanFlash.NONE,
    val lastEvent: InventoryEvent? = null,
    val commitError: String? = null,
    val gpsLat: Double? = null,
    val gpsLng: Double? = null,
    val gpsAddress: String? = null,
    val mode: String? = null,
    val scannerType: String? = null,
    val expandedContainerRawCode: String? = null,
    // Bulk Check-Out's "estimated return date" step.
    val expectedReturnDateMillis: Long? = null,
    // Resolved after a Bulk Check-In commit, purely for CommitResultScreen's summary copy.
    val committedLocationName: String? = null,
    // Single-item, repeating "Check-In" mode (mode key "check_in_repeat") — see RepeatCheckInStep.
    val repeatCheckInStep: RepeatCheckInStep = RepeatCheckInStep.SCANNING_ITEM,
    val repeatCheckInPendingAsset: Asset? = null,
    val repeatCheckInPairs: List<RepeatCheckInPair> = emptyList(),
    val repeatCheckInResult: RepeatCheckInResult? = null
)

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val repository: InventoryRepository,
    private val offlineRepository: OfflineRepository,
    private val sessionDataStore: SessionDataStore,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _state = MutableStateFlow(ScanUiState())
    val state: StateFlow<ScanUiState> = _state.asStateFlow()

    // One-shot events for duplicate snackbar
    private val _snackbarMessage = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val snackbarMessage: SharedFlow<String> = _snackbarMessage.asSharedFlow()

    private val _navigateToAsset = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val navigateToAsset: SharedFlow<String> = _navigateToAsset.asSharedFlow()

    val currentMember = sessionDataStore.currentMember

    val isOnline: StateFlow<Boolean> = offlineRepository.isOnline
    val pendingSyncCount: StateFlow<Int> = offlineRepository.pendingCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _pendingContainerConfirmation = MutableStateFlow<PendingContainerScan?>(null)
    val pendingContainerConfirmation: StateFlow<PendingContainerScan?> = _pendingContainerConfirmation.asStateFlow()

    private val _scanMode = MutableStateFlow("2D")
    val scanMode: StateFlow<String> = _scanMode.asStateFlow()
    private var previousScanMode = "2D"

    init {
        viewModelScope.launch {
            val saved = sessionDataStore.scanMode.first()
            _scanMode.value = saved
            previousScanMode = saved
        }
        viewModelScope.launch {
            MainActivity.scanModeFailure.collect { failedMode ->
                val message = if (failedMode == "MULTI") {
                    "Multi-barcode requires a Mobility DNA Enterprise license"
                } else {
                    "Failed to set scanner mode"
                }
                _scanMode.value = previousScanMode
                sessionDataStore.saveScanMode(previousScanMode)
                _snackbarMessage.emit(message)
            }
        }
    }

    fun setScanMode(mode: String, context: Context) {
        previousScanMode = _scanMode.value
        _scanMode.value = mode
        viewModelScope.launch { sessionDataStore.saveScanMode(mode) }
        DataWedgeManager(context).setScanMode(mode)
    }

    // Applies the DataStore-persisted mode on ScannerScreen open, before the user makes any
    // selection, so decoders are correct again after an app restart.
    suspend fun loadSavedScanMode(): String {
        val saved = sessionDataStore.scanMode.first()
        _scanMode.value = saved
        previousScanMode = saved
        return saved
    }

    fun processInquiryScan(code: String) {
        val trimmed = code.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            try {
                val asset = offlineRepository.getAssetByCode(trimmed)
                if (asset != null) {
                    _state.value = _state.value.copy(flash = ScanFlash.FOUND)
                    _navigateToAsset.emit(asset.assetId)
                } else {
                    _state.value = _state.value.copy(flash = ScanFlash.NOT_FOUND)
                    _snackbarMessage.emit("Asset not found: $trimmed")
                }
            } catch (_: Exception) {
                _state.value = _state.value.copy(flash = ScanFlash.NOT_FOUND)
                _snackbarMessage.emit("Asset not found: $trimmed")
            }
        }
    }

    // Bulk Check-Out's "estimated return date" step.
    fun setExpectedReturnDate(millis: Long) {
        _state.value = _state.value.copy(expectedReturnDateMillis = millis)
    }

    fun loadLocations() {
        viewModelScope.launch {
            try {
                val locs = offlineRepository.getLocations()
                _state.value = _state.value.copy(locations = locs)
            } catch (_: Exception) {}
        }
    }

    fun processScannedCode(code: String) {
        val trimmed = code.trim()
        if (trimmed.isEmpty()) return

        val usesContainerExpansion = _state.value.mode == "check_out" || _state.value.mode == "update"
        val expandedItem = _state.value.expandedContainerRawCode
            ?.let { rc -> _state.value.scannedItems.find { it.rawCode == rc && it.container != null } }

        viewModelScope.launch {
            try {
                val asset = offlineRepository.getAssetByCode(trimmed)
                if (asset != null) {
                    if (usesContainerExpansion && expandedItem != null) {
                        addAssetToExpandedContainer(expandedItem, asset)
                        return@launch
                    }
                    if (_state.value.scannedItems.any { it.rawCode == trimmed }) {
                        _state.value = _state.value.copy(flash = ScanFlash.DUPLICATE)
                        _snackbarMessage.tryEmit("Already added")
                        return@launch
                    }
                    addScannedItem(ScannedItem(asset = asset, rawCode = trimmed))
                    _state.value = _state.value.copy(flash = ScanFlash.FOUND)
                    return@launch
                }

                val container = offlineRepository.getContainerByCode(trimmed)
                if (container != null) {
                    val existing = _state.value.scannedItems.find { it.rawCode == trimmed }

                    if (usesContainerExpansion) {
                        // Re-scanning a container already in this session just reopens it —
                        // scanning a different one minimizes whichever was expanded and opens
                        // the new one. Never nests a container inside another.
                        if (existing == null) {
                            addScannedItem(ScannedItem(container = container, rawCode = trimmed))
                        }
                        _state.value = _state.value.copy(
                            expandedContainerRawCode = trimmed,
                            flash = ScanFlash.FOUND
                        )
                        return@launch
                    }

                    // check_in / mark_lost / other modes keep the original confirm-dialog
                    // behavior for a second container — unchanged.
                    if (existing != null) {
                        _state.value = _state.value.copy(flash = ScanFlash.DUPLICATE)
                        _snackbarMessage.tryEmit("Already added")
                        return@launch
                    }
                    val alreadyHasContainer = _state.value.scannedItems.any { it.container != null }
                    if (alreadyHasContainer) {
                        // Hold the scan — ScannerScreen shows a confirmation dialog and calls
                        // confirmAddContainer()/cancelAddContainer() based on the user's choice.
                        _pendingContainerConfirmation.value = PendingContainerScan(container, trimmed)
                        return@launch
                    }
                    addScannedItem(ScannedItem(container = container, rawCode = trimmed))
                    _state.value = _state.value.copy(flash = ScanFlash.FOUND)
                    return@launch
                }

                // Unknown — added to list with red styling
                if (_state.value.scannedItems.any { it.rawCode == trimmed }) {
                    _state.value = _state.value.copy(flash = ScanFlash.DUPLICATE)
                    _snackbarMessage.tryEmit("Already added")
                    return@launch
                }
                addScannedItem(ScannedItem(isUnknown = true, rawCode = trimmed))
                _state.value = _state.value.copy(flash = ScanFlash.NOT_FOUND)
            } catch (_: Exception) {
                addScannedItem(ScannedItem(isUnknown = true, rawCode = trimmed))
                _state.value = _state.value.copy(flash = ScanFlash.NOT_FOUND)
            }
        }
    }

    // Stages a scanned asset into the currently expanded container, moving it out of wherever
    // it currently is (a different container in the DB, or a flat top-level scan from earlier
    // in this same session) — matches "move it automatically" for cross-container conflicts.
    private fun addAssetToExpandedContainer(expandedItem: ScannedItem, asset: Asset) {
        val container = expandedItem.container!!
        val alreadyIn = container.assets.any { it.assetId == asset.assetId } ||
            expandedItem.pendingAssets.any { it.assetId == asset.assetId }
        if (alreadyIn) {
            _state.value = _state.value.copy(flash = ScanFlash.DUPLICATE)
            _snackbarMessage.tryEmit("Already in container ${container.container.containerId}")
            return
        }
        _state.value = _state.value.copy(
            scannedItems = _state.value.scannedItems
                .filter { it.asset?.assetId != asset.assetId }
                .map {
                    if (it.rawCode == expandedItem.rawCode) it.copy(pendingAssets = it.pendingAssets + asset) else it
                },
            flash = ScanFlash.FOUND
        )
    }

    fun toggleContainerExpanded(rawCode: String) {
        _state.value = _state.value.copy(
            expandedContainerRawCode = if (_state.value.expandedContainerRawCode == rawCode) null else rawCode
        )
    }

    fun removePendingAssetFromContainer(containerRawCode: String, assetId: String) {
        _state.value = _state.value.copy(
            scannedItems = _state.value.scannedItems.map {
                if (it.rawCode == containerRawCode) {
                    it.copy(pendingAssets = it.pendingAssets.filter { a -> a.assetId != assetId })
                } else it
            }
        )
    }

    fun confirmAddContainer() {
        val pending = _pendingContainerConfirmation.value ?: return
        _pendingContainerConfirmation.value = null
        addScannedItem(ScannedItem(container = pending.container, rawCode = pending.rawCode))
        _state.value = _state.value.copy(flash = ScanFlash.FOUND)
    }

    fun cancelAddContainer() {
        _pendingContainerConfirmation.value = null
        _state.value = _state.value.copy(flash = ScanFlash.DUPLICATE)
    }

    private fun addScannedItem(item: ScannedItem) {
        _state.value = _state.value.copy(
            scannedItems = listOf(item) + _state.value.scannedItems
        )
    }

    fun clearFlash() {
        _state.value = _state.value.copy(flash = ScanFlash.NONE)
    }

    fun setModeAndScannerType(mode: String, scannerType: String?) {
        _state.value = _state.value.copy(mode = mode, scannerType = scannerType)
    }

    fun clearModeAndScannerType() {
        _state.value = _state.value.copy(mode = null, scannerType = null)
    }

    fun removeItem(rawCode: String) {
        _state.value = _state.value.copy(
            scannedItems = _state.value.scannedItems.filter { it.rawCode != rawCode },
            expandedContainerRawCode = _state.value.expandedContainerRawCode
                ?.takeUnless { it == rawCode }
        )
    }

    fun setLocation(locationId: String) {
        _state.value = _state.value.copy(locationId = locationId)
    }

    fun updateGps(lat: Double, lng: Double) {
        viewModelScope.launch {
            val address = reverseGeocode(lat, lng)
            _state.value = _state.value.copy(gpsLat = lat, gpsLng = lng, gpsAddress = address)
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun reverseGeocode(lat: Double, lng: Double): String? =
        withContext(Dispatchers.IO) {
            try {
                Geocoder(appContext).getFromLocation(lat, lng, 1)
                    ?.firstOrNull()
                    ?.getAddressLine(0)
            } catch (_: Exception) { null }
        }

    // Silent, one-shot GPS fetch for Check In/Check Out/Update/Mark Lost — unlike
    // Update mode's continuous, visible tracking, this just grabs a single fix (or gives up after
    // 10s) so every mode can stamp last_known_* on commit without showing any GPS UI. No-ops if
    // permission isn't granted, or if this session already has a fix (e.g. Update mode's own
    // continuous tracking already populated gpsLat/gpsLng).
    @Suppress("DEPRECATION")
    fun captureGpsSnapshot() {
        if (_state.value.gpsLat != null) return
        val hasPermission = ContextCompat.checkSelfPermission(
            appContext, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) return

        viewModelScope.launch {
            val locationManager =
                appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return@launch
            val location = withTimeoutOrNull(10_000L) {
                suspendCancellableCoroutine<Location?> { cont ->
                    val provider = when {
                        locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                        locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                        else -> null
                    }
                    if (provider == null) {
                        cont.resume(null) { _, _, _ -> }
                        return@suspendCancellableCoroutine
                    }
                    val listener = object : LocationListener {
                        override fun onLocationChanged(loc: Location) {
                            locationManager.removeUpdates(this)
                            if (cont.isActive) cont.resume(loc) { _, _, _ -> }
                        }
                    }
                    try {
                        locationManager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                    } catch (_: SecurityException) {
                        if (cont.isActive) cont.resume(null) { _, _, _ -> }
                    }
                    cont.invokeOnCancellation { locationManager.removeUpdates(listener) }
                }
            }
            location?.let { updateGps(it.latitude, it.longitude) }
        }
    }

    fun loadLastEvent(assetUuid: String) {
        viewModelScope.launch {
            try {
                val event = repository.getLastEvent(assetUuid)
                _state.value = _state.value.copy(lastEvent = event)
            } catch (_: Exception) {}
        }
    }

    fun commitSession(mode: String, performedBy: TeamMember, onSuccess: (Int) -> Unit) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isCommitting = true, commitError = null)
            try {
                val allItems      = _state.value.scannedItems
                val containerItems = allItems.filter { it.container != null }
                val assetItems    = allItems.filter { it.asset != null }
                val unknownItems  = allItems.filter { it.isUnknown }
                val performedById = performedBy.clerkUserId ?: performedBy.id
                // Bulk Check-Out's "estimated return date" — collected on ReturnDateSelectScreen,
                // after scanning, immediately before this commit.
                val expectedReturnDateIso = _state.value.expectedReturnDateMillis
                    ?.let { java.time.Instant.ofEpochMilli(it).toString() }

                // Container commits also apply to every asset inside them — those assets are
                // NOT processed again individually below, even if they were somehow also scanned.
                containerItems.forEach { item ->
                    val container = item.container!!   // safe — filtered by container != null

                    // Assets scanned into this container while it was expanded are staged only —
                    // persist the container assignment now, before the container-wide operation
                    // runs, so it picks them up as members (this also "moves" them out of
                    // whatever container/session item they were in before).
                    item.pendingAssets.forEach { asset ->
                        offlineRepository.addAssetToContainer(asset.assetId, container.container.id, performedBy)
                    }

                    // check_out/check_in/update all go through the offline queue (survives no
                    // connectivity) — every mode that reaches commitSession maps to one of these.
                    val offlineOperationType = when (mode) {
                        "check_out" -> "CONTAINER_CHECK_OUT"
                        "check_in"  -> "CONTAINER_CHECK_IN"
                        "update"    -> "CONTAINER_UPDATE"
                        else        -> null
                    }

                    if (offlineOperationType != null) {
                        offlineRepository.commitContainerOperation(
                            operationType = offlineOperationType,
                            containerId   = container.container.id,
                            locationId    = when (mode) {
                                "check_in"  -> _state.value.locationId
                                "check_out" -> null
                                else        -> container.container.currentLocationId
                            },
                            locationName  = null,
                            gpsLat        = _state.value.gpsLat,
                            gpsLng        = _state.value.gpsLng,
                            gpsAddress    = _state.value.gpsAddress,
                            performedBy   = performedBy,
                            expectedReturnDate = if (mode == "check_out") expectedReturnDateIso else null
                        )
                    }
                }

                val containerAssetIds = containerItems
                    .flatMap { it.container!!.assets }
                    .map { it.assetId }
                    .toSet()

                assetItems
                    .filter { it.asset!!.assetId !in containerAssetIds }
                    .forEach { item ->
                        val asset = item.asset!!

                        // check_out/check_in/update/mark_lost all go through the offline queue
                        // (survives no connectivity) — every mode that reaches commitSession maps
                        // to one of these.
                        val offlineOperationType = when (mode) {
                            "check_out" -> "CHECK_OUT"
                            "check_in"  -> "CHECK_IN"
                            "update"    -> "UPDATE_LOCATION"
                            "mark_lost" -> "MARK_LOST"
                            else        -> null
                        }

                        if (offlineOperationType != null) {
                            offlineRepository.commitAssetOperation(
                                operationType = offlineOperationType,
                                assetId       = asset.assetId,
                                locationId    = when (mode) {
                                    "check_in"  -> _state.value.locationId
                                    "check_out" -> null
                                    else        -> asset.currentLocationId
                                },
                                locationName  = null,
                                gpsLat        = _state.value.gpsLat,
                                gpsLng        = _state.value.gpsLng,
                                gpsAddress    = _state.value.gpsAddress,
                                performedBy   = performedBy,
                                expectedReturnDate = if (mode == "check_out") expectedReturnDateIso else null
                            )
                        }
                    }

                // Bulk Check-In commits every scanned item to a single location — resolve its
                // name once here, purely for CommitResultScreen's summary copy.
                val committedLocationName = if (mode == "check_in") {
                    _state.value.locationId?.let { locId -> offlineRepository.getLocationById(locId)?.name }
                } else null

                val committedItems = containerItems + assetItems
                _state.value = _state.value.copy(
                    isCommitting   = false,
                    committedItems = committedItems,
                    skippedItems   = unknownItems,
                    scannedItems   = emptyList(),
                    mode           = null,
                    scannerType    = null,
                    expandedContainerRawCode = null,
                    committedLocationName = committedLocationName
                )
                onSuccess(committedItems.size)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isCommitting = false,
                    commitError  = e.message ?: "Commit failed"
                )
            }
        }
    }

    fun resetSession() {
        _state.value = ScanUiState()
    }

    // ── Single-item, repeating "Check-In" mode (mode key "check_in_repeat") ────────────────────
    // SCANNING_ITEM -> SCANNING_LOCATION -> auto-commit that pair -> back to SCANNING_ITEM,
    // looping until the user taps "Done" (finishRepeatCheckIn). Distinct from the bulk check_in
    // flow above, which stays on processScannedCode/commitSession.

    fun processRepeatCheckInItemScan(code: String) {
        val trimmed = code.trim()
        if (trimmed.isEmpty() || _state.value.repeatCheckInStep != RepeatCheckInStep.SCANNING_ITEM) return
        viewModelScope.launch {
            try {
                val asset = offlineRepository.getAssetByCode(trimmed)
                if (asset != null) {
                    _state.value = _state.value.copy(
                        repeatCheckInPendingAsset = asset,
                        repeatCheckInStep = RepeatCheckInStep.SCANNING_LOCATION,
                        flash = ScanFlash.FOUND
                    )
                } else {
                    _state.value = _state.value.copy(
                        flash = ScanFlash.NOT_FOUND,
                        repeatCheckInResult = RepeatCheckInResult.Invalid("Not a known asset: $trimmed")
                    )
                }
            } catch (_: Exception) {
                _state.value = _state.value.copy(
                    flash = ScanFlash.NOT_FOUND,
                    repeatCheckInResult = RepeatCheckInResult.Invalid("Not a known asset: $trimmed")
                )
            }
        }
    }

    fun processRepeatCheckInLocationScan(code: String) {
        val trimmed = code.trim()
        if (trimmed.isEmpty() || _state.value.repeatCheckInStep != RepeatCheckInStep.SCANNING_LOCATION) return
        viewModelScope.launch {
            try {
                val result = offlineRepository.getLocationByCode(trimmed)
                if (result != null) {
                    commitRepeatCheckInPair(result.location)
                } else {
                    _state.value = _state.value.copy(
                        flash = ScanFlash.NOT_FOUND,
                        repeatCheckInResult = RepeatCheckInResult.Invalid("Unknown location: $trimmed")
                    )
                }
            } catch (_: Exception) {
                _state.value = _state.value.copy(
                    flash = ScanFlash.NOT_FOUND,
                    repeatCheckInResult = RepeatCheckInResult.Invalid("Unknown location: $trimmed")
                )
            }
        }
    }

    // Dropdown-picker fallback for SCANNING_LOCATION, alongside the scan/manual-entry path above.
    fun selectRepeatCheckInLocation(location: LocationModel) {
        if (_state.value.repeatCheckInStep != RepeatCheckInStep.SCANNING_LOCATION) return
        viewModelScope.launch { commitRepeatCheckInPair(location) }
    }

    private suspend fun commitRepeatCheckInPair(location: LocationModel) {
        val asset = _state.value.repeatCheckInPendingAsset ?: return
        val member = currentMember.first() ?: return
        _state.value = _state.value.copy(isCommitting = true)
        try {
            offlineRepository.commitAssetOperation(
                operationType = "CHECK_IN",
                assetId       = asset.assetId,
                locationId    = location.id,
                locationName  = location.name,
                gpsLat        = _state.value.gpsLat,
                gpsLng        = _state.value.gpsLng,
                gpsAddress    = _state.value.gpsAddress,
                performedBy   = member
            )
            // Snapshot is the asset AS FETCHED at the item-scan step, i.e. its state immediately
            // before this check-in — exactly what an undo needs to restore.
            val pair = RepeatCheckInPair(
                asset = asset,
                location = location,
                previousStatus = asset.status,
                previousLocationId = asset.currentLocationId,
                previousUserId = asset.currentUserId,
                previousUserName = asset.currentUserName,
                previousExpectedReturnDate = asset.expectedReturnDate
            )
            _state.value = _state.value.copy(
                repeatCheckInPairs = listOf(pair) + _state.value.repeatCheckInPairs,
                repeatCheckInPendingAsset = null,
                repeatCheckInStep = RepeatCheckInStep.SCANNING_ITEM,
                isCommitting = false,
                flash = ScanFlash.FOUND,
                repeatCheckInResult = RepeatCheckInResult.Success(pair)
            )
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                isCommitting = false,
                commitError  = e.message ?: "Check-in failed",
                repeatCheckInResult = RepeatCheckInResult.Invalid(e.message ?: "Check-in failed")
            )
        }
    }

    // Clears the transient floating-card result — called by RepeatCheckInScreen after its brief
    // display delay, same pattern as clearFlash().
    fun clearRepeatCheckInResult() {
        _state.value = _state.value.copy(repeatCheckInResult = null)
    }

    // Undoes one committed pair from this session's running log — restores the asset to exactly
    // what it was before that check-in (see InventoryRepository.revertCheckIn) and removes the
    // row from the log. The audit trail itself isn't touched; this appends a correction event
    // rather than deleting the original "checkin" event.
    fun revertRepeatCheckInPair(pair: RepeatCheckInPair) {
        viewModelScope.launch {
            val member = currentMember.first() ?: return@launch
            try {
                offlineRepository.revertCheckIn(
                    assetId = pair.asset.assetId,
                    previousStatus = pair.previousStatus,
                    previousLocationId = pair.previousLocationId,
                    previousUserId = pair.previousUserId,
                    previousUserName = pair.previousUserName,
                    previousExpectedReturnDate = pair.previousExpectedReturnDate,
                    performedBy = member
                )
                _state.value = _state.value.copy(
                    repeatCheckInPairs = _state.value.repeatCheckInPairs.filter { it.committedAt != pair.committedAt }
                )
            } catch (e: Exception) {
                _snackbarMessage.tryEmit(e.message ?: "Failed to revert check-in")
            }
        }
    }

    // "Done" — ends the session. Committed pairs are mapped into committedItems so
    // CommitResultScreen (shared with every other mode) can render them unchanged.
    fun finishRepeatCheckIn(onDone: (Int) -> Unit) {
        val committedItems = _state.value.repeatCheckInPairs.map { pair ->
            ScannedItem(asset = pair.asset, rawCode = pair.asset.assetId)
        }
        _state.value = _state.value.copy(
            committedItems = committedItems,
            skippedItems = emptyList(),
            repeatCheckInPairs = emptyList(),
            repeatCheckInPendingAsset = null,
            repeatCheckInStep = RepeatCheckInStep.SCANNING_ITEM,
            repeatCheckInResult = null,
            mode = null,
            scannerType = null
        )
        onDone(committedItems.size)
    }
}
