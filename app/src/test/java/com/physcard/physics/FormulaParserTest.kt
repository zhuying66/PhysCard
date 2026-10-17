package com.physcard.physics

import com.physcard.physics.data.seed.FormulaSeedDto
import com.physcard.physics.data.seed.QuestionSeedDto
import com.physcard.physics.ui.components.FormulaParser
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FormulaParserTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun everyBundledFormulaAndLatexOptionParses() {
        val formulas = json.decodeFromString<List<FormulaSeedDto>>(
            File("src/main/assets/formulas.json").readText(),
        )
        val questions = json.decodeFromString<List<QuestionSeedDto>>(
            File("src/main/assets/questions.json").readText(),
        )
        var parsed = 0
        formulas.forEach { formula ->
            FormulaParser(formula.latex).parse()
            parsed++
        }
        questions.forEach { question ->
            question.stimulusLatex?.takeIf { it.isNotBlank() }?.let {
                FormulaParser(it).parse()
                parsed++
            }
            question.options.forEach { option ->
                option.latex?.takeIf { it.isNotBlank() }?.let {
                    FormulaParser(it).parse()
                    parsed++
                }
            }
        }
        assertTrue("expected to parse a realistic number of expressions", parsed > 800)
    }

    @Test
    fun fractionAndRootNestingProducesNodes() {
        val nodes = FormulaParser(
            """v = \sqrt{\frac{2\left(x - v_0t\right)}{t^2}} + \Delta v""",
        ).parse()
        assertTrue(nodes.isNotEmpty())
        assertTrue(nodes.any { it is com.physcard.physics.ui.components.FormulaNode.Radical })
    }

    @Test
    fun fillBlankIsRepresentedExplicitly() {
        val nodes = FormulaParser("""v = v_0 + \boxed{?}""").parse()
        assertTrue(nodes.any { it is com.physcard.physics.ui.components.FormulaNode.Blank })
    }
}
