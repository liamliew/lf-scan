package com.lfcreative.lfscan.ui.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lfcreative.lfscan.session.SessionDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ModeSelectViewModel @Inject constructor(
    private val sessionDataStore: SessionDataStore
) : ViewModel() {

    val currentMember = sessionDataStore.currentMember

    fun signOut(onDone: () -> Unit) {
        viewModelScope.launch {
            sessionDataStore.clearSession()
            onDone()
        }
    }
}
