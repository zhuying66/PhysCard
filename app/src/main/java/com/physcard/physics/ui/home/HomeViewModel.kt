package com.physcard.physics.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.physcard.physics.PhysicsApp
import com.physcard.physics.domain.model.HomeSnapshot
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as PhysicsApp).container

    val home: StateFlow<HomeSnapshot> = container.studyRepository
        .observeHomeSnapshot(container.settingsRepository.settings)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeSnapshot(),
        )
}
