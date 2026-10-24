package com.physcard.physics

import androidx.test.core.app.ApplicationProvider
import com.physcard.physics.domain.model.ReviewRating
import com.physcard.physics.domain.session.SessionMode
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = PhysicsApp::class)
class AppDataIntegrationTest {
    private lateinit var app: PhysicsApp

    @Before
    fun setUp(): Unit = runBlocking(kotlinx.coroutines.Dispatchers.IO) {
        app = ApplicationProvider.getApplicationContext()
        app.container.database.clearAllTables()
        app.container.settingsRepository.updateSeedVersion(0)
        app.container.contentRepository.syncBuiltInContent()
        app.container.studyRepository.ensureReviewStates()
    }

    @After
    fun tearDown() = runBlocking(kotlinx.coroutines.Dispatchers.IO) {
        app.container.database.clearAllTables()
    }

    @Test
    fun builtInContentAndReviewStateSeedOffline() = runBlocking(kotlinx.coroutines.Dispatchers.IO) {
        val formulas = app.container.contentRepository.getAllFormulas()
        assertEquals(91, formulas.size)
        val bank = app.container.contentRepository.questionBank()
        assertEquals(1365, app.container.database.questionDao().count())
        assertEquals(
            3,
            bank.questions(formulas.first().id, com.physcard.physics.domain.model.Ability.FULL_FORMULA).size,
        )
        assertTrue(bank.hasAllAbilities(formulas.first().id))
        assertEquals(455, app.container.database.reviewDao().getAllStates().size)
    }

    @Test
    fun dailySessionAndReviewPersistThroughRepository() = runBlocking(kotlinx.coroutines.Dispatchers.IO) {
        val session = app.container.studyRepository.buildDailySession(dailyNewLimit = 1)
        assertEquals(SessionMode.DAILY, session.mode)
        assertEquals(5, session.items.size)
        assertEquals(
            listOf(
                com.physcard.physics.domain.model.Ability.FULL_FORMULA,
                com.physcard.physics.domain.model.Ability.FORMULA_FILL,
                com.physcard.physics.domain.model.Ability.FORMULA_TRANSFORM,
                com.physcard.physics.domain.model.Ability.APPLICABLE_CONDITION,
                com.physcard.physics.domain.model.Ability.CONFUSION_DISCRIMINATION,
            ),
            session.items.map { it.ability },
        )
        assertTrue(session.items.first().showFormulaIntro)
        assertTrue(session.items.map { it.question.id }.toSet().size == session.items.size)
        val item = session.items.first()
        val correct = item.question.correctIndex
        val outcome = app.container.studyRepository.recordReview(
            item = item,
            rating = ReviewRating.GOOD,
            correct = true,
            selectedOption = correct,
            durationMs = 6_000,
        )
        assertTrue(outcome.state.reviewCount == 1)
        assertNotNull(outcome.state.snapshot.dueAt)
        assertTrue(outcome.state.snapshot.dueAt!! > System.currentTimeMillis())
        assertTrue(outcome.state.level >= 1)

        val logs = app.container.database.reviewDao().getAllLogs()
        assertEquals(1, logs.size)
        assertEquals("GOOD", logs.first().rating)
        assertEquals(true, logs.first().wasFormulaFirstReview)

        val resumed = app.container.studyRepository.buildDailySession(dailyNewLimit = 1)
        assertEquals(4, resumed.items.size)
        assertTrue(resumed.items.all { it.formula.id == item.formula.id })
        assertTrue(resumed.items.none { it.ability == item.ability })
    }

    @Test
    fun focusedPracticeCoversEveryAbilityAndEveryVariant() = runBlocking(kotlinx.coroutines.Dispatchers.IO) {
        val formulaId = app.container.contentRepository.getAllFormulas().first().id
        val abilities = listOf(
            com.physcard.physics.domain.model.Ability.FULL_FORMULA,
            com.physcard.physics.domain.model.Ability.FORMULA_FILL,
            com.physcard.physics.domain.model.Ability.FORMULA_TRANSFORM,
            com.physcard.physics.domain.model.Ability.APPLICABLE_CONDITION,
            com.physcard.physics.domain.model.Ability.CONFUSION_DISCRIMINATION,
        )

        val wholeSet = app.container.studyRepository.buildFocusedSession(formulaId, ability = null)
        assertEquals(SessionMode.FOCUSED, wholeSet.mode)
        // 5 abilities x 3 variants each.
        assertEquals(15, wholeSet.items.size)
        assertEquals(abilities, wholeSet.items.map { it.ability }.distinct())
        assertTrue(wholeSet.items.all { it.formula.id == formulaId })
        assertEquals(
            "every item must be a distinct question",
            wholeSet.items.size,
            wholeSet.items.map { it.question.id }.toSet().size,
        )
        // Exactly the first variant of each ability drives FSRS.
        assertEquals(5, wholeSet.items.count { it.schedulesReview })
        assertEquals(
            abilities,
            wholeSet.items.filter { it.schedulesReview }.map { it.ability },
        )

        val single = app.container.studyRepository.buildFocusedSession(
            formulaId,
            ability = com.physcard.physics.domain.model.Ability.FORMULA_FILL,
        )
        assertEquals(3, single.items.size)
        assertTrue(single.items.all { it.ability == com.physcard.physics.domain.model.Ability.FORMULA_FILL })
        assertEquals(1, single.items.count { it.schedulesReview })
    }

    @Test
    fun drillVariantsDoNotAdvanceTheSchedule() = runBlocking(kotlinx.coroutines.Dispatchers.IO) {
        val formulaId = app.container.contentRepository.getAllFormulas().first().id
        val session = app.container.studyRepository.buildFocusedSession(formulaId, ability = null)
        val first = session.items.first { it.schedulesReview }
        val drills = session.items.filter { !it.schedulesReview }

        val scheduled = app.container.studyRepository.recordReview(
            item = first,
            rating = ReviewRating.GOOD,
            correct = true,
            selectedOption = first.question.correctIndex,
            durationMs = 5_000,
        )
        assertTrue(scheduled.scheduled)
        val dueAfterScheduled = scheduled.state.dueAt
        val levelAfterScheduled = scheduled.state.level

        val drill = drills.first()
        val drillOutcome = app.container.studyRepository.recordReview(
            item = drill,
            rating = ReviewRating.GOOD,
            correct = true,
            selectedOption = drill.question.correctIndex,
            durationMs = 5_000,
        )
        assertTrue("drill answers must not schedule a review", !drillOutcome.scheduled)
        assertEquals(0L, drillOutcome.fsrs.intervalMinutes)
        // The card's due date and level stay exactly where the scheduled answer left them.
        val stateNow = app.container.database.reviewDao()
            .getState(com.physcard.physics.data.repository.StudyRepository.cardKey(formulaId, drill.ability))
        assertNotNull(stateNow)
        assertEquals(dueAfterScheduled, stateNow!!.dueAt)
        assertEquals(levelAfterScheduled, stateNow.level)
        // The drill answer is still logged so daily accuracy stays truthful.
        assertTrue(app.container.database.reviewDao().getAllLogs().size >= 2)
    }

    @Test
    fun exportContainsSeparatedQuestionAnswerAndOptions() = runBlocking(kotlinx.coroutines.Dispatchers.IO) {
        val payload = app.container.contentRepository.exportJson(BuildConfig.VERSION_NAME)
        assertTrue(payload.contains("\"schemaVersion\": 1"))
        assertTrue(payload.contains("\"formulas\""))
        assertTrue(payload.contains("\"questions\""))
        assertTrue(payload.contains("correctIndex"))
        assertTrue(payload.length > 100_000)
    }
}
