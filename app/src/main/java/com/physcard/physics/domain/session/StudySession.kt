package com.physcard.physics.domain.session

import com.physcard.physics.domain.model.Ability
import com.physcard.physics.domain.model.Formula
import com.physcard.physics.domain.model.Question
import com.physcard.physics.domain.model.ReviewState

enum class SessionMode {
    DAILY,
    FOCUSED,
}

data class StudyItem(
    val formula: Formula,
    val ability: Ability,
    val question: Question,
    val state: ReviewState,
    val isNewFormula: Boolean,
    val showFormulaIntro: Boolean,
)

data class StudySession(
    val mode: SessionMode,
    val items: List<StudyItem>,
    val generatedAt: Long,
) {
    val size: Int get() = items.size
    val newFormulaCount: Int get() = items.map { it.formula.id }.distinct().count {
        formulaId -> items.first { it.formula.id == formulaId }.isNewFormula
    }
}
