package com.physcard.physics

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.physcard.physics.domain.model.Ability
import com.physcard.physics.domain.model.FsrsSnapshot
import com.physcard.physics.domain.model.FsrsState
import com.physcard.physics.domain.model.Formula
import com.physcard.physics.domain.model.FormulaConfusion
import com.physcard.physics.domain.model.Question
import com.physcard.physics.domain.model.QuestionOption
import com.physcard.physics.domain.model.ReviewState
import com.physcard.physics.domain.session.StudyItem
import com.physcard.physics.ui.study.StudyScreen
import com.physcard.physics.ui.study.StudyStage
import com.physcard.physics.ui.study.StudyUiState
import com.physcard.physics.ui.theme.PhysicsFormulaTheme
import com.physcard.physics.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Long prose options must wrap instead of requiring a horizontal swipe, and every option
 * card must occupy the same width no matter how long its text is.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = PhysicsApp::class)
class LongOptionLayoutTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val longText =
        "光从光密介质射向光疏介质，且入射角增大到临界角 C 时，折射角等于 90°，折射光恰好消失。"
    private val shortText = "R"

    @Test
    fun longTextOptionsWrapAndMatchShortOptionWidth() {
        compose.setContent {
            PhysicsFormulaTheme(ThemeMode.LIGHT) {
                StudyScreen(
                    state = StudyUiState(
                        stage = StudyStage.QUESTION,
                        item = studyItem(),
                        index = 0,
                        total = 4,
                    ),
                    onBeginQuestion = {},
                    onSelectOption = {},
                    onRate = {},
                    onRetry = {},
                    onExit = {},
                    onFinish = {},
                )
            }
        }

        compose.onNodeWithText(longText).assertIsDisplayed()
        compose.onNodeWithText(shortText).assertIsDisplayed()

        val shortBounds = compose.onNodeWithTag("option-0").getBoundsInRoot()
        val longBounds = compose.onNodeWithTag("option-1").getBoundsInRoot()

        assertEquals("left edges must match", shortBounds.left.value, longBounds.left.value, 0.5f)
        assertEquals("right edges must match", shortBounds.right.value, longBounds.right.value, 0.5f)
        assertEquals(
            "widths must match",
            (shortBounds.right - shortBounds.left).value,
            (longBounds.right - longBounds.left).value,
            0.5f,
        )

        // The long text must be laid out inside its card, not wider than it: a node wider than
        // the card is what used to force a horizontal swipe.
        val longTextBounds = compose.onNodeWithText(longText).getBoundsInRoot()
        val cardWidth = (longBounds.right - longBounds.left).value
        val textWidth = (longTextBounds.right - longTextBounds.left).value
        assertTrue(
            "long option text ($textWidth) must fit within the card ($cardWidth)",
            textWidth <= cardWidth + 0.5f,
        )
        assertTrue(
            "long option text must start inside the card",
            longTextBounds.left.value >= longBounds.left.value - 0.5f,
        )
    }

    private fun studyItem(): StudyItem {
        val formula = Formula(
            id = "refraction_critical",
            title = "全反射临界角",
            moduleId = "optics",
            chapter = "光的折射",
            textbook = "人教版高中物理（2019 版）",
            latex = """\sin C = \frac{1}{n}""",
            conditions = "光从光密介质射向光疏介质，入射角大于等于临界角。",
            variables = emptyList(),
            confusions = listOf(FormulaConfusion("""\sin C = n""", "临界角的正弦是折射率的倒数。")),
            relatedIds = emptyList(),
            orderIndex = 0,
        )
        return StudyItem(
            formula = formula,
            ability = Ability.APPLICABLE_CONDITION,
            question = Question(
                id = "q_long",
                formulaId = formula.id,
                ability = Ability.APPLICABLE_CONDITION,
                prompt = "关于全反射，下列说法正确的是：",
                stimulusLatex = null,
                options = listOf(
                    QuestionOption(0, text = shortText),
                    QuestionOption(1, text = longText),
                    QuestionOption(2, text = "只适用于光从空气射入玻璃"),
                    QuestionOption(3, text = "与介质折射率无关"),
                ),
                correctIndex = 1,
                explanation = "临界角满足 sin C = 1/n。",
            ),
            state = ReviewState(
                cardKey = "refraction_critical::${Ability.APPLICABLE_CONDITION.wireName}",
                formulaId = formula.id,
                ability = Ability.APPLICABLE_CONDITION,
                introduced = true,
                snapshot = FsrsSnapshot(
                    cardId = 1,
                    state = FsrsState.REVIEW,
                    step = 0,
                    stability = 5.0,
                    difficulty = 5.0,
                    dueAt = System.currentTimeMillis() + 86_400_000L,
                    lastReviewAt = System.currentTimeMillis(),
                ),
                reviewCount = 2,
                lapseCount = 0,
                correctCount = 2,
                level = 3,
                levelPoints = 40,
                createdAt = 0L,
                updatedAt = 0L,
            ),
            isNewFormula = false,
            showFormulaIntro = false,
        )
    }
}
