package com.lfcreative.lfscan.ui.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lfcreative.lfscan.data.model.TeamMember
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
    data class AwaitingPassword(val member: TeamMember) : PinUiState()
    object Success : PinUiState()
    object InvalidId : PinUiState()
    object InvalidPassword : PinUiState()
    data class Error(val message: String) : PinUiState()
}

@HiltViewModel
class PinViewModel @Inject constructor(
    private val repository: InventoryRepository,
    private val sessionDataStore: SessionDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow<PinUiState>(PinUiState.Idle)
    val uiState: StateFlow<PinUiState> = _uiState.asStateFlow()

    // Used by ContinueSessionScreen to show who was last signed in, without needing its own ViewModel.
    val currentMember = sessionDataStore.currentMember

    // The member identified in Step 1, held while Step 2 (password) is in progress.
    private var pendingMember: TeamMember? = null

    // Step 1 — also used by the camera/NFC/hardware-scanner ID inputs, which encode the same
    // 4-digit ID as the numpad. If the member has no password set, log in immediately
    // (backwards compatible); otherwise move to Step 2.
    fun submitId(id: String) {
        viewModelScope.launch {
            _uiState.value = PinUiState.Loading
            try {
                val member = repository.getTeamMemberByPin(id)
                when {
                    member == null -> _uiState.value = PinUiState.InvalidId
                    member.password.isNullOrBlank() -> {
                        pendingMember = null
                        sessionDataStore.saveSession(member)
                        _uiState.value = PinUiState.Success
                    }
                    else -> {
                        pendingMember = member
                        _uiState.value = PinUiState.AwaitingPassword(member)
                    }
                }
            } catch (e: Exception) {
                _uiState.value = PinUiState.Error(e.message ?: "Connection error")
            }
        }
    }

    // Step 2 — password entered via numpad or scanned via the internal/external hardware scanner.
    fun submitPassword(password: String) {
        val member = pendingMember ?: return
        viewModelScope.launch {
            _uiState.value = PinUiState.Loading
            try {
                val match = repository.getTeamMemberByPinAndPassword(member.pin, password)
                if (match != null) {
                    pendingMember = null
                    sessionDataStore.saveSession(match)
                    _uiState.value = PinUiState.Success
                } else {
                    _uiState.value = PinUiState.InvalidPassword
                }
            } catch (e: Exception) {
                _uiState.value = PinUiState.Error(e.message ?: "Connection error")
            }
        }
    }

    // Lets a hardware scan fill the password field then submit it in one step.
    fun setPasswordFromScan(value: String) {
        submitPassword(value)
    }

    fun backToIdStep() {
        pendingMember = null
        _uiState.value = PinUiState.Idle
    }

    fun resetState() {
        _uiState.value = pendingMember?.let { PinUiState.AwaitingPassword(it) } ?: PinUiState.Idle
    }

    // Seeds the password step directly from an already-persisted session (skips Step 1 entirely)
    // so ContinueSessionScreen can reuse submitPassword/setPasswordFromScan as-is.
    fun prepareForContinue(member: TeamMember) {
        pendingMember = member
        _uiState.value = PinUiState.AwaitingPassword(member)
    }

    // "Return to Login" from the Continue Session screen — an explicit user choice to switch
    // accounts, so unlike a failed/expired session this clears the persisted session outright.
    fun clearSessionForSwitchUser() {
        viewModelScope.launch { sessionDataStore.clearSession() }
        pendingMember = null
        _uiState.value = PinUiState.Idle
    }
}
