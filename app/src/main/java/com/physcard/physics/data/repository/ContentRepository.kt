package com.physcard.physics.data.repository

import androidx.compose.ui.graphics.Color
import androidx.room.withTransaction
import com.physcard.physics.data.db.AppDatabase
import com.physcard.physics.data.db.FormulaEntity
import com.physcard.physics.data.db.QuestionAnswerEntity
import com.physcard.physics.data.db.QuestionEntity
import com.physcard.physics.data.db.QuestionOptionEntity
import com.physcard.physics.data.seed.ConfusionSeedDto
import com.physcard.physics.data.seed.ExportBundleDto
import com.physcard.physics.data.seed.FormulaSeedDto
import com.physcard.physics.data.seed.ModuleSeedDto
import com.physcard.physics.data.seed.OptionSeedDto
import com.physcard.physics.data.seed.QuestionSeedDto
import com.physcard.physics.data.seed.SeedBundle
import com.physcard.physics.data.seed.SeedException
import com.physcard.physics.data.seed.SeedLoader
import com.physcard.physics.data.seed.SeedValidationException
import com.physcard.physics.data.seed.VariableSeedDto
import com.physcard.physics.domain.model.Ability
import com.physcard.physics.domain.model.Formula
import com.physcard.physics.domain.model.FormulaConfusion
import com.physcard.physics.domain.model.FormulaModule
import com.physcard.physics.domain.model.FormulaVariable
import com.physcard.physics.domain.model.Question
import com.physcard.physics.domain.model.QuestionOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant

class ContentException(message: String, cause: Throwable? = null) : Exception(message, cause)

data class ImportReport(
    val importedFormulas: Int,
    val skippedFormulas: Int,
    val importedQuestions: Int,
    val skippedQuestions: Int,
    val warnings: List<String>,
)

data class SyncReport(
    val formulas: Int,
    val questions: Int,
    val wasFirstSeed: Boolean,
)

data class QuestionBank(
    private val byFormulaAndAbility: Map<String, Map<Ability, List<Question>>>,
) {
    fun questions(formulaId: String, ability: Ability): List<Question> =
        byFormulaAndAbility[formulaId]?.get(ability).orEmpty()

    fun questionFor(formulaId: String, ability: Ability, reviewCount: Int): Question? {
        val candidates = questions(formulaId, ability)
        if (candidates.isEmpty()) return null
        return candidates[Math.floorMod(reviewCount + formulaId.hashCode(), candidates.size)]
    }

    fun hasAllAbilities(formulaId: String): Boolean =
        Ability.entries.all { !questions(formulaId, it).isEmpty() }
}

