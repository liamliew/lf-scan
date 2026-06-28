package com.lfcreative.lfscan.ui.screen

import androidx.lifecycle.SavedStateHandle
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AssetDetailViewModel @Inject constructor(
    private val repository: InventoryRepository,
    sessionDataStore: SessionDataStore,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val assetId: String = savedStateHandle.get<String>("assetId") ?: ""

    val currentMember = sessionDataStore.currentMember

    private val _asset = MutableStateFlow<Asset?>(null)
    private val _location = MutableStateFlow<Location?>(null)
    private val _locations = MutableStateFlow<List<Location>>(emptyList())
    private val _events = MutableStateFlow<List<InventoryEvent>>(emptyList())
    private val _locationMap = MutableStateFlow<Map<String, String>>(emptyMap())
    private val _isLoading = MutableStateFlow(false)
    private val _isEditing = MutableStateFlow(false)
    private val _isSaving = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)
    private val _snackbarMessage = MutableSharedFlow<String>(extraBufferCapacity = 4)

    val asset: StateFlow<Asset?> = _asset.asStateFlow()
    val location: StateFlow<Location?> = _location.asStateFlow()
    val locations: StateFlow<List<Location>> = _locations.asStateFlow()
    val events: StateFlow<List<InventoryEvent>> = _events.asStateFlow()
    val locationMap: StateFlow<Map<String, String>> = _locationMap.asStateFlow()
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    val isEditing: StateFlow<Boolean> = _isEditing.asStateFlow()
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()
    val error: StateFlow<String?> = _error.asStateFlow()
    val snackbarMessage: SharedFlow<String> = _snackbarMessage.asSharedFlow()

    init {
        if (assetId.isNotEmpty()) load()
    }

    private fun load() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val asset = repository.getAssetByCode(assetId)
                _asset.value = asset

                val allLocations = repository.getLocations()
                _locations.value = allLocations
                _locationMap.value = allLocations.associate { it.id to it.name }

                if (asset?.currentLocationId != null) {
                    _location.value = allLocations.find { it.id == asset.currentLocationId }
                } else {
                    _location.value = null
                }

                _events.value = repository.getEventsByAssetId(assetId)
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load asset"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun toggleEdit() {
        _isEditing.value = !_isEditing.value
    }

    fun cancelEdit() {
        _isEditing.value = false
    }

    fun saveAsset(updated: Asset, performedBy: TeamMember) {
        viewModelScope.launch {
            _isSaving.value = true
            _error.value = null
            try {
                repository.updateAssetFull(updated)
                repository.insertEvent(
                    InventoryEventInsert(
                        assetId         = updated.assetId,
                        eventType       = "edited",
                        performedBy     = performedBy.clerkUserId ?: performedBy.id,
                        performedByName = performedBy.name
                    )
                )
                load()
                _isEditing.value = false
                _snackbarMessage.emit("Asset updated")
            } catch (e: Exception) {
                _error.value = e.message ?: "Save failed"
            } finally {
                _isSaving.value = false
            }
        }
    }
}
