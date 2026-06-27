package com.lfcreative.lfscan.ui.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lfcreative.lfscan.data.repository.InventoryRepository
import com.lfcreative.lfscan.session.SessionDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class PinUiState {
    object Idle : PinUiState()
    object Loading : PinUiState()
    object Success : PinUiState()
    object InvalidPin : PinUiState()
    data class Error(val message: String) : PinUiState()
}

@HiltViewModel
class PinViewModel @Inject constructor(
    private val repository: InventoryRepository,
    private val sessionDataStore: SessionDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow<PinUiState>(PinUiState.Idle)
    val uiState: StateFlow<PinUiState> = _uiState.asStateFlow()

    fun confirmPin(pin: String) {
        viewModelScope.launch {
            _uiState.value = PinUiState.Loading
            try {
                val member = repository.getTeamMemberByPin(pin)
                if (member != null) {
                    sessionDataStore.saveSession(member)
                    _uiState.value = PinUiState.Success
                } else {
                    _uiState.value = PinUiState.InvalidPin
                }
            } catch (e: Exception) {
                _uiState.value = PinUiState.Error(e.message ?: "Connection error")
            }
        }
    }

    fun resetState() {
        _uiState.value = PinUiState.Idle
    }
}