class ContentRepository(
    private val database: AppDatabase,
    private val seedLoader: SeedLoader,
    private val json: Json,
) {
    private val formulaDao = database.formulaDao()
    private val questionDao = database.questionDao()

    private val _modules = MutableStateFlow<List<FormulaModule>>(emptyList())
    val modules: StateFlow<List<FormulaModule>> = _modules.asStateFlow()

    private val bankMutex = Mutex()
    @Volatile
    private var cachedBank: QuestionBank? = null

    fun observeFormulas(): Flow<List<Formula>> =
        formulaDao.observeAll().map { entities -> entities.mapNotNull { it.toDomainOrNull() } }

    suspend fun ensureModules(): List<FormulaModule> {
        if (_modules.value.isNotEmpty()) return _modules.value
        val bundle = seedLoader.loadBuiltIn()
        val modules = bundle.modules.sortedBy { it.orderIndex }.map { it.toDomain() }
        _modules.value = modules
        return modules
    }

    suspend fun getAllFormulas(): List<Formula> =
        formulaDao.getAll().map { entity ->
            entity.toDomainOrNull()
                ?: throw ContentException("公式「${entity.title}」的数据已损坏，无法读取。")
        }

    suspend fun getFormula(id: String): Formula? = formulaDao.getById(id)?.toDomainOrNull()

    suspend fun syncBuiltInContent(): SyncReport {
        ensureModules()
        val bundle = seedLoader.loadBuiltIn()
        val wasFirstSeed = formulaDao.count() == 0
        val questionIds = bundle.questions.map { it.id }
        database.withTransaction {
            formulaDao.upsertAll(bundle.formulas.map { it.toEntity() })
            if (questionIds.isNotEmpty()) {
                // Replace built-in question bundles so fixes to wording or answers land on
                // upgrade, while review states and history remain attached to formulas.
                questionDao.deleteQuestions(questionIds)
                insertQuestions(bundle.questions)
            }
        }
        invalidateQuestionCache()
        return SyncReport(
            formulas = bundle.formulas.size,
            questions = bundle.questions.size,
            wasFirstSeed = wasFirstSeed,
        )
    }

    suspend fun exportJson(appVersion: String): String {
        val formulaEntities = formulaDao.getAll()
        val formulas = formulaEntities.mapNotNull { it.toSeedDtoOrNull() }
        val questionEntities = questionDao.getAll()
        val options = questionDao.getAllOptions().groupBy { it.questionId }
        val answers = questionDao.getAllAnswers().associateBy { it.questionId }
        val questions = questionEntities.mapNotNull { question ->
            val answer = answers[question.id] ?: return@mapNotNull null
            val questionOptions = options[question.id].orEmpty()
            if (questionOptions.isEmpty()) return@mapNotNull null
            try {
                QuestionSeedDto(
                    id = question.id,
                    formulaId = question.formulaId,
                    ability = question.ability,
                    prompt = question.prompt,
                    stimulusLatex = question.stimulusLatex,
                    options = questionOptions.sortedBy { it.orderIndex }.map {
                        OptionSeedDto(latex = it.latex, text = it.text)
                    },
                    correctIndex = answer.correctOptionIndex,
                    explanation = answer.explanation,
                )
            } catch (e: Exception) {
                null
            }
        }
        val bundle = ExportBundleDto(
            schemaVersion = 1,
            appVersion = appVersion,
            exportedAt = Instant.now().toString(),
            formulas = formulas,
            questions = questions,
        )
        return json.encodeToString(bundle)
    }

    suspend fun importJson(text: String): ImportReport {
        val export = try {
            seedLoader.parseExport(text)
        } catch (e: SeedException) {
            throw ContentException(e.message ?: "无法解析题库文件。", e)
        }
        val currentModules = ensureModules().map { module ->
            ModuleSeedDto(
                id = module.id,
                name = module.name,
                description = module.description,
                accent = module.accent.toHex(),
                orderIndex = module.orderIndex,
            )
        }
        val existingFormulas = formulaDao.getAll()
        val existingIds = existingFormulas.map { it.id }.toSet()
        val existingTitleFormula = existingFormulas
            .map { "${it.title.trim()}::${it.latex.trim()}" }
            .toSet()
        val incomingFormulaIds = export.formulas.map { it.id }.toSet()
        // A small custom bundle can legitimately reference a built-in related formula.
        val sanitizedFormulas = export.formulas.map { formula ->
            formula.copy(
                relatedIds = formula.relatedIds
                    .filter { it in incomingFormulaIds || it in existingIds }
                    .distinct(),
            )
        }
        val bundle = SeedBundle(
            modules = currentModules,
            formulas = sanitizedFormulas,
            questions = export.questions,
        )
        try {
            seedLoader.validate(bundle, requireAllAbilities = false)
        } catch (e: SeedValidationException) {
            throw ContentException("题库校验失败：${e.message}", e)
        }
        val existingQuestionIds = questionDao.getAll().map { it.id }.toSet()
        val existingQuestionKeys = questionDao.getAll().associate { question ->
            semanticQuestionKey(question.formulaId, question.ability, question.prompt) to question.id
        }

        val formulasToInsert = mutableListOf<FormulaSeedDto>()
        val skippedFormulaIds = mutableSetOf<String>()
        var skippedFormulas = 0
        val warnings = mutableListOf<String>()
        sanitizedFormulas.forEach { formula ->
            val key = "${formula.title.trim()}::${formula.latex.trim()}"
            when {
                formula.id in existingIds -> {
                    skippedFormulas++
                    warnings += "跳过重复公式：${formula.title}"
                }
                key in existingTitleFormula -> {
                    skippedFormulas++
                    skippedFormulaIds += formula.id
                    warnings += "已存在同名同式公式，跳过：${formula.title}"
                }
                else -> formulasToInsert += formula
            }
        }

        val importedFormulaIds = formulasToInsert.map { it.id }.toSet()
        val questionsToInsert = mutableListOf<QuestionSeedDto>()
        var skippedQuestions = 0
        export.questions.forEach { question ->
            if (question.formulaId in skippedFormulaIds) {
                skippedQuestions++
                return@forEach
            }
            val formulaExists = question.formulaId in existingIds || question.formulaId in importedFormulaIds
            if (!formulaExists) {
                skippedQuestions++
                warnings += "题目 ${question.id} 引用的公式不存在，已跳过"
                return@forEach
            }
            val semanticKey = semanticQuestionKey(question.formulaId, question.ability, question.prompt)
            if (question.id in existingQuestionIds || semanticKey in existingQuestionKeys) {
                skippedQuestions++
            } else {
                questionsToInsert += question
            }
        }

        if (formulasToInsert.isNotEmpty() || questionsToInsert.isNotEmpty()) {
            database.withTransaction {
                if (formulasToInsert.isNotEmpty()) {
                    formulaDao.upsertAll(formulasToInsert.map { it.toEntity() })
                }
                if (questionsToInsert.isNotEmpty()) {
                    insertQuestions(questionsToInsert)
                }
            }
            invalidateQuestionCache()
        }

        return ImportReport(
            importedFormulas = formulasToInsert.size,
            skippedFormulas = skippedFormulas,
            importedQuestions = questionsToInsert.size,
            skippedQuestions = skippedQuestions,
            warnings = warnings.distinct().take(8),
        )
    }

    suspend fun questionBank(forceReload: Boolean = false): QuestionBank {
        if (!forceReload) {
            cachedBank?.let { return it }
        }
        return bankMutex.withLock {
            val existing = cachedBank
            if (!forceReload && existing != null) return@withLock existing
            val questions = questionDao.getAll()
            val options = questionDao.getAllOptions().groupBy { it.questionId }
            val answers = questionDao.getAllAnswers().associateBy { it.questionId }
            val grouped = mutableMapOf<String, MutableMap<Ability, MutableList<Question>>>()
            questions.forEach { entity ->
                val ability = runCatching { Ability.fromWire(entity.ability) }.getOrNull()
                    ?: throw ContentException("题目 ${entity.id} 的考查能力无法识别。")
                val answer = answers[entity.id]
                    ?: throw ContentException("题目 ${entity.id} 缺少答案数据。")
                val questionOptions = options[entity.id].orEmpty()
                    .sortedBy { it.orderIndex }
                    .map { QuestionOption(it.orderIndex, it.latex, it.text) }
                if (questionOptions.size != 4) {
                    throw ContentException("题目 ${entity.id} 的选项数量不是 4。")
                }
                val question = Question(
                    id = entity.id,
                    formulaId = entity.formulaId,
                    ability = ability,
                    prompt = entity.prompt,
                    stimulusLatex = entity.stimulusLatex,
                    options = questionOptions,
                    correctIndex = answer.correctOptionIndex,
                    explanation = answer.explanation,
                )
                grouped
                    .getOrPut(entity.formulaId) { mutableMapOf() }
                    .getOrPut(ability) { mutableListOf() }
                    .add(question)
            }
            val bank = QuestionBank(grouped)
            cachedBank = bank
            bank
        }
    }

    fun invalidateQuestionCache() {
        cachedBank = null
    }

    suspend fun validateImportedModules(modules: List<ModuleSeedDto>): Boolean {
        val known = ensureModules().map { it.id }.toSet()
        return modules.all { it.id in known }
    }

    private suspend fun insertQuestions(questions: List<QuestionSeedDto>) {
        val questionEntities = mutableListOf<QuestionEntity>()
        val optionEntities = mutableListOf<QuestionOptionEntity>()
        val answerEntities = mutableListOf<QuestionAnswerEntity>()
        questions.forEach { question ->
            questionEntities += QuestionEntity(
                id = question.id,
                formulaId = question.formulaId,
                ability = question.ability,
                prompt = question.prompt,
                stimulusLatex = question.stimulusLatex,
            )
            question.options.forEachIndexed { index, option ->
                optionEntities += QuestionOptionEntity(
                    questionId = question.id,
                    orderIndex = index,
                    latex = option.latex,
                    text = option.text,
                )
            }
            answerEntities += QuestionAnswerEntity(
                questionId = question.id,
                correctOptionIndex = question.correctIndex,
                explanation = question.explanation,
            )
        }
        questionDao.upsertQuestions(questionEntities)
        questionDao.upsertOptions(optionEntities)
        questionDao.upsertAnswers(answerEntities)
    }

    private fun semanticQuestionKey(formulaId: String, ability: String, prompt: String): String =
        "$formulaId::$ability::${prompt.trim()}"

    private fun FormulaSeedDto.toEntity(): FormulaEntity = FormulaEntity(
        id = id,
        title = title,
        module = module,
        chapter = chapter,
        textbook = textbook,
        latex = latex,
        conditions = conditions,
        variablesJson = json.encodeToString(variables),
        confusionsJson = json.encodeToString(confusions),
        relatedIdsJson = json.encodeToString(relatedIds),
        orderIndex = orderIndex,
    )

    private fun FormulaEntity.toDomainOrNull(): Formula? = runCatching {
        Formula(
            id = id,
            title = title,
            moduleId = module,
            chapter = chapter,
            textbook = textbook,
            latex = latex,
            conditions = conditions,
            variables = json.decodeFromString<List<VariableSeedDto>>(variablesJson).map {
                FormulaVariable(it.symbol, it.name, it.unit)
            },
            confusions = json.decodeFromString<List<ConfusionSeedDto>>(confusionsJson).map {
                FormulaConfusion(it.wrong, it.why)
            },
            relatedIds = json.decodeFromString<List<String>>(relatedIdsJson),
            orderIndex = orderIndex,
        )
    }.getOrNull()

    private fun FormulaEntity.toSeedDtoOrNull(): FormulaSeedDto? = runCatching {
        FormulaSeedDto(
            id = id,
            title = title,
            module = module,
            chapter = chapter,
            textbook = textbook,
            latex = latex,
            conditions = conditions,
            variables = json.decodeFromString<List<VariableSeedDto>>(variablesJson),
            confusions = json.decodeFromString<List<ConfusionSeedDto>>(confusionsJson),
            relatedIds = json.decodeFromString<List<String>>(relatedIdsJson),
            orderIndex = orderIndex,
        )
    }.getOrNull()

    private fun ModuleSeedDto.toDomain(): FormulaModule = FormulaModule(
        id = id,
        name = name,
        description = description,
        accent = parseColor(accent),
        orderIndex = orderIndex,
    )

    private fun parseColor(value: String): Color {
        val cleaned = value.removePrefix("#")
        val argb = when (cleaned.length) {
            6 -> 0xFF000000L or cleaned.toLong(16)
            8 -> cleaned.toLong(16)
            else -> 0xFF2F6F55
        }
        return Color(argb)
    }

    private fun Color.toHex(): String {
        val argb = (value shr 32).toInt()
        return "#%06X".format(argb and 0xFFFFFF)
    }

    companion object {
        const val SEED_VERSION = 2
        const val SCHEMA_VERSION = 1
    }
}
