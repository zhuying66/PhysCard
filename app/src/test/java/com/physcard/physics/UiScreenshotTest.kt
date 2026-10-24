package com.physcard.physics

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.physcard.physics.domain.model.Ability
import com.physcard.physics.domain.model.Formula
import com.physcard.physics.domain.model.FormulaConfusion
import com.physcard.physics.domain.model.FormulaModule
import com.physcard.physics.domain.model.FormulaVariable
import com.physcard.physics.domain.model.HomeSnapshot
import com.physcard.physics.domain.model.ModuleProgress
import com.physcard.physics.domain.model.Question
import com.physcard.physics.domain.model.QuestionOption
import com.physcard.physics.domain.model.ReviewState
import com.physcard.physics.domain.model.StudyStats
import com.physcard.physics.domain.session.StudyItem
import com.physcard.physics.domain.session.StudySession
import com.physcard.physics.domain.session.SessionMode
import com.physcard.physics.ui.components.Formula
import com.physcard.physics.ui.home.HomeScreen
import com.physcard.physics.ui.study.StudyScreen
import com.physcard.physics.ui.study.StudyStage
import com.physcard.physics.ui.study.StudyUiState
import com.physcard.physics.ui.theme.PhysicsFormulaTheme
import com.physcard.physics.ui.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UiScreenshotTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun homeScreenRendersAndCaptures() {
        compose.setContent {
            PhysicsFormulaTheme(ThemeMode.LIGHT) {
                HomeScreen(
                    snapshot = sampleHome(),
                    onStartStudy = {},
                    onOpenStats = {},
                )
            }
        }
        compose.onNodeWithText("物理公式记忆").assertIsDisplayed()
        compose.onNodeWithText("开始学习").assertIsDisplayed()
        compose.onNodeWithText("学习统计").assertIsDisplayed()
        saveScreenshot("home-light")
    }

    @Test
    fun formulaRendererSupportsFractionsRootsAndSubscripts() {
        compose.setContent {
            PhysicsFormulaTheme(ThemeMode.LIGHT) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White)
                        .padding(24.dp),
                ) {
                    Text("公式排版检查", style = MaterialTheme.typography.titleLarge)
                    listOf(
                        """v = v_0 + at""",
                        """x = v_0t + \frac{1}{2}at^2""",
                        """v^2 - v_0^2 = 2ax""",
                        """T = 2\pi\sqrt{\frac{r^3}{GM}}""",
                        """F_{\text{合}} = ma""",
                        """S = \frac{1}{2}at^2""",
                        """v_y = \sqrt{v^2 - v_x^2}""",
                        """\Delta U = Q + W""",
                        """E_k = h\nu - W_0""",
                        """\frac{p_1V_1}{T_1} = \frac{p_2V_2}{T_2}""",
                    ).forEach { latex ->
                        Formula(latex = latex, fontSize = 26.sp)
                    }
                }
            }
        }
        compose.onNodeWithText("公式排版检查").assertIsDisplayed()
        saveScreenshot("formula-renderer")
    }

    @Test
    fun studyFeedbackRendersOptionsExplanationAndRatings() {
        val item = sampleItem()
        compose.setContent {
            PhysicsFormulaTheme(ThemeMode.LIGHT) {
                StudyScreen(
                    state = StudyUiState(
                        stage = StudyStage.FEEDBACK,
                        item = item,
                        index = 0,
                        total = 1,
                        selectedIndex = 1,
                        isCorrect = false,
                        previewIntervals = mapOf(
                            com.physcard.physics.domain.model.ReviewRating.AGAIN to 10,
                            com.physcard.physics.domain.model.ReviewRating.HARD to 15,
                            com.physcard.physics.domain.model.ReviewRating.GOOD to 4320,
                            com.physcard.physics.domain.model.ReviewRating.EASY to 21600,
                        ),
                        sessionMode = SessionMode.FOCUSED,
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
        compose.onNodeWithText("这次没有选对").assertIsDisplayed()
        compose.onNodeWithText("这次回忆有多轻松？").assertIsDisplayed()
        saveScreenshot("study-feedback")
    }

    private fun saveScreenshot(name: String) {
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val width = view.width.coerceAtLeast(1)
        val height = view.height.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val output = File("build/screenshots/$name.png")
        output.parentFile?.mkdirs()
        output.outputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
        assert(output.length() > 0)
    }

    private fun sampleFormula(): Formula = Formula(
        id = "velocity_time",
        title = "匀变速直线运动的速度公式",
        moduleId = "mechanics",
        chapter = "匀变速直线运动",
        textbook = "人教版高中物理（2019 版）",
        latex = """v = v_0 + at""",
        conditions = "加速度恒定的直线运动。先规定正方向，各量带符号代入。",
        variables = listOf(
            FormulaVariable("v", "末速度", "m/s"),
            FormulaVariable("v_0", "初速度", "m/s"),
            FormulaVariable("a", "加速度", "m/s²"),
            FormulaVariable("t", "时间", "s"),
        ),
        confusions = listOf(
            FormulaConfusion("""v = at""", "只有当 v₀ = 0 时才成立。"),
            FormulaConfusion("""x = v_0t + \frac{1}{2}at^2""", "这是位移公式，不是速度公式。"),
        ),
        relatedIds = listOf("displacement_time"),
        orderIndex = 0,
    )

    private fun sampleItem(): StudyItem {
        val formula = sampleFormula()
        val question = Question(
            id = "q1",
            formulaId = formula.id,
            ability = Ability.FORMULA_TRANSFORM,
            prompt = "由上式求解 a，正确的变形是：",
            stimulusLatex = formula.latex,
            options = listOf(
                QuestionOption(0, latex = """a = \frac{v - v_0}{t}"""),
                QuestionOption(1, latex = """a = \frac{v + v_0}{t}"""),
                QuestionOption(2, latex = """a = (v - v_0)t"""),
                QuestionOption(3, latex = """a = \frac{t}{v - v_0}"""),
            ),
            correctIndex = 0,
            explanation = "由 v = v₀ + at 移项可得 a = (v − v₀)/t。",
        )
        return StudyItem(
            formula = formula,
            ability = Ability.FORMULA_TRANSFORM,
            question = question,
            state = ReviewState(
                cardKey = "velocity_time::FORMULA_TRANSFORM",
                formulaId = formula.id,
                ability = Ability.FORMULA_TRANSFORM,
                introduced = true,
                snapshot = com.physcard.physics.domain.model.FsrsSnapshot(
                    cardId = 42,
                    dueAt = System.currentTimeMillis() + 86_400_000,
                ),
                reviewCount = 3,
                lapseCount = 0,
                correctCount = 2,
                level = 3,
                levelPoints = 46,
                createdAt = 0,
                updatedAt = 0,
            ),
            isNewFormula = false,
            showFormulaIntro = false,
        )
    }

    private fun sampleHome(): HomeSnapshot {
        val mechanic = FormulaModule(
            id = "mechanics",
            name = "力学",
            description = "运动、力、能量与动量",
            accent = Color(0xFF2F6F55),
            orderIndex = 0,
        )
        val em = FormulaModule(
            id = "electromagnetism",
            name = "电磁学",
            description = "电场、电路、磁场与电磁感应",
            accent = Color(0xFF8A5B8E),
            orderIndex = 1,
        )
        return HomeSnapshot(
            dueCount = 12,
            newFormulaCount = 5,
            introducedFormulaCount = 18,
            totalFormulaCount = 81,
            todayReviewed = 8,
            todayNewFormulas = 1,
            todayCorrect = 7,
            streakDays = 6,
            dailyNewLimit = 3,
            masteredFormulas = 4,
            modules = listOf(
                ModuleProgress(mechanic, total = 30, started = 9, mastered = 2, averageLevel = 2.4f),
                ModuleProgress(em, total = 25, started = 4, mastered = 1, averageLevel = 1.8f),
            ),
            remainingToday = 23,
        )
    }
}
