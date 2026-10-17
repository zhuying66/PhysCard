package com.physcard.physics.domain.fsrs

import com.physcard.physics.domain.model.FsrsReviewResult
import com.physcard.physics.domain.model.FsrsSnapshot
import com.physcard.physics.domain.model.FsrsState
import com.physcard.physics.domain.model.ReviewRating
import com.physcard.physics.domain.time.AppClock
import io.github.openspacedrepetition.Card
import io.github.openspacedrepetition.Rating as FsrsRating
import io.github.openspacedrepetition.Scheduler
import io.github.openspacedrepetition.State as FsrsCardState
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToLong

/**
 * Thin, testable adapter around the official `io.github.open-spaced-repetition:fsrs` library
 * (java-fsrs 1.0.0, MIT). No scheduling math is reimplemented here.
 *
 * The scheduler is stateful because of FSRS interval fuzzing, so calls are serialized. Each
 * ability owns an independent FSRS card, which prevents e.g. a strong fill-in-the-blank result
 * from hiding weak formula-rearrangement recall.
 */
class FsrsSchedulingService(
    private val clock: AppClock,
    initialRetention: Float = 0.90f,
    private val enableFuzzing: Boolean = true,
) {
    private val mutex = Mutex()
    private var retention: Double = initialRetention.toDouble().coerceIn(0.80, 0.97)
    private var scheduler: Scheduler = buildScheduler(retention, enableFuzzing)

    val description: String
        get() = "java-fsrs 1.0.0 · FSRS-5 · 21 参数 · 目标保持率 ${(retention * 100).roundToLong()}%"

    val desiredRetention: Double get() = retention

    suspend fun updateRetention(value: Float) {
        val safe = value.toDouble().coerceIn(0.80, 0.97)
        mutex.withLock {
            if (safe != retention) {
                retention = safe
                scheduler = buildScheduler(retention, enableFuzzing)
            }
        }
    }

    fun newCardSnapshot(cardId: Int, nowMillis: Long = clock.nowMillis()): FsrsSnapshot =
        FsrsSnapshot(
            cardId = cardId,
            state = FsrsState.LEARNING,
            step = 0,
            stability = null,
            difficulty = null,
            dueAt = nowMillis,
            lastReviewAt = null,
        )

    suspend fun review(
        snapshot: FsrsSnapshot,
        rating: ReviewRating,
        durationMs: Long,
        reviewedAtMillis: Long = clock.nowMillis(),
    ): FsrsReviewResult = mutex.withLock {
        val reviewedAt = Instant.ofEpochMilli(reviewedAtMillis)
        val card = snapshot.toLibraryCard(defaultDue = reviewedAt)
        val result = scheduler.reviewCard(
            card,
            rating.toLibraryRating(),
            reviewedAt,
            durationMs.coerceIn(0, Int.MAX_VALUE.toLong()).toInt(),
        )
        val newCard = result.card()
        val newDue = newCard.due
        val intervalMinutes = Duration.between(reviewedAt, newDue).toMinutes().coerceAtLeast(0)
        val previousIntervalDays = previousIntervalDays(snapshot)
        FsrsReviewResult(
            snapshot = FsrsSnapshot(
                cardId = newCard.cardId,
                state = FsrsState.fromWire(newCard.state.name),
                step = newCard.step,
                stability = newCard.stability,
                difficulty = newCard.difficulty,
                dueAt = newDue.toEpochMilli(),
                lastReviewAt = newCard.lastReview?.toEpochMilli(),
            ),
            intervalMinutes = intervalMinutes,
            previousIntervalDays = previousIntervalDays,
        )
    }

    suspend fun retrievability(
        snapshot: FsrsSnapshot,
        nowMillis: Long = clock.nowMillis(),
    ): Double = mutex.withLock {
        if (snapshot.lastReviewAt == null || snapshot.stability == null) return@withLock 0.0
        val card = snapshot.toLibraryCard(Instant.ofEpochMilli(nowMillis))
        runCatching {
            scheduler.getCardRetrievability(card, Instant.ofEpochMilli(nowMillis))
        }.getOrDefault(0.0).coerceIn(0.0, 1.0)
    }

    /**
     * Preview intervals for the four rating buttons. Uses a separate fuzzing-disabled scheduler
     * so opening the feedback panel cannot perturb the real fuzz sequence.
     */
    suspend fun previewIntervals(
        snapshot: FsrsSnapshot,
        nowMillis: Long = clock.nowMillis(),
    ): Map<ReviewRating, Long> = mutex.withLock {
        val previewScheduler = buildScheduler(retention, enableFuzzing = false)
        val now = Instant.ofEpochMilli(nowMillis)
        ReviewRating.entries.associateWith { rating ->
            val card = snapshot.toLibraryCard(now)
            val result = previewScheduler.reviewCard(
                card,
                rating.toLibraryRating(),
                now,
                0,
            )
            Duration.between(now, result.card().due).toMinutes().coerceAtLeast(0)
        }
    }

    private fun previousIntervalDays(snapshot: FsrsSnapshot): Int {
        val due = snapshot.dueAt ?: return 0
        val last = snapshot.lastReviewAt ?: return 0
        return Duration.ofMillis(due - last).toDays().toInt().coerceAtLeast(0)
    }

    private fun FsrsSnapshot.toLibraryCard(defaultDue: Instant): Card {
        val builder = Card.builder()
            .cardId(cardId)
            .state(FsrsCardState.valueOf(state.name))
            .step(step)
            .stability(stability)
            .difficulty(difficulty)
            .lastReview(lastReviewAt?.let(Instant::ofEpochMilli))
        builder.due(dueAt?.let(Instant::ofEpochMilli) ?: defaultDue)
        return builder.build()
    }

    private fun ReviewRating.toLibraryRating(): FsrsRating = when (this) {
        ReviewRating.AGAIN -> FsrsRating.AGAIN
        ReviewRating.HARD -> FsrsRating.HARD
        ReviewRating.GOOD -> FsrsRating.GOOD
        ReviewRating.EASY -> FsrsRating.EASY
    }

    private fun buildScheduler(retention: Double, enableFuzzing: Boolean): Scheduler =
        Scheduler.builder()
            .desiredRetention(retention)
            .learningSteps(arrayOf(Duration.ofMinutes(10)))
            .relearningSteps(arrayOf(Duration.ofMinutes(10)))
            .maximumInterval(36500)
            .enableFuzzing(enableFuzzing)
            .build()
}
