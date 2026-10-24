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
    /**
     * Whether this answer updates the FSRS schedule and memory level.
     *
     * Focused practice drills several question variants of the same ability. Only the first
     * variant is scheduled; the rest are extra practice so one sitting cannot count as three
     * consecutive reviews of the same card (which would collapse the interval and jump levels).
     */
    val schedulesReview: Boolean = true,
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
