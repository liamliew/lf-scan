package com.lfcreative.lfscan.ui.screen

import android.content.Context
import android.location.Geocoder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lfcreative.lfscan.MainActivity
import com.lfcreative.lfscan.data.model.Asset
import com.lfcreative.lfscan.data.model.ContainerWithDetails
import com.lfcreative.lfscan.data.model.InventoryEvent
import com.lfcreative.lfscan.data.model.InventoryEventInsert
import com.lfcreative.lfscan.data.model.Location
import com.lfcreative.lfscan.data.model.TeamMember
import com.lfcreative.lfscan.data.repository.InventoryRepository
import com.lfcreative.lfscan.session.SessionDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

data class ScanUiState(
    val scannedItems: List<ScannedItem> = emptyList(),
    val committedItems: List<ScannedItem> = emptyList(),
    val skippedItems: List<ScannedItem> = emptyList(),
    val locations: List<Location> = emptyList(),
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
    // Rental details, collected up front on RentDetailsScreen before scanning starts (rent_out
    // mode only) — renterName doubles as the asset's current_user_name while it's rented out.
    val renterName: String = "",
    val renterContact: String = "",
    val rentalDueDateMillis: Long? = null,
    // rawCode of the container currently "expanded" (check_out/update modes only) — scanning an
    // asset while a container is expanded stages it into that container instead of adding a new
    // top-level item. Only one container can be expanded at a time.
    val expandedContainerRawCode: String? = null
)

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val repository: InventoryRepository,
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
                val asset = repository.getAssetByCode(trimmed)
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

    fun setRenterDetails(name: String, contact: String, dueDateMillis: Long) {
        _state.value = _state.value.copy(
            renterName = name.trim(),
            renterContact = contact.trim(),
            rentalDueDateMillis = dueDateMillis
        )
    }

    fun loadLocations() {
        viewModelScope.launch {
            try {
                val locs = repository.getLocations()
                _state.value = _state.value.copy(locations = locs)
            } catch (_: Exception) {}
        }
    }

    fun processScannedCode(code: String) {
        val trimmed = code.trim()
        if (trimmed.isEmpty()) return

        val usesContainerExpansion = _state.value.mode == "check_out" || _state.value.mode == "update" || _state.value.mode == "rent_out"
        val expandedItem = _state.value.expandedContainerRawCode
            ?.let { rc -> _state.value.scannedItems.find { it.rawCode == rc && it.container != null } }

        viewModelScope.launch {
            try {
                val asset = repository.getAssetByCode(trimmed)
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

                val container = repository.getContainerByCode(trimmed)
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
                // rent_out details were collected up front on RentDetailsScreen, before scanning
                // started, and hang off session state rather than any one scanned item.
                val dueDateIso = _state.value.rentalDueDateMillis
                    ?.let { java.time.Instant.ofEpochMilli(it).toString() }
                val renterName = _state.value.renterName
                val renterContact = _state.value.renterContact.ifBlank { null }

                // Container commits also apply to every asset inside them — those assets are
                // NOT processed again individually below, even if they were somehow also scanned.
                containerItems.forEach { item ->
                    val container = item.container!!   // safe — filtered by container != null

                    // Assets scanned into this container while it was expanded are staged only —
                    // persist the container assignment now, before the container-wide operation
                    // runs, so it picks them up as members (this also "moves" them out of
                    // whatever container/session item they were in before).
                    item.pendingAssets.forEach { asset ->
                        repository.addAssetToContainer(asset.assetId, container.container.id, performedBy)
                    }

                    val operationMode = when (mode) {
                        "check_out" -> "check_out"
                        "check_in"  -> "check_in"
                        "update"    -> "update_location"
                        "rent_out"  -> "rent_out"
                        else        -> null
                    } ?: return@forEach
                    repository.commitContainerOperation(
                        containerId = container.container.id,
                        mode        = operationMode,
                        locationId  = when (mode) {
                            "check_in"  -> _state.value.locationId
                            "check_out", "rent_out" -> null
                            else        -> container.container.currentLocationId
                        },
                        userId        = performedById,
                        userName      = performedBy.name,
                        gpsLat        = _state.value.gpsLat,
                        gpsLng        = _state.value.gpsLng,
                        gpsAddress    = _state.value.gpsAddress,
                        holderName    = if (mode == "rent_out") renterName else null,
                        renterContact = if (mode == "rent_out") renterContact else null,
                        rentalDueDate = if (mode == "rent_out") dueDateIso else null
                    )
                }

                val containerAssetIds = containerItems
                    .flatMap { it.container!!.assets }
                    .map { it.assetId }
                    .toSet()

                assetItems
                    .filter { it.asset!!.assetId !in containerAssetIds }
                    .forEach { item ->
                        val asset = item.asset!!
                        val (newStatus, eventType) = when (mode) {
                            "check_out" -> "checked_out" to "checkout"
                            "check_in"  -> "available"   to "checkin"
                            "update"    -> asset.status  to "location"
                            "mark_lost" -> "lost"        to "lost"
                            "rent_out"  -> "rented"      to "rented"
                            else        -> asset.status  to "inquiry"
                        }
                        repository.updateAssetStatus(
                            assetId    = asset.assetId,
                            status     = newStatus,
                            locationId = when (mode) {
                                "check_in"  -> _state.value.locationId
                                "check_out", "rent_out" -> null
                                else        -> asset.currentLocationId
                            },
                            // Only check-out/rent-out assign a holder and check-in clears one;
                            // every other mode (update, mark_lost, inquiry) must leave the
                            // existing holder alone — an asset stays "with" whoever has it until
                            // it's actually checked back in.
                            userId   = when (mode) {
                                "check_out" -> performedBy.id
                                "check_in"  -> null
                                "rent_out"  -> null
                                else        -> asset.currentUserId
                            },
                            userName = when (mode) {
                                "check_out" -> performedBy.name
                                "check_in"  -> null
                                "rent_out"  -> renterName
                                else        -> asset.currentUserName
                            },
                            renterContact = when (mode) {
                                "rent_out"                -> renterContact
                                "check_in", "check_out"   -> null
                                else                       -> asset.renterContact
                            },
                            rentalDueDate = when (mode) {
                                "rent_out"                -> dueDateIso
                                "check_in", "check_out"   -> null
                                else                       -> asset.rentalDueDate
                            }
                        )
                        repository.insertEvent(
                            InventoryEventInsert(
                                assetId         = asset.assetId,
                                eventType       = eventType,
                                performedBy     = performedById,
                                performedByName = performedBy.name,
                                locationId      = _state.value.locationId,
                                gpsLat          = _state.value.gpsLat,
                                gpsLng          = _state.value.gpsLng,
                                gpsAddress      = _state.value.gpsAddress
                            )
                        )
                    }

                val committedItems = containerItems + assetItems
                _state.value = _state.value.copy(
                    isCommitting   = false,
                    committedItems = committedItems,
                    skippedItems   = unknownItems,
                    scannedItems   = emptyList(),
                    mode           = null,
                    scannerType    = null,
                    expandedContainerRawCode = null
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
}
