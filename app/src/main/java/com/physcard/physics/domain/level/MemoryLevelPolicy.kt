package com.physcard.physics.domain.level

import com.physcard.physics.domain.model.LevelUpdate
import com.physcard.physics.domain.model.ReviewRating
import kotlin.math.min

/**
 * User-facing growth layer. Deliberately independent from FSRS:
 *
 * - FSRS decides *when* to review.
 * - This policy decides which of the 1..8 memory levels is displayed.
 *
 * A level cannot rise more than one step per review. It also cannot fall more than one step for a
 * single lapse, so one forgotten answer never erases weeks of visible progress. Higher levels are
 * gated by total reviews, FSRS stability and recent accuracy, not by a streak of taps.
 */
class MemoryLevelPolicy(
    val thresholds: List<Int> = listOf(0, 18, 40, 68, 100, 138, 182, 232),
    private val reviewGates: List<Int> = listOf(0, 0, 2, 4, 6, 9, 12, 16),
    private val stabilityGates: List<Double> = listOf(0.0, 0.0, 0.0, 1.0, 3.0, 7.0, 20.0, 60.0),
    private val ratingPoints: Map<ReviewRating, Int> = mapOf(
        ReviewRating.AGAIN to -14,
        ReviewRating.HARD to 4,
        ReviewRating.GOOD to 9,
        ReviewRating.EASY to 13,
    ),
) {
    init {
        require(thresholds.size == 8) { "八级等级需要 8 个阈值" }
        require(reviewGates.size == 8) { "八级等级需要 8 个复习次数门槛" }
        require(stabilityGates.size == 8) { "八级等级需要 8 个稳定性门槛" }
        require(thresholds.zipWithNext().all { (a, b) -> b > a }) { "等级阈值必须递增" }
    }

    fun evaluate(
        levelBefore: Int,
        pointsBefore: Int,
        rating: ReviewRating,
        correct: Boolean,
        reviewCountAfter: Int,
        stabilityDaysAfter: Double,
        accuracyAfter: Float,
    ): LevelUpdate {
        val safeLevel = levelBefore.coerceIn(1, 8)
        val safePoints = pointsBefore.coerceIn(0, MAX_POINTS)
        val stabilityBonus = when {
            stabilityDaysAfter >= 90.0 -> 3
            stabilityDaysAfter >= 30.0 -> 2
            stabilityDaysAfter >= 7.0 -> 1
            else -> 0
        }
        var delta = if (correct) {
            (ratingPoints[rating] ?: 0) + 2 + stabilityBonus
        } else {
            -6 + min(ratingPoints[rating] ?: 0, 0)
        }
        if (!correct) {
            // A wrong answer can never increase visible progress, even if the learner taps
            // "Easy" because they misread their own memory.
            delta = min(delta, -6)
        }
        var pointsAfter = (safePoints + delta).coerceIn(0, MAX_POINTS)
        if (!correct && safeLevel > 1) {
            // Keep the learner from dropping more than one level per lapse by flooring points at
            // the threshold of (currentLevel - 1).
            pointsAfter = pointsAfter.coerceAtLeast(thresholds[safeLevel - 2])
        }

        val pointsLevel = levelForPoints(pointsAfter)
        val allowedByReviews = highestGate(reviewGates.map { it.toDouble() }, reviewCountAfter.toDouble())
        val allowedByStability = highestGate(stabilityGates, stabilityDaysAfter)
        val allowedByAccuracy = when {
            reviewCountAfter >= 8 && accuracyAfter < 0.55f -> 4
            reviewCountAfter >= 4 && accuracyAfter < 0.70f -> 6
            else -> 8
        }
        val target = minOf(pointsLevel, allowedByReviews, allowedByStability, allowedByAccuracy)
        val levelAfter = when {
            target > safeLevel -> safeLevel + 1
            target < safeLevel -> safeLevel - 1
            else -> safeLevel
        }.coerceIn(1, 8)

        if (levelAfter < safeLevel && levelAfter > 1) {
            pointsAfter = pointsAfter.coerceAtLeast(thresholds[levelAfter - 1])
        }

        return LevelUpdate(
            levelBefore = safeLevel,
            levelAfter = levelAfter,
            pointsBefore = safePoints,
            pointsAfter = pointsAfter,
            leveledUp = levelAfter > safeLevel,
            leveledDown = levelAfter < safeLevel,
        )
    }

    fun levelForPoints(points: Int): Int {
        var level = 1
        thresholds.forEachIndexed { index, threshold ->
            if (points >= threshold) level = index + 1
        }
        return level.coerceIn(1, 8)
    }

    fun progressToNext(points: Int, level: Int): Float {
        val safeLevel = level.coerceIn(1, 8)
        if (safeLevel >= 8) return 1f
        val start = thresholds[safeLevel - 1].toFloat()
        val end = thresholds[safeLevel].toFloat()
        return ((points.coerceIn(start.toInt(), end.toInt()) - start) / (end - start)).coerceIn(0f, 1f)
    }

    fun nextThreshold(points: Int): Int? {
        val level = levelForPoints(points)
        return thresholds.getOrNull(level)
    }

    private fun highestGate(gates: List<Double>, value: Double): Int {
        var result = 1
        gates.forEachIndexed { index, gate ->
            if (value >= gate) result = index + 1
        }
        return result
    }

    companion object {
        const val MAX_POINTS = 300
        const val MAX_LEVEL = 8
        val LEVEL_TITLES = listOf(
            "初识",
            "熟悉",
            "理解",
            "掌握",
            "稳固",
            "熟练",
            "精通",
            "贯通",
        )
        val LEVEL_DESCRIPTIONS = listOf(
            "刚刚开始建立印象",
            "能辨认公式的基本形状",
            "理解各物理量的关系",
            "能独立完成常见回忆",
            "多次正确，记忆开始稳定",
            "不同题型都能应对",
            "长期保持，能迁移应用",
            "融会贯通，接近长期记忆",
        )
    }
}
