package com.lfcreative.lfscan.ui.screen

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lfcreative.lfscan.data.model.Asset
import com.lfcreative.lfscan.data.model.Container
import com.lfcreative.lfscan.data.model.InventoryEvent
import com.lfcreative.lfscan.data.model.InventoryEventInsert
import com.lfcreative.lfscan.data.model.Location
import com.lfcreative.lfscan.data.model.TeamMember
import com.lfcreative.lfscan.data.offline.OfflineRepository
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
    private val offlineRepository: OfflineRepository,
    sessionDataStore: SessionDataStore,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val assetId: String = savedStateHandle.get<String>("assetId") ?: ""

    val currentMember = sessionDataStore.currentMember

    private val _asset = MutableStateFlow<Asset?>(null)
    private val _location = MutableStateFlow<Location?>(null)
    private val _locations = MutableStateFlow<List<Location>>(emptyList())
    private val _container = MutableStateFlow<Container?>(null)
    private val _events = MutableStateFlow<List<InventoryEvent>>(emptyList())
    private val _locationMap = MutableStateFlow<Map<String, String>>(emptyMap())
    private val _isLoading = MutableStateFlow(false)
    private val _isEditing = MutableStateFlow(false)
    private val _isSaving = MutableStateFlow(false)
    private val _isRenamingId = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)
    private val _snackbarMessage = MutableSharedFlow<String>(extraBufferCapacity = 4)

    val asset: StateFlow<Asset?> = _asset.asStateFlow()
    val location: StateFlow<Location?> = _location.asStateFlow()
    val locations: StateFlow<List<Location>> = _locations.asStateFlow()
    val container: StateFlow<Container?> = _container.asStateFlow()
    val events: StateFlow<List<InventoryEvent>> = _events.asStateFlow()
    val locationMap: StateFlow<Map<String, String>> = _locationMap.asStateFlow()
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    val isEditing: StateFlow<Boolean> = _isEditing.asStateFlow()
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()
    val isRenamingId: StateFlow<Boolean> = _isRenamingId.asStateFlow()
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
                val asset = offlineRepository.getAssetByCode(assetId)
                _asset.value = asset

                val allLocations = offlineRepository.getLocations()
                _locations.value = allLocations
                _locationMap.value = allLocations.associate { it.id to it.name }

                if (asset?.currentLocationId != null) {
                    _location.value = allLocations.find { it.id == asset.currentLocationId }
                } else {
                    _location.value = null
                }

                _container.value = asset?.containerId?.let { offlineRepository.getContainerById(it)?.container }

                try {
                    _events.value = repository.getEventsByAssetId(assetId)
                } catch (_: Exception) {
                    _events.value = emptyList()
                }
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

    fun removeFromContainer(performedBy: TeamMember) {
        val currentAsset = _asset.value ?: return
        val containerId = currentAsset.containerId ?: return
        viewModelScope.launch {
            try {
                offlineRepository.removeAssetFromContainer(
                    assetId = currentAsset.assetId,
                    containerId = containerId,
                    performedBy = performedBy,
                    forced = true
                )
                load()
                _snackbarMessage.emit("Removed from container")
            } catch (e: Exception) {
                _snackbarMessage.emit(e.message ?: "Failed to remove from container")
            }
        }
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

    // Addendum — a container's asset code and a plain asset's code are the same concept (a
    // container is always also an asset), so this one entry point covers both. Doesn't reload
    // `this` instance afterward: assetId (above) is fixed for the lifetime of this ViewModel
    // (SavedStateHandle-backed, per this screen's "load fresh by id" convention), so the caller is
    // expected to navigate to the renamed asset's OWN detail route on success — a fresh ViewModel
    // instance then loads it normally. Every FK into inventory_assets(asset_id) has ON UPDATE
    // CASCADE (see the add_on_update_cascade_for_asset_id_fks migration), so the single repository
    // call is enough — no follow-up writes needed here for events/linked locations/etc.
    fun renameAssetId(newId: String, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        val current = _asset.value ?: return
        viewModelScope.launch {
            _isRenamingId.value = true
            try {
                repository.renameAssetId(current.assetId, newId)
                onSuccess(newId.trim())
            } catch (e: Exception) {
                onError(e.message ?: "Failed to change ID")
            } finally {
                _isRenamingId.value = false
            }
        }
    }
}
