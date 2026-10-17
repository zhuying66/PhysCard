package com.physcard.physics.ui.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.physcard.physics.PhysicsApp
import com.physcard.physics.domain.model.Ability
import com.physcard.physics.domain.model.Formula
import com.physcard.physics.domain.model.FormulaModule
import com.physcard.physics.domain.model.ReviewState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlin.math.roundToInt

data class AbilityStatus(
    val ability: Ability,
    val state: ReviewState?,
)

data class FormulaDetailUiState(
    val loading: Boolean = true,
    val formula: Formula? = null,
    val module: FormulaModule? = null,
    val abilities: List<AbilityStatus> = emptyList(),
    val related: List<Formula> = emptyList(),
    val averageLevel: Int = 0,
    val introducedAbilities: Int = 0,
    val error: String? = null,
)

class FormulaDetailViewModel(
    application: Application,
    private val formulaId: String,
) : AndroidViewModel(application) {
    private val container = (application as PhysicsApp).container

    val state: StateFlow<FormulaDetailUiState> = combine(
        container.contentRepository.observeFormulas(),
        container.studyRepository.observeStates(formulaId),
        container.contentRepository.modules,
    ) { formulas, states, modules ->
        val formula = formulas.firstOrNull { it.id == formulaId }
        if (formula == null) {
            FormulaDetailUiState(
                loading = false,
                error = "找不到这个公式，可能已被移除。",
            )
        } else {
            val stateByAbility = states.associateBy { it.ability }
            val introduced = states.filter { it.reviewCount > 0 }
            FormulaDetailUiState(
                loading = false,
                formula = formula,
                module = modules.firstOrNull { it.id == formula.moduleId },
                abilities = Ability.entries.map { ability ->
                    AbilityStatus(ability, stateByAbility[ability])
                },
                related = formula.relatedIds.mapNotNull { id -> formulas.firstOrNull { it.id == id } },
                averageLevel = if (introduced.isEmpty()) 0 else {
                    introduced.map { it.level }.average().roundToInt().coerceIn(1, 8)
                },
                introducedAbilities = introduced.size,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = FormulaDetailUiState(),
    )

    companion object {
        fun factory(application: Application, formulaId: String): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    FormulaDetailViewModel(application, formulaId)
                }
            }
    }
}
