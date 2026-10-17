package com.physcard.physics.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.physcard.physics.PhysicsApp
import com.physcard.physics.data.repository.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface StartupState {
    data object Loading : StartupState
    data object Ready : StartupState
    data class Error(val message: String, val detail: String? = null) : StartupState
}

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as PhysicsApp).container
    private val _startup = MutableStateFlow<StartupState>(StartupState.Loading)
    val startup: StateFlow<StartupState> = _startup.asStateFlow()

    val settings: StateFlow<AppSettings> = container.settingsRepository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppSettings(),
    )

    init {
        initialize()
    }

    fun initialize() {
        if (_startup.value is StartupState.Ready) return
        _startup.value = StartupState.Loading
        viewModelScope.launch {
            runCatching { container.initialize() }
                .onSuccess { _startup.value = StartupState.Ready }
                .onFailure { error ->
                    _startup.value = StartupState.Error(
                        message = error.message ?: "应用初始化失败。",
                        detail = error.stackTraceToString().lineSequence().take(8).joinToString("\n"),
                    )
                }
        }
    }
}
