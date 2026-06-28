package com.lfcreative.lfscan.ui.screen

import android.content.Context
import android.location.Geocoder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lfcreative.lfscan.data.model.Asset
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ScannedItem(
    val asset: Asset?,
    val isUnknown: Boolean = false,
    val rawCode: String,
    val scannedAt: Long = System.currentTimeMillis()
)

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
    val gpsAddress: String? = null
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

    val currentMember = sessionDataStore.currentMember

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

        // Duplicate check covers both known and unknown items
        if (_state.value.scannedItems.any { it.rawCode == trimmed }) {
            _state.value = _state.value.copy(flash = ScanFlash.DUPLICATE)
            _snackbarMessage.tryEmit("Already added")
            return
        }

        viewModelScope.launch {
            try {
                val asset = repository.getAssetByCode(trimmed)
                if (asset != null) {
                    _state.value = _state.value.copy(
                        scannedItems = _state.value.scannedItems + ScannedItem(
                            asset = asset,
                            isUnknown = false,
                            rawCode = trimmed
                        ),
                        flash = ScanFlash.FOUND
                    )
                } else {
                    // Unknown — added to list with red styling
                    _state.value = _state.value.copy(
                        scannedItems = _state.value.scannedItems + ScannedItem(
                            asset = null,
                            isUnknown = true,
                            rawCode = trimmed
                        ),
                        flash = ScanFlash.NOT_FOUND
                    )
                }
            } catch (_: Exception) {
                _state.value = _state.value.copy(
                    scannedItems = _state.value.scannedItems + ScannedItem(
                        asset = null,
                        isUnknown = true,
                        rawCode = trimmed
                    ),
                    flash = ScanFlash.NOT_FOUND
                )
            }
        }
    }

    fun clearFlash() {
        _state.value = _state.value.copy(flash = ScanFlash.NONE)
    }

    fun removeItem(rawCode: String) {
        _state.value = _state.value.copy(
            scannedItems = _state.value.scannedItems.filter { it.rawCode != rawCode }
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
                val allItems    = _state.value.scannedItems
                val knownItems  = allItems.filter { !it.isUnknown }
                val unknownItems = allItems.filter { it.isUnknown }

                knownItems.forEach { item ->
                    val asset = item.asset!!     // safe — !isUnknown guarantees non-null
                    val (newStatus, eventType) = when (mode) {
                        "check_out" -> "checked_out" to "check_out"
                        "check_in"  -> "available"   to "check_in"
                        "update"    -> asset.status  to "update"
                        "mark_lost" -> "lost"        to "mark_lost"
                        else        -> asset.status  to "inquiry"
                    }
                    repository.updateAssetStatus(
                        assetId    = asset.assetId,
                        status     = newStatus,
                        locationId = when (mode) {
                            "check_in"  -> _state.value.locationId
                            "check_out" -> null
                            else        -> asset.currentLocationId
                        },
                        userId   = if (mode == "check_out") performedBy.id else null,
                        userName = if (mode == "check_out") performedBy.name else null
                    )
                    repository.insertEvent(
                        InventoryEventInsert(
                            assetId         = asset.id,
                            eventType       = eventType,
                            performedBy     = performedBy.clerkUserId ?: performedBy.id,
                            performedByName = performedBy.name,
                            locationId      = _state.value.locationId,
                            gpsLat          = _state.value.gpsLat,
                            gpsLng          = _state.value.gpsLng,
                            gpsAddress      = _state.value.gpsAddress
                        )
                    )
                }

                _state.value = _state.value.copy(
                    isCommitting   = false,
                    committedItems = knownItems,
                    skippedItems   = unknownItems,
                    scannedItems   = emptyList()
                )
                onSuccess(knownItems.size)
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
