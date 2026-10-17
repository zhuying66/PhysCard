package com.physcard.physics.ui.stats

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.physcard.physics.PhysicsApp
import com.physcard.physics.domain.model.StudyStats
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class StatsViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as PhysicsApp).container

    val stats: StateFlow<StudyStats> = container.studyRepository
        .observeStats(container.settingsRepository.settings)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = StudyStats(),
        )
}
