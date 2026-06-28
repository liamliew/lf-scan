package com.lfcreative.lfscan.ui.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lfcreative.lfscan.data.model.Asset
import com.lfcreative.lfscan.data.repository.InventoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AssetsViewModel @Inject constructor(
    private val repository: InventoryRepository
) : ViewModel() {

    private val _assets = MutableStateFlow<List<Asset>>(emptyList())
    private val _isLoading = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)
    private val _searchQuery = MutableStateFlow("")
    private val _selectedStatus = MutableStateFlow<String?>(null)

    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    val error: StateFlow<String?> = _error.asStateFlow()
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    val selectedStatus: StateFlow<String?> = _selectedStatus.asStateFlow()

    val filteredAssets: StateFlow<List<Asset>> = combine(
        _assets, _searchQuery, _selectedStatus
    ) { assets, query, status ->
        assets
            .let { list -> if (status != null) list.filter { it.status == status } else list }
            .let { list ->
                if (query.isEmpty()) list
                else list.filter {
                    it.name.contains(query, ignoreCase = true) ||
                    it.assetId.contains(query, ignoreCase = true)
                }
            }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        loadAssets()
    }

    fun loadAssets() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                _assets.value = repository.getAllAssets()
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load assets"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setStatusFilter(status: String?) {
        _selectedStatus.value = status
    }
}
