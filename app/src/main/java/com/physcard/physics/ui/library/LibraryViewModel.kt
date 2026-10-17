package com.physcard.physics.ui.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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

data class FormulaCardUi(
    val formula: Formula,
    val module: FormulaModule?,
    val averageLevel: Int,
    val introducedAbilities: Int,
    val totalAbilities: Int,
    val dueCount: Int,
    val reviewCount: Int,
)

data class LibraryUiState(
    val loading: Boolean = true,
    val formulas: List<FormulaCardUi> = emptyList(),
    val modules: List<FormulaModule> = emptyList(),
)

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as PhysicsApp).container

    val state: StateFlow<LibraryUiState> = combine(
        container.contentRepository.observeFormulas(),
        container.studyRepository.observeAllStates(),
        container.contentRepository.modules,
    ) { formulas, states, modules ->
        val statesByFormula = states.groupBy { it.formulaId }
        val moduleById = modules.associateBy { it.id }
        LibraryUiState(
            loading = false,
            modules = modules.sortedBy { it.orderIndex },
            formulas = formulas.sortedBy { it.orderIndex }.map { formula ->
                val formulaStates = statesByFormula[formula.id].orEmpty()
                val introduced = formulaStates.filter { it.reviewCount > 0 }
                FormulaCardUi(
                    formula = formula,
                    module = moduleById[formula.moduleId],
                    averageLevel = if (introduced.isEmpty()) 0 else {
                        introduced.map { it.level }.average().roundToInt().coerceIn(1, 8)
                    },
                    introducedAbilities = introduced.size,
                    totalAbilities = Ability.entries.size,
                    dueCount = formulaStates.count {
                        it.introduced && (it.dueAt ?: Long.MAX_VALUE) <= System.currentTimeMillis()
                    },
                    reviewCount = formulaStates.sumOf { it.reviewCount },
                )
            },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LibraryUiState(),
    )
}
