package com.physcard.physics

import com.physcard.physics.domain.fsrs.FsrsSchedulingService
import com.physcard.physics.domain.model.FsrsState
import com.physcard.physics.domain.model.ReviewRating
import com.physcard.physics.domain.time.AppClock
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class FsrsSchedulingServiceTest {
    private val now = Instant.parse("2026-10-17T08:00:00Z")
    private val clock = AppClock(
        instantProvider = { now },
        zoneProvider = { ZoneOffset.UTC },
    )
    private val service = FsrsSchedulingService(
        clock = clock,
        initialRetention = 0.90f,
        enableFuzzing = false,
    )

    @Test
    fun officialSchedulerInitializesCardAndSchedulesReviewState() = runBlocking {
        val initial = service.newCardSnapshot(cardId = 7, nowMillis = now.toEpochMilli())
        assertEquals(FsrsState.LEARNING, initial.state)
        assertTrue(initial.dueAt != null)

        val result = service.review(initial, ReviewRating.GOOD, durationMs = 4_000)
        assertEquals(FsrsState.REVIEW, result.snapshot.state)
        assertNotNull(result.snapshot.stability)
        assertNotNull(result.snapshot.difficulty)
        assertTrue("Good should schedule beyond the learning step", result.intervalMinutes >= 24 * 60)
        assertTrue(result.snapshot.stability!! > 0.0)
    }

    @Test
    fun againOnNewCardUsesTenMinuteRelearningStep() = runBlocking {
        val initial = service.newCardSnapshot(cardId = 8, nowMillis = now.toEpochMilli())
        val result = service.review(initial, ReviewRating.AGAIN, durationMs = 2_000)
        assertEquals(10L, result.intervalMinutes)
        assertEquals(FsrsState.LEARNING, result.snapshot.state)
    }

    @Test
    fun reviewLapseEntersRelearningAndDoesNotResetHistory() = runBlocking {
        var snapshot = service.newCardSnapshot(cardId = 9, nowMillis = now.toEpochMilli())
        snapshot = service.review(snapshot, ReviewRating.GOOD, durationMs = 3_000).snapshot
        val due = snapshot.dueAt!!
        val again = service.review(
            snapshot,
            ReviewRating.AGAIN,
            durationMs = 3_000,
            reviewedAtMillis = due,
        )
        assertEquals(FsrsState.RELEARNING, again.snapshot.state)
        assertEquals(10L, again.intervalMinutes)
        assertTrue(again.snapshot.stability!! > 0.0)
    }

    @Test
    fun fourRatingsProduceMonotonicPreviewIntervals() = runBlocking {
        val snapshot = service.newCardSnapshot(cardId = 10, nowMillis = now.toEpochMilli())
        val preview = service.previewIntervals(snapshot)
        assertEquals(setOf(ReviewRating.AGAIN, ReviewRating.HARD, ReviewRating.GOOD, ReviewRating.EASY), preview.keys)
        assertTrue(preview.getValue(ReviewRating.AGAIN) < preview.getValue(ReviewRating.HARD))
        assertTrue(preview.getValue(ReviewRating.HARD) < preview.getValue(ReviewRating.GOOD))
        assertTrue(preview.getValue(ReviewRating.GOOD) < preview.getValue(ReviewRating.EASY))
    }

    @Test
    fun cardsAreIndependent() = runBlocking {
        val cardA = service.newCardSnapshot(cardId = 11, nowMillis = now.toEpochMilli())
        val cardB = service.newCardSnapshot(cardId = 12, nowMillis = now.toEpochMilli())
        val resultA = service.review(cardA, ReviewRating.GOOD, durationMs = 1_000)
        val resultB = service.review(cardB, ReviewRating.AGAIN, durationMs = 1_000)
        assertTrue(resultA.snapshot.dueAt!! > resultB.snapshot.dueAt!!)
    }

    @Test
    fun utcInstantIsIndependentOfLocalTimeZone() = runBlocking {
        val shanghaiClock = AppClock(
            instantProvider = { now },
            zoneProvider = { ZoneId.of("Asia/Shanghai") },
        )
        val shanghaiService = FsrsSchedulingService(shanghaiClock, enableFuzzing = false)
        val a = service.review(
            service.newCardSnapshot(13, now.toEpochMilli()),
            ReviewRating.GOOD,
            durationMs = 1_000,
        )
        val b = shanghaiService.review(
            shanghaiService.newCardSnapshot(13, now.toEpochMilli()),
            ReviewRating.GOOD,
            durationMs = 1_000,
        )
        assertEquals(a.snapshot.dueAt, b.snapshot.dueAt)
    }
}
