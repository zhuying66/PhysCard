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
        assertEquals(455, app.container.database.questionDao().count())
        assertEquals(1, bank.questions(formulas.first().id, com.physcard.physics.domain.model.Ability.FULL_FORMULA).size)
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
    fun focusedPracticeCoversEveryAbilityOfTheFormula() = runBlocking(kotlinx.coroutines.Dispatchers.IO) {
        val formulaId = app.container.contentRepository.getAllFormulas().first().id

        val wholeSet = app.container.studyRepository.buildFocusedSession(formulaId, ability = null)
        assertEquals(SessionMode.FOCUSED, wholeSet.mode)
        assertEquals(5, wholeSet.items.size)
        assertEquals(
            listOf(
                com.physcard.physics.domain.model.Ability.FULL_FORMULA,
                com.physcard.physics.domain.model.Ability.FORMULA_FILL,
                com.physcard.physics.domain.model.Ability.FORMULA_TRANSFORM,
                com.physcard.physics.domain.model.Ability.APPLICABLE_CONDITION,
                com.physcard.physics.domain.model.Ability.CONFUSION_DISCRIMINATION,
            ),
            wholeSet.items.map { it.ability },
        )
        assertTrue(wholeSet.items.all { it.formula.id == formulaId })
        assertEquals(
            "every item must be a distinct question",
            wholeSet.items.size,
            wholeSet.items.map { it.question.id }.toSet().size,
        )

        val single = app.container.studyRepository.buildFocusedSession(
            formulaId,
            ability = com.physcard.physics.domain.model.Ability.FORMULA_FILL,
        )
        assertEquals(1, single.items.size)
        assertEquals(
            com.physcard.physics.domain.model.Ability.FORMULA_FILL,
            single.items.single().ability,
        )
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
