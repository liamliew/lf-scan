package com.lfcreative.lfscan.ui.screen

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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ScannedItem(
    val asset: Asset,
    val timestamp: Long = System.currentTimeMillis()
)

enum class ScanFlash { NONE, FOUND, NOT_FOUND, DUPLICATE }

data class ScanUiState(
    val scannedItems: List<ScannedItem> = emptyList(),
    val committedItems: List<ScannedItem> = emptyList(),
    val locations: List<Location> = emptyList(),
    val locationId: String? = null,
    val isCommitting: Boolean = false,
    val flash: ScanFlash = ScanFlash.NONE,
    val lastEvent: InventoryEvent? = null,
    val commitError: String? = null
)

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val repository: InventoryRepository,
    private val sessionDataStore: SessionDataStore
) : ViewModel() {

    private val _state = MutableStateFlow(ScanUiState())
    val state: StateFlow<ScanUiState> = _state.asStateFlow()

    val currentMember = sessionDataStore.currentMember

    fun loadLocations() {
        viewModelScope.launch {
            try {
                val locs = repository.getLocations()
                _state.value = _state.value.copy(locations = locs)
            } catch (_: Exception) {}
        }
    }

    fun processBarcode(code: String) {
        if (_state.value.scannedItems.any { it.asset.assetId == code }) {
            _state.value = _state.value.copy(flash = ScanFlash.DUPLICATE)
            return
        }
        viewModelScope.launch {
            try {
                val asset = repository.getAssetByCode(code)
                if (asset != null) {
                    _state.value = _state.value.copy(
                        scannedItems = _state.value.scannedItems + ScannedItem(asset),
                        flash = ScanFlash.FOUND
                    )
                } else {
                    _state.value = _state.value.copy(flash = ScanFlash.NOT_FOUND)
                }
            } catch (_: Exception) {
                _state.value = _state.value.copy(flash = ScanFlash.NOT_FOUND)
            }
        }
    }

    fun clearFlash() {
        _state.value = _state.value.copy(flash = ScanFlash.NONE)
    }

    fun removeItem(assetId: String) {
        _state.value = _state.value.copy(
            scannedItems = _state.value.scannedItems.filter { it.asset.assetId != assetId }
        )
    }

    fun setLocation(locationId: String) {
        _state.value = _state.value.copy(locationId = locationId)
    }

    fun loadLastEvent(assetUuid: String) {
        viewModelScope.launch {
            try {
                val event = repository.getLastEvent(assetUuid)
                _state.value = _state.value.copy(lastEvent = event)
            } catch (_: Exception) {}
        }
    }

    fun commit(mode: String, performedBy: TeamMember, onSuccess: (Int) -> Unit) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isCommitting = true, commitError = null)
            try {
                val items = _state.value.scannedItems
                items.forEach { item ->
                    val (newStatus, eventType) = when (mode) {
                        "check_out" -> "checked_out" to "check_out"
                        "check_in"  -> "available"   to "check_in"
                        "update"    -> item.asset.status to "update"
                        else        -> item.asset.status to "inquiry"
                    }
                    repository.updateAssetStatus(
                        assetId   = item.asset.assetId,
                        status    = newStatus,
                        locationId = when (mode) {
                            "check_in" -> _state.value.locationId
                            else       -> item.asset.currentLocationId
                        },
                        userId   = if (mode == "check_out") performedBy.id else null,
                        userName = if (mode == "check_out") performedBy.name else null
                    )
                    repository.insertEvent(
                        InventoryEventInsert(
                            assetId        = item.asset.id,
                            eventType      = eventType,
                            performedBy    = performedBy.clerkUserId ?: performedBy.id,
                            performedByName = performedBy.name,
                            locationId     = _state.value.locationId
                        )
                    )
                }
                _state.value = _state.value.copy(
                    isCommitting   = false,
                    committedItems = items,
                    scannedItems   = emptyList()
                )
                onSuccess(items.size)
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
