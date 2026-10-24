package com.physcard.physics

import androidx.test.core.app.ApplicationProvider
import com.physcard.physics.data.repository.ContentException
import com.physcard.physics.data.seed.ConfusionSeedDto
import com.physcard.physics.data.seed.ExportBundleDto
import com.physcard.physics.data.seed.FormulaSeedDto
import com.physcard.physics.data.seed.OptionSeedDto
import com.physcard.physics.data.seed.QuestionSeedDto
import com.physcard.physics.data.seed.VariableSeedDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = PhysicsApp::class)
class ContentImportTest {
    private lateinit var app: PhysicsApp

    @Before
    fun setUp(): Unit = runBlocking(Dispatchers.IO) {
        app = ApplicationProvider.getApplicationContext()
        app.container.database.clearAllTables()
        app.container.contentRepository.syncBuiltInContent()
        app.container.studyRepository.ensureReviewStates()
    }

    @After
    fun tearDown(): Unit = runBlocking(Dispatchers.IO) {
        app.container.database.clearAllTables()
    }

    @Test
    fun importingTheSameExportIsIdempotent() = runBlocking(Dispatchers.IO) {
        val exported = app.container.contentRepository.exportJson("test")
        val report = app.container.contentRepository.importJson(exported)
        assertEquals(0, report.importedFormulas)
        assertEquals(0, report.importedQuestions)
        assertEquals(91, report.skippedFormulas)
        assertEquals(1365, report.skippedQuestions)
        assertEquals(91, app.container.contentRepository.getAllFormulas().size)
    }

    @Test
    fun validCustomBundleAddsFormulaQuestionsAndReviewStatesWithoutDuplicates() = runBlocking(Dispatchers.IO) {
        val custom = customBundle()
        val encoded = app.container.json.encodeToString(custom)
        val report = app.container.contentRepository.importJson(encoded)
        assertEquals(1, report.importedFormulas)
        assertEquals(5, report.importedQuestions)
        app.container.studyRepository.ensureReviewStates()
        assertEquals(92, app.container.contentRepository.getAllFormulas().size)
        assertEquals(460, app.container.database.reviewDao().getAllStates().size)
        assertEquals(1370, app.container.database.questionDao().count())

        val duplicate = app.container.contentRepository.importJson(encoded)
        assertEquals(0, duplicate.importedFormulas)
        assertEquals(0, duplicate.importedQuestions)
        assertEquals(1, duplicate.skippedFormulas)
    }

    @Test
    fun malformedJsonReturnsReadableFailure() = runBlocking(Dispatchers.IO) {
        val failure = runCatching {
            app.container.contentRepository.importJson("""{"schemaVersion":1,"formulas":[]}""")
        }.exceptionOrNull()
        assertTrue(failure is ContentException)
        assertTrue(failure?.message?.contains("questions") == true)
    }

    private fun customBundle(): ExportBundleDto {
        val formula = FormulaSeedDto(
            id = "test_custom_formula",
            title = "测试自定义公式",
            module = "mechanics",
            chapter = "测试章节",
            latex = """s = vt""",
            conditions = "物体做匀速直线运动。",
            variables = listOf(
                VariableSeedDto("s", "位移", "m"),
                VariableSeedDto("v", "速度", "m/s"),
                VariableSeedDto("t", "时间", "s"),
            ),
            confusions = listOf(
                ConfusionSeedDto("""s = \frac{v}{t}""", "位移等于速度乘时间。"),
            ),
            relatedIds = emptyList(),
            orderIndex = 999,
        )
        val abilities = listOf(
            "FULL_FORMULA" to "下列哪一项是测试公式？",
            "FORMULA_FILL" to "补全公式：",
            "FORMULA_TRANSFORM" to "由上式求解 t：",
            "APPLICABLE_CONDITION" to "该公式的适用条件是：",
            "CONFUSION_DISCRIMINATION" to "物体做匀速直线运动，求位移。应使用哪个公式？",
        )
        val questions = abilities.mapIndexed { index, (ability, prompt) ->
            QuestionSeedDto(
                id = "test_custom_question_$index",
                formulaId = formula.id,
                ability = ability,
                prompt = prompt,
                stimulusLatex = if (ability == "FULL_FORMULA") null else formula.latex,
                options = if (ability == "APPLICABLE_CONDITION") {
                    listOf(
                        OptionSeedDto(text = "物体做匀速直线运动。"),
                        OptionSeedDto(text = "物体做匀加速直线运动。"),
                        OptionSeedDto(text = "只适用于曲线运动。"),
                        OptionSeedDto(text = "不需要任何条件。"),
                    )
                } else {
                    listOf(
                        OptionSeedDto(latex = formula.latex),
                        OptionSeedDto(latex = """s = \frac{v}{t}"""),
                        OptionSeedDto(latex = """s = v + t"""),
                        OptionSeedDto(latex = """s = v^2t"""),
                    )
                },
                correctIndex = 0,
                explanation = "标准形式为 s = vt。",
            )
        }
        return ExportBundleDto(
            schemaVersion = 1,
            appVersion = "test",
            exportedAt = "2026-10-17T00:00:00Z",
            formulas = listOf(formula),
            questions = questions,
        )
    }
}
