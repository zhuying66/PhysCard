package com.physcard.physics

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.physcard.physics.domain.level.MemoryLevelPolicy
import com.physcard.physics.domain.model.Ability
import com.physcard.physics.domain.model.DailyStat
import com.physcard.physics.domain.model.FsrsSnapshot
import com.physcard.physics.domain.model.Formula
import com.physcard.physics.domain.model.FormulaConfusion
import com.physcard.physics.domain.model.FormulaModule
import com.physcard.physics.domain.model.FormulaVariable
import com.physcard.physics.domain.model.HomeSnapshot
import com.physcard.physics.domain.model.ModuleProgress
import com.physcard.physics.domain.model.RecentReview
import com.physcard.physics.domain.model.ReviewRating
import com.physcard.physics.domain.model.ReviewState
import com.physcard.physics.domain.model.StudyStats
import com.physcard.physics.domain.session.SessionMode
import com.physcard.physics.ui.home.HomeScreen
import com.physcard.physics.ui.library.AbilityStatus
import com.physcard.physics.ui.library.FormulaCardUi
import com.physcard.physics.ui.library.FormulaDetailScreen
import com.physcard.physics.ui.library.FormulaDetailUiState
import com.physcard.physics.ui.library.LibraryScreen
import com.physcard.physics.ui.library.LibraryUiState
import com.physcard.physics.ui.settings.DataActionState
import com.physcard.physics.ui.settings.SettingsScreen
import com.physcard.physics.ui.stats.StatsScreen
import com.physcard.physics.ui.study.StudyScreen
import com.physcard.physics.ui.study.StudyStage
import com.physcard.physics.ui.study.StudySummary
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
class UiScreensTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun darkHomeRenders() {
        compose.setContent {
            PhysicsFormulaTheme(ThemeMode.DARK) {
                HomeScreen(
                    snapshot = sampleHome(),
                    onStartStudy = {},
                    onOpenLibrary = {},
                    onOpenModule = {},
                    onOpenStats = {},
                )
            }
        }
        compose.onNodeWithText("物理公式记忆").assertIsDisplayed()
        save("home-dark")
    }

    @Test
    fun libraryRendersSearchAndModuleFilter() {
        compose.setContent {
            PhysicsFormulaTheme(ThemeMode.LIGHT) {
                LibraryScreen(
                    state = sampleLibrary(),
                    initialModule = null,
                    onOpenFormula = {},
                )
            }
        }
        compose.onNodeWithText("公式库").assertIsDisplayed()
        compose.onNodeWithText("搜索公式、章节或符号").assertIsDisplayed()
        save("library")
    }

    @Test
    fun statsRendersChartsAndRecentReviews() {
        compose.setContent {
            PhysicsFormulaTheme(ThemeMode.LIGHT) {
                StatsScreen(stats = sampleStats(), onStartStudy = {})
            }
        }
        compose.onNodeWithText("学习统计").assertIsDisplayed()
        compose.onNodeWithText("最近 7 天").assertIsDisplayed()
        save("stats")
    }

    @Test
    fun settingsRendersLearningAndDataSections() {
        compose.setContent {
            PhysicsFormulaTheme(ThemeMode.LIGHT) {
                SettingsScreen(
                    settings = com.physcard.physics.data.repository.AppSettings(),
                    action = DataActionState.Idle,
                    onThemeChange = {},
                    onRetentionChange = {},
                    onDailyNewChange = {},
                    onExport = {},
                    onImport = {},
                    onReset = {},
                    onDismissAction = {},
                    suggestedExportName = "physics-formulas-20261017.json",
                )
            }
        }
        compose.onNodeWithText("设置").assertIsDisplayed()
        compose.onNodeWithText("目标保持率").assertIsDisplayed()
        compose.onNodeWithText("导出公式与题目").assertIsDisplayed()
        save("settings")
    }

    @Test
    fun formulaDetailRendersAbilitiesAndConditions() {
        compose.setContent {
            PhysicsFormulaTheme(ThemeMode.LIGHT) {
                FormulaDetailScreen(
                    state = sampleDetail(),
                    onBack = {},
                    onPractice = { _, _ -> },
                    onOpenFormula = {},
                )
            }
        }
        compose.onAllNodesWithText("匀变速直线运动的速度公式").assertCountEquals(2)
        save("formula-detail")
    }

    @Test
    fun studySummaryRenders() {
        compose.setContent {
            PhysicsFormulaTheme(ThemeMode.LIGHT) {
                StudyScreen(
                    state = StudyUiState(
                        stage = StudyStage.COMPLETE,
                        item = sampleDetailItem(),
                        index = 5,
                        total = 5,
                        summary = StudySummary(
                            answered = 5,
                            correct = 4,
                            durationMinutes = 6,
                            newFormulas = 1,
                            levelUps = listOf("公式填空 提升到 Lv.2"),
                            ratings = mapOf(
                                ReviewRating.AGAIN to 1,
                                ReviewRating.HARD to 1,
                                ReviewRating.GOOD to 2,
                                ReviewRating.EASY to 1,
                            ),
                            lastIntervalMinutes = 4320,
                        ),
                        scheduleNotice = "下次复习：3 天 · Lv.2",
                        sessionMode = SessionMode.DAILY,
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
        compose.onNodeWithText("这一组完成了。").assertIsDisplayed()
        save("study-summary")
    }

    private fun save(name: String) {
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(
            view.width.coerceAtLeast(1),
            view.height.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        view.draw(Canvas(bitmap))
        val output = File("build/screenshots/$name.png")
        output.parentFile?.mkdirs()
        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun mechanicsModule() = FormulaModule(
        id = "mechanics",
        name = "力学",
        description = "运动、力、能量与动量",
        accent = Color(0xFF2F6F55),
        orderIndex = 0,
    )

    private fun emModule() = FormulaModule(
        id = "electromagnetism",
        name = "电磁学",
        description = "电场、电路、磁场与电磁感应",
        accent = Color(0xFF8A5B8E),
        orderIndex = 1,
    )

    private fun sampleFormula(): Formula = Formula(
        id = "velocity_time",
        title = "匀变速直线运动的速度公式",
        moduleId = "mechanics",
        chapter = "匀变速直线运动",
        textbook = "人教版高中物理（2019 版）",
        latex = """v = v_0 + at""",
        conditions = "加速度恒定的直线运动。v₀ 为初速度，v 为经过时间 t 后的末速度；先规定正方向，各量带符号代入。",
        variables = listOf(
            FormulaVariable("v", "末速度", "m/s"),
            FormulaVariable("v_0", "初速度", "m/s"),
            FormulaVariable("a", "加速度", "m/s²"),
            FormulaVariable("t", "时间", "s"),
        ),
        confusions = listOf(
            FormulaConfusion("""v = at""", "只有当 v₀ = 0 时才简化为 v = at。"),
            FormulaConfusion("""x = v_0t + \frac{1}{2}at^2""", "这是位移公式，不是速度公式。"),
        ),
        relatedIds = listOf("displacement_time"),
        orderIndex = 0,
    )

    private fun sampleDetailItem(): com.physcard.physics.domain.session.StudyItem {
        val formula = sampleFormula()
        return com.physcard.physics.domain.session.StudyItem(
            formula = formula,
            ability = Ability.FULL_FORMULA,
            question = com.physcard.physics.domain.model.Question(
                id = "q1",
                formulaId = formula.id,
                ability = Ability.FULL_FORMULA,
                prompt = "下列哪一项是速度公式？",
                stimulusLatex = null,
                options = listOf(
                    com.physcard.physics.domain.model.QuestionOption(0, latex = """v = v_0 + at"""),
                    com.physcard.physics.domain.model.QuestionOption(1, latex = """v = at"""),
                    com.physcard.physics.domain.model.QuestionOption(2, latex = """x = v_0t"""),
                    com.physcard.physics.domain.model.QuestionOption(3, latex = """v^2 - v_0^2 = 2ax"""),
                ),
                correctIndex = 0,
                explanation = "标准公式为 v = v₀ + at。",
            ),
            state = reviewState(Ability.FULL_FORMULA, 3),
            isNewFormula = false,
            showFormulaIntro = false,
        )
    }

    private fun reviewState(ability: Ability, level: Int): ReviewState = ReviewState(
        cardKey = "velocity_time::${ability.wireName}",
        formulaId = "velocity_time",
        ability = ability,
        introduced = true,
        snapshot = FsrsSnapshot(
            cardId = ability.ordinal + 1,
            dueAt = System.currentTimeMillis() + 86_400_000,
        ),
        reviewCount = level,
        lapseCount = 0,
        correctCount = level - 1,
        level = level,
        levelPoints = MemoryLevelPolicy().thresholds[level - 1],
        createdAt = 0,
        updatedAt = 0,
    )

    private fun sampleHome(): HomeSnapshot = HomeSnapshot(
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
            ModuleProgress(mechanicsModule(), 30, 9, 2, 2.4f),
            ModuleProgress(emModule(), 25, 4, 1, 1.8f),
        ),
        remainingToday = 23,
    )

    private fun sampleLibrary(): LibraryUiState {
        val formula = sampleFormula()
        return LibraryUiState(
            loading = false,
            modules = listOf(mechanicsModule(), emModule()),
            formulas = listOf(
                FormulaCardUi(formula, mechanicsModule(), 3, 3, 5, 1, 6),
                FormulaCardUi(
                    formula.copy(
                        id = "coulomb",
                        title = "库仑定律",
                        moduleId = "electromagnetism",
                        chapter = "静电场",
                        latex = """F = k\frac{q_1q_2}{r^2}""",
                    ),
                    emModule(),
                    2,
                    2,
                    5,
                    0,
                    2,
                ),
            ),
        )
    }

    private fun sampleStats(): StudyStats = StudyStats(
        totalReviews = 128,
        totalCorrect = 109,
        totalStudyMinutes = 246,
        streakDays = 6,
        todayReviews = 8,
        todayCorrect = 7,
        todayNewFormulas = 1,
        learnedFormulas = 18,
        masteredFormulas = 4,
        totalFormulas = 81,
        levelDistribution = listOf(12, 24, 20, 15, 7, 2, 1, 0),
        lastSevenDays = listOf(
            DailyStat(0, "周一", 12, 10),
            DailyStat(1, "周二", 8, 7),
            DailyStat(2, "周三", 0, 0),
            DailyStat(3, "周四", 16, 14),
            DailyStat(4, "周五", 9, 8),
            DailyStat(5, "周六", 20, 17),
            DailyStat(6, "周日", 8, 7),
        ),
        moduleProgress = listOf(
            ModuleProgress(mechanicsModule(), 30, 9, 2, 2.4f),
            ModuleProgress(emModule(), 25, 4, 1, 1.8f),
        ),
        recentReviews = listOf(
            RecentReview("匀变速直线运动的速度公式", Ability.FULL_FORMULA, true, ReviewRating.GOOD, System.currentTimeMillis()),
            RecentReview("库仑定律", Ability.APPLICABLE_CONDITION, false, ReviewRating.AGAIN, System.currentTimeMillis() - 3_600_000),
        ),
    )

    private fun sampleDetail(): FormulaDetailUiState = FormulaDetailUiState(
        loading = false,
        formula = sampleFormula(),
        module = mechanicsModule(),
        abilities = Ability.entries.map { ability ->
            AbilityStatus(ability, reviewState(ability, (ability.ordinal % 5) + 1))
        },
        related = emptyList(),
        averageLevel = 3,
        introducedAbilities = 5,
    )
}
