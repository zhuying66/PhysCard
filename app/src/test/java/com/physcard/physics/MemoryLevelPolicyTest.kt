package com.physcard.physics

import com.physcard.physics.domain.level.MemoryLevelPolicy
import com.physcard.physics.domain.model.ReviewRating
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryLevelPolicyTest {
    private val policy = MemoryLevelPolicy()

    @Test
    fun oneCorrectAnswerCannotJumpToHighLevel() {
        val update = policy.evaluate(
            levelBefore = 1,
            pointsBefore = 0,
            rating = ReviewRating.EASY,
            correct = true,
            reviewCountAfter = 1,
            stabilityDaysAfter = 16.0,
            accuracyAfter = 1f,
        )
        assertEquals(1, update.levelAfter)
        assertEquals(1, update.levelBefore)
    }

    @Test
    fun repeatedReviewsRaiseLevelGradually() {
        var level = 1
        var points = 0
        repeat(4) { index ->
            val update = policy.evaluate(
                levelBefore = level,
                pointsBefore = points,
                rating = ReviewRating.GOOD,
                correct = true,
                reviewCountAfter = index + 1,
                stabilityDaysAfter = 10.0 + index,
                accuracyAfter = 1f,
            )
            level = update.levelAfter
            points = update.pointsAfter
        }
        assertTrue("level should rise with repeated successful reviews", level >= 2)
        assertTrue("level should still be earned gradually", level <= 4)
    }

    @Test
    fun aSingleLapseNeverErasesAllProgress() {
        val update = policy.evaluate(
            levelBefore = 6,
            pointsBefore = 160,
            rating = ReviewRating.AGAIN,
            correct = false,
            reviewCountAfter = 20,
            stabilityDaysAfter = 5.0,
            accuracyAfter = 0.8f,
        )
        assertEquals(5, update.levelAfter)
        assertTrue(update.pointsAfter >= policy.thresholds[3])
    }

    @Test
    fun wrongAnswerCannotRaiseLevelEvenIfRatedEasy() {
        val update = policy.evaluate(
            levelBefore = 4,
            pointsBefore = 80,
            rating = ReviewRating.EASY,
            correct = false,
            reviewCountAfter = 9,
            stabilityDaysAfter = 50.0,
            accuracyAfter = 0.8f,
        )
        assertTrue(update.levelAfter <= 4)
        assertTrue(update.pointsAfter < 80)
    }

    @Test
    fun levelEightRequiresLongTermStability() {
        val lowStability = policy.evaluate(
            levelBefore = 7,
            pointsBefore = 240,
            rating = ReviewRating.EASY,
            correct = true,
            reviewCountAfter = 18,
            stabilityDaysAfter = 25.0,
            accuracyAfter = 0.95f,
        )
        assertEquals(7, lowStability.levelAfter)

        val highStability = policy.evaluate(
            levelBefore = 7,
            pointsBefore = 240,
            rating = ReviewRating.EASY,
            correct = true,
            reviewCountAfter = 18,
            stabilityDaysAfter = 90.0,
            accuracyAfter = 0.95f,
        )
        assertEquals(8, highStability.levelAfter)
    }
}
