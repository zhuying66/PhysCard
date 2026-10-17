package com.physcard.physics.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "formulas",
    indices = [
        Index(value = ["module"]),
        Index(value = ["orderIndex"]),
    ],
)
data class FormulaEntity(
    @PrimaryKey val id: String,
    val title: String,
    val module: String,
    val chapter: String,
    val textbook: String,
    val latex: String,
    val conditions: String,
    val variablesJson: String,
    val confusionsJson: String,
    val relatedIdsJson: String,
    val orderIndex: Int,
)

@Entity(
    tableName = "questions",
    foreignKeys = [
        ForeignKey(
            entity = FormulaEntity::class,
            parentColumns = ["id"],
            childColumns = ["formulaId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["formulaId"]),
        Index(value = ["formulaId", "ability"]),
    ],
)
data class QuestionEntity(
    @PrimaryKey val id: String,
    val formulaId: String,
    val ability: String,
    val prompt: String,
    val stimulusLatex: String?,
)

@Entity(
    tableName = "question_options",
    primaryKeys = ["questionId", "orderIndex"],
    foreignKeys = [
        ForeignKey(
            entity = QuestionEntity::class,
            parentColumns = ["id"],
            childColumns = ["questionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["questionId"])],
)
data class QuestionOptionEntity(
    val questionId: String,
    val orderIndex: Int,
    val latex: String?,
    val text: String?,
)

/**
 * Answers live in a separate table from questions so content review and imports can be
 * validated independently. The correct option is identified by its stable display order.
 */
@Entity(
    tableName = "question_answers",
    foreignKeys = [
        ForeignKey(
            entity = QuestionEntity::class,
            parentColumns = ["id"],
            childColumns = ["questionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class QuestionAnswerEntity(
    @PrimaryKey val questionId: String,
    val correctOptionIndex: Int,
    val explanation: String,
)

@Entity(
    tableName = "review_states",
    foreignKeys = [
        ForeignKey(
            entity = FormulaEntity::class,
            parentColumns = ["id"],
            childColumns = ["formulaId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["formulaId"]),
        Index(value = ["dueAt"]),
        Index(value = ["introduced", "dueAt"]),
        Index(value = ["formulaId", "dueAt"]),
        Index(value = ["formulaId", "ability"], unique = true),
    ],
)
data class ReviewStateEntity(
    @PrimaryKey val cardKey: String,
    val formulaId: String,
    val ability: String,
    val introduced: Boolean,
    val cardId: Int,
    val fsrsState: String,
    val step: Int?,
    val stability: Double?,
    val difficulty: Double?,
    val dueAt: Long?,
    val lastReviewAt: Long?,
    val reviewCount: Int,
    val lapseCount: Int,
    val correctCount: Int,
    val level: Int,
    val levelPoints: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "review_logs",
    indices = [
        Index(value = ["reviewedAt"]),
        Index(value = ["localDate"]),
        Index(value = ["formulaId"]),
        Index(value = ["cardKey"]),
    ],
)
data class ReviewLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cardKey: String,
    val formulaId: String,
    val ability: String,
    val reviewedAt: Long,
    val localDate: String,
    val rating: String,
    val correct: Boolean,
    val selectedOption: Int,
    val correctOption: Int,
    val scheduledMinutes: Long,
    val elapsedDays: Int,
    val levelBefore: Int,
    val levelAfter: Int,
    val stabilityAfter: Double?,
    val difficultyAfter: Double?,
    val durationMs: Long,
    val wasFirstReview: Boolean,
    val wasFormulaFirstReview: Boolean,
)
