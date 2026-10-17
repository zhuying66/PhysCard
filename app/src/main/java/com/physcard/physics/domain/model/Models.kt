package com.physcard.physics.domain.model

import androidx.compose.ui.graphics.Color

enum class Ability(
    val wireName: String,
    val title: String,
    val shortTitle: String,
    val description: String,
) {
    FULL_FORMULA(
        wireName = "FULL_FORMULA",
        title = "完整公式",
        shortTitle = "完整",
        description = "从相似公式中准确认出标准表达式",
    ),
    FORMULA_FILL(
        wireName = "FORMULA_FILL",
        title = "公式填空",
        shortTitle = "填空",
        description = "补全公式中隐藏的物理量、系数或运算符",
    ),
    FORMULA_TRANSFORM(
        wireName = "FORMULA_TRANSFORM",
        title = "公式变形",
        shortTitle = "变形",
        description = "把一个公式正确变形为求解目标量的形式",
    ),
    APPLICABLE_CONDITION(
        wireName = "APPLICABLE_CONDITION",
        title = "适用条件",
        shortTitle = "条件",
        description = "判断公式成立的范围与使用前提",
    ),
    CONFUSION_DISCRIMINATION(
        wireName = "CONFUSION_DISCRIMINATION",
        title = "易混辨析",
        shortTitle = "辨析",
        description = "在真实物理情境中选择合适的规律",
    );

    companion object {
        fun fromWire(value: String): Ability =
            entries.firstOrNull { it.wireName == value }
                ?: throw IllegalArgumentException("未知的考查能力：$value")
    }
}

data class FormulaModule(
    val id: String,
    val name: String,
    val description: String,
    val accent: Color,
    val orderIndex: Int,
)

data class FormulaVariable(
    val symbol: String,
    val name: String,
    val unit: String,
)

data class FormulaConfusion(
    val wrong: String,
    val why: String,
)

data class Formula(
    val id: String,
    val title: String,
    val moduleId: String,
    val chapter: String,
    val textbook: String,
    val latex: String,
    val conditions: String,
    val variables: List<FormulaVariable>,
    val confusions: List<FormulaConfusion>,
    val relatedIds: List<String>,
    val orderIndex: Int,
)

data class QuestionOption(
    val index: Int,
    val latex: String? = null,
    val text: String? = null,
)

data class Question(
    val id: String,
    val formulaId: String,
    val ability: Ability,
    val prompt: String,
    val stimulusLatex: String?,
    val options: List<QuestionOption>,
    val correctIndex: Int,
    val explanation: String,
) {
    init {
        require(options.size == 4) { "每题必须有 4 个选项" }
        require(correctIndex in options.indices) { "正确答案下标越界" }
    }
}

enum class ReviewRating(val wireName: String, val label: String, val description: String) {
    AGAIN("AGAIN", "忘记", "完全想不起来"),
    HARD("HARD", "困难", "想起来了，但很吃力"),
    GOOD("GOOD", "记得", "正常回忆起来"),
    EASY("EASY", "熟练", "毫不费力，非常熟练");

    companion object {
        fun fromWire(value: String): ReviewRating =
            entries.firstOrNull { it.wireName == value }
                ?: throw IllegalArgumentException("未知的复习评价：$value")
    }
}

enum class FsrsState(val wireName: String) {
    LEARNING("LEARNING"),
    REVIEW("REVIEW"),
    RELEARNING("RELEARNING");

    companion object {
        fun fromWire(value: String): FsrsState =
            entries.firstOrNull { it.wireName == value }
                ?: throw IllegalArgumentException("未知的 FSRS 状态：$value")
    }
}

data class FsrsSnapshot(
    val cardId: Int,
    val state: FsrsState = FsrsState.LEARNING,
    val step: Int? = null,
    val stability: Double? = null,
    val difficulty: Double? = null,
    val dueAt: Long? = null,
    val lastReviewAt: Long? = null,
)

data class FsrsReviewResult(
    val snapshot: FsrsSnapshot,
    val intervalMinutes: Long,
    val previousIntervalDays: Int,
)

data class ReviewState(
    val cardKey: String,
    val formulaId: String,
    val ability: Ability,
    val introduced: Boolean,
    val snapshot: FsrsSnapshot,
    val reviewCount: Int,
    val lapseCount: Int,
    val correctCount: Int,
    val level: Int,
    val levelPoints: Int,
    val createdAt: Long,
    val updatedAt: Long,
) {
    val dueAt: Long? get() = snapshot.dueAt
    val accuracy: Float
        get() = if (reviewCount == 0) 0f else correctCount.toFloat() / reviewCount
}

data class ReviewLog(
    val id: Long,
    val cardKey: String,
    val formulaId: String,
    val ability: Ability,
    val reviewedAt: Long,
    val localDate: String,
    val rating: ReviewRating,
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

data class LevelUpdate(
    val levelBefore: Int,
    val levelAfter: Int,
    val pointsBefore: Int,
    val pointsAfter: Int,
    val leveledUp: Boolean,
    val leveledDown: Boolean,
)

data class ModuleProgress(
    val module: FormulaModule,
    val total: Int,
    val started: Int,
    val mastered: Int,
    val averageLevel: Float,
)

data class DailyStat(
    val epochDay: Long,
    val label: String,
    val reviews: Int,
    val correct: Int,
)

data class RecentReview(
    val formulaTitle: String,
    val ability: Ability,
    val correct: Boolean,
    val rating: ReviewRating,
    val reviewedAt: Long,
)

data class StudyStats(
    val totalReviews: Int = 0,
    val totalCorrect: Int = 0,
    val totalStudyMinutes: Long = 0,
    val streakDays: Int = 0,
    val todayReviews: Int = 0,
    val todayCorrect: Int = 0,
    val todayNewFormulas: Int = 0,
    val learnedFormulas: Int = 0,
    val masteredFormulas: Int = 0,
    val totalFormulas: Int = 0,
    val levelDistribution: List<Int> = List(8) { 0 },
    val lastSevenDays: List<DailyStat> = emptyList(),
    val moduleProgress: List<ModuleProgress> = emptyList(),
    val recentLogs: List<ReviewLog> = emptyList(),
    val recentReviews: List<RecentReview> = emptyList(),
) {
    val accuracy: Float
        get() = if (totalReviews == 0) 0f else totalCorrect.toFloat() / totalReviews
    val todayAccuracy: Float
        get() = if (todayReviews == 0) 0f else todayCorrect.toFloat() / todayReviews
}

data class HomeSnapshot(
    val dueCount: Int = 0,
    val newFormulaCount: Int = 0,
    val introducedFormulaCount: Int = 0,
    val totalFormulaCount: Int = 0,
    val todayReviewed: Int = 0,
    val todayNewFormulas: Int = 0,
    val todayCorrect: Int = 0,
    val streakDays: Int = 0,
    val dailyNewLimit: Int = 3,
    val masteredFormulas: Int = 0,
    val modules: List<ModuleProgress> = emptyList(),
    val nextDueAt: Long? = null,
    val remainingToday: Int = 0,
) {
    val hasStudyWork: Boolean get() = dueCount > 0 || newFormulaCount > 0
    val todayAccuracy: Float
        get() = if (todayReviewed == 0) 0f else todayCorrect.toFloat() / todayReviewed
}
