package com.physcard.physics.data.repository

import androidx.room.withTransaction
import com.physcard.physics.data.db.AppDatabase
import com.physcard.physics.data.db.ReviewLogEntity
import com.physcard.physics.data.db.ReviewStateEntity
import com.physcard.physics.domain.fsrs.FsrsSchedulingService
import com.physcard.physics.domain.level.MemoryLevelPolicy
import com.physcard.physics.domain.model.Ability
import com.physcard.physics.domain.model.DailyStat
import com.physcard.physics.domain.model.FsrsReviewResult
import com.physcard.physics.domain.model.FsrsSnapshot
import com.physcard.physics.domain.model.FsrsState
import com.physcard.physics.domain.model.Formula
import com.physcard.physics.domain.model.HomeSnapshot
import com.physcard.physics.domain.model.LevelUpdate
import com.physcard.physics.domain.model.ModuleProgress
import com.physcard.physics.domain.model.Question
import com.physcard.physics.domain.model.RecentReview
import com.physcard.physics.domain.model.ReviewLog
import com.physcard.physics.domain.model.ReviewRating
import com.physcard.physics.domain.model.ReviewState
import com.physcard.physics.domain.model.StudyStats
import com.physcard.physics.domain.session.SessionMode
import com.physcard.physics.domain.session.StudyItem
import com.physcard.physics.domain.session.StudySession
import com.physcard.physics.domain.time.AppClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

data class ReviewOutcome(
    val state: ReviewState,
    val level: LevelUpdate,
    val fsrs: FsrsReviewResult,
)

class StudyRepository(
    private val database: AppDatabase,
    private val contentRepository: ContentRepository,
    private val scheduler: FsrsSchedulingService,
    private val levelPolicy: MemoryLevelPolicy,
    private val clock: AppClock,
) {
    private val formulaDao = database.formulaDao()
    private val questionDao = database.questionDao()
    private val reviewDao = database.reviewDao()

    suspend fun ensureReviewStates(): Int {
        val formulas = contentRepository.getAllFormulas()
        val bank = contentRepository.questionBank()
        val existing = reviewDao.getAllStates().associateBy { it.cardKey }
        val now = clock.nowMillis()
        val missing = mutableListOf<ReviewStateEntity>()
        formulas.forEach { formula ->
            Ability.entries.forEach { ability ->
                if (bank.questions(formula.id, ability).isEmpty()) return@forEach
                val key = cardKey(formula.id, ability)
                if (key !in existing) {
                    missing += newStateEntity(formula.id, ability, now)
                }
            }
        }
        if (missing.isNotEmpty()) {
            reviewDao.upsertStates(missing)
        }
        return missing.size
    }

    suspend fun buildDailySession(dailyNewLimit: Int): StudySession {
        ensureReviewStates()
        val now = clock.nowMillis()
        val formulas = contentRepository.getAllFormulas().sortedBy { it.orderIndex }
        val bank = contentRepository.questionBank()
        val states = reviewDao.getAllStates()
        val logs = reviewDao.getAllLogs()
        val statesByFormula = states.groupBy { it.formulaId }
        val formulaById = formulas.associateBy { it.id }
        val todayKey = clock.dayKey()

        val dueItems = states
            .asSequence()
            .filter { it.introduced && it.reviewCount > 0 && (it.dueAt ?: Long.MAX_VALUE) <= now }
            .sortedBy { it.dueAt }
            .mapNotNull { state ->
                val formula = formulaById[state.formulaId] ?: return@mapNotNull null
                val ability = runCatching { Ability.fromWire(state.ability) }.getOrNull()
                    ?: return@mapNotNull null
                val question = bank.questionFor(formula.id, ability, state.reviewCount)
                    ?: return@mapNotNull null
                StudyItem(
                    formula = formula,
                    ability = ability,
                    question = rotateOptions(question, state.reviewCount),
                    state = state.toDomain(),
                    isNewFormula = false,
                    showFormulaIntro = false,
                )
            }
            .take(MAX_DUE_ITEMS)
            .toList()

        val dailyNewIntroduced = logs
            .filter { it.wasFormulaFirstReview && it.localDate == todayKey }
            .map { it.formulaId }
            .distinct()
            .size
        val remainingNew = (dailyNewLimit - dailyNewIntroduced).coerceAtLeast(0)
        val newFormulas = formulas
            .filter { formula ->
                val statesForFormula = statesByFormula[formula.id].orEmpty()
                statesForFormula.isNotEmpty() && statesForFormula.all { it.reviewCount == 0 }
            }
            .take(remainingNew)

        val newItems = mutableListOf<StudyItem>()
        newFormulas.forEach { formula ->
            newItems += itemsForFormula(
                formula = formula,
                states = statesByFormula[formula.id].orEmpty(),
                bank = bank,
                isNewFormula = true,
                forceIntro = true,
            )
        }

        val unfinishedFormulas = formulas
            .filter { formula ->
                formula !in newFormulas && formulaById[formula.id] != null
            }
            .mapNotNull { formula: Formula ->
                val statesForFormula = statesByFormula[formula.id].orEmpty()
                val reviewed = statesForFormula.count { it.reviewCount > 0 }
                val unreviewed = statesForFormula.count { it.reviewCount == 0 }
                val pair: Pair<Formula, Long>? = if (reviewed > 0 && unreviewed > 0) {
                    formula to (statesForFormula.maxOfOrNull { it.updatedAt } ?: 0L)
                } else {
                    null
                }
                pair
            }
            .sortedByDescending { it.second }
            .take(1)

        val unfinishedItems = mutableListOf<StudyItem>()
        unfinishedFormulas.forEach { (formula, _) ->
            unfinishedItems += itemsForFormula(
                formula = formula,
                states = statesByFormula[formula.id].orEmpty().filter { it.reviewCount == 0 },
                bank = bank,
                isNewFormula = false,
                forceIntro = true,
            )
        }

        val items = (dueItems + newItems + unfinishedItems).take(MAX_TOTAL_ITEMS)
        return StudySession(
            mode = SessionMode.DAILY,
            items = items,
            generatedAt = now,
        )
    }

    /**
     * Focused practice for a single formula.
     *
     * When [ability] is null the session covers every ability that has questions for this
     * formula (the "专项练习" entry point), so the learner sees the whole test set instead of
     * one question. When [ability] is set only that ability is practised (the per-ability row).
     */
    suspend fun buildFocusedSession(formulaId: String, ability: Ability?): StudySession {
        ensureReviewStates()
        val formula = contentRepository.getFormula(formulaId)
            ?: throw ContentException("找不到该公式，可能已被移除。")
        val bank = contentRepository.questionBank()
        val states = reviewDao.getStatesForFormula(formulaId)
        val statesByAbility = states.associateBy { it.ability }

        val targets = if (ability != null) {
            listOf(ability)
        } else {
            Ability.entries.filter { bank.questions(formulaId, it).isNotEmpty() }
        }
        if (targets.isEmpty()) {
            throw ContentException("这张公式卡还没有可用的练习题。")
        }

        val items = targets.mapNotNull { targetAbility ->
            val targetState = statesByAbility[targetAbility.wireName]
                ?: newStateEntity(formulaId, targetAbility, clock.nowMillis())
            val question = bank.questionFor(formulaId, targetAbility, targetState.reviewCount)
                ?: return@mapNotNull null
            StudyItem(
                formula = formula,
                ability = targetAbility,
                question = rotateOptions(question, targetState.reviewCount),
                state = targetState.toDomain(),
                isNewFormula = false,
                showFormulaIntro = targetState.reviewCount == 0,
            )
        }
        if (items.isEmpty()) {
            throw ContentException("这道题暂时没有可用题目。")
        }
        return StudySession(SessionMode.FOCUSED, items, clock.nowMillis())
    }

    suspend fun previewIntervals(item: StudyItem): Map<ReviewRating, Long> =
        scheduler.previewIntervals(item.state.snapshot, clock.nowMillis())

    suspend fun recordReview(
        item: StudyItem,
        rating: ReviewRating,
        correct: Boolean,
        selectedOption: Int,
        durationMs: Long,
    ): ReviewOutcome {
        val now = clock.nowMillis()
        val currentEntity = reviewDao.getState(cardKey(item.formula.id, item.ability))
            ?: newStateEntity(item.formula.id, item.ability, now)
        val current = currentEntity.toDomain()
        val wasFirstReview = current.reviewCount == 0
        val formulaFirstReview = reviewDao.reviewedAbilityCount(item.formula.id) == 0

        val fsrs = scheduler.review(
            snapshot = current.snapshot,
            rating = rating,
            durationMs = durationMs,
            reviewedAtMillis = now,
        )
        val reviewCountAfter = current.reviewCount + 1
        val correctCountAfter = current.correctCount + if (correct) 1 else 0
        val lapseCountAfter = current.lapseCount + if (rating == ReviewRating.AGAIN) 1 else 0
        val accuracyAfter = correctCountAfter.toFloat() / reviewCountAfter
        val level = levelPolicy.evaluate(
            levelBefore = current.level,
            pointsBefore = current.levelPoints,
            rating = rating,
            correct = correct,
            reviewCountAfter = reviewCountAfter,
            stabilityDaysAfter = fsrs.snapshot.stability ?: 0.0,
            accuracyAfter = accuracyAfter,
        )
        val updatedEntity = currentEntity.copy(
            introduced = true,
            fsrsState = fsrs.snapshot.state.wireName,
            step = fsrs.snapshot.step,
            stability = fsrs.snapshot.stability,
            difficulty = fsrs.snapshot.difficulty,
            dueAt = fsrs.snapshot.dueAt,
            lastReviewAt = fsrs.snapshot.lastReviewAt,
            reviewCount = reviewCountAfter,
            lapseCount = lapseCountAfter,
            correctCount = correctCountAfter,
            level = level.levelAfter,
            levelPoints = level.pointsAfter,
            updatedAt = now,
        )
        val log = ReviewLogEntity(
            cardKey = current.cardKey,
            formulaId = item.formula.id,
            ability = item.ability.wireName,
            reviewedAt = now,
            localDate = clock.dayKey(Instant.ofEpochMilli(now)),
            rating = rating.wireName,
            correct = correct,
            selectedOption = selectedOption,
            correctOption = item.question.correctIndex,
            scheduledMinutes = fsrs.intervalMinutes,
            elapsedDays = clock.elapsedDays(currentEntity.lastReviewAt, now),
            levelBefore = level.levelBefore,
            levelAfter = level.levelAfter,
            stabilityAfter = fsrs.snapshot.stability,
            difficultyAfter = fsrs.snapshot.difficulty,
            durationMs = durationMs.coerceIn(0, MAX_REVIEW_DURATION_MS),
            wasFirstReview = wasFirstReview,
            wasFormulaFirstReview = formulaFirstReview,
        )
        database.withTransaction {
            reviewDao.upsertStates(listOf(updatedEntity))
            reviewDao.insertLog(log)
        }
        return ReviewOutcome(
            state = updatedEntity.toDomain(),
            level = level,
            fsrs = fsrs,
        )
    }

    fun observeHomeSnapshot(settings: Flow<AppSettings>): Flow<HomeSnapshot> =
        combine(
            contentRepository.observeFormulas(),
            reviewDao.observeAllStates(),
            reviewDao.observeAllLogs(),
            settings,
            minuteTicker(),
        ) { formulas, states, logs, appSettings, _ ->
            buildHomeSnapshot(formulas, states, logs, appSettings)
        }

    fun observeStats(settings: Flow<AppSettings>): Flow<StudyStats> =
        combine(
            contentRepository.observeFormulas(),
            reviewDao.observeAllStates(),
            reviewDao.observeAllLogs(),
            contentRepository.modules,
            minuteTicker(),
        ) { formulas, states, logs, modules, _ ->
            buildStats(formulas, states, logs, modules)
        }

    fun observeStates(formulaId: String): Flow<List<ReviewState>> =
        reviewDao.observeAllStates().map { states ->
            states.filter { it.formulaId == formulaId }.map { it.toDomain() }
        }

    fun observeAllStates(): Flow<List<ReviewState>> =
        reviewDao.observeAllStates().map { states -> states.map { it.toDomain() } }

    suspend fun resetProgress() {
        database.withTransaction {
            reviewDao.deleteAllLogs()
            reviewDao.deleteAllStates()
        }
        ensureReviewStates()
    }

    private fun itemsForFormula(
        formula: Formula,
        states: List<ReviewStateEntity>,
        bank: QuestionBank,
        isNewFormula: Boolean,
        forceIntro: Boolean,
    ): List<StudyItem> {
        val ordered = states
            .filter { it.reviewCount == 0 }
            .sortedBy { runCatching { Ability.fromWire(it.ability).ordinal }.getOrDefault(99) }
        val items = mutableListOf<StudyItem>()
        var introPlaced = !forceIntro
        ordered.forEach { state ->
            val ability = runCatching { Ability.fromWire(state.ability) }.getOrNull() ?: return@forEach
            val question = bank.questionFor(formula.id, ability, state.reviewCount) ?: return@forEach
            val showIntro = !introPlaced
            introPlaced = true
            items += StudyItem(
                formula = formula,
                ability = ability,
                question = rotateOptions(question, state.reviewCount),
                state = state.toDomain(),
                isNewFormula = isNewFormula,
                showFormulaIntro = showIntro,
            )
        }
        return items
    }

    private fun rotateOptions(question: Question, reviewCount: Int): Question {
        val offset = Math.floorMod(question.id.hashCode() + reviewCount, 4)
        if (offset == 0) return question
        val rotated = question.options.drop(offset) + question.options.take(offset)
        val newCorrect = Math.floorMod(question.correctIndex - offset, 4)
        return question.copy(
            options = rotated.mapIndexed { index, option -> option.copy(index = index) },
            correctIndex = newCorrect,
        )
    }

    private fun buildHomeSnapshot(
        formulas: List<Formula>,
        stateEntities: List<ReviewStateEntity>,
        logEntities: List<ReviewLogEntity>,
        settings: AppSettings,
    ): HomeSnapshot {
        val now = clock.nowMillis()
        val today = clock.dayKey()
        val states = stateEntities.map { it.toDomain() }
        val logs = logEntities.map { it.toDomain() }
        val statesByFormula = states.groupBy { it.formulaId }
        val due = states.count { it.introduced && it.reviewCount > 0 && (it.dueAt ?: Long.MAX_VALUE) <= now }
        val newFormulaCount = formulas.count { formula ->
            val formulaStates = statesByFormula[formula.id].orEmpty()
            formulaStates.isNotEmpty() && formulaStates.all { it.reviewCount == 0 }
        }
        val todayLogs = logs.filter { clock.localDate(Instant.ofEpochMilli(it.reviewedAt)).toString() == today }
        val todayNew = todayLogs.filter { it.wasFormulaFirstReview }.map { it.formulaId }.distinct().size
        val learned = formulas.count { formula ->
            statesByFormula[formula.id].orEmpty().any { it.reviewCount > 0 }
        }
        val mastered = formulas.count { formula ->
            isFormulaMastered(statesByFormula[formula.id].orEmpty())
        }
        val remainingNew = (settings.dailyNewFormulas - todayNew).coerceAtLeast(0)
        return HomeSnapshot(
            dueCount = due,
            newFormulaCount = newFormulaCount,
            introducedFormulaCount = learned,
            totalFormulaCount = formulas.size,
            todayReviewed = todayLogs.size,
            todayNewFormulas = todayNew,
            todayCorrect = todayLogs.count { it.correct },
            streakDays = computeStreak(logs),
            dailyNewLimit = settings.dailyNewFormulas,
            masteredFormulas = mastered,
            modules = buildModuleProgress(formulas, statesByFormula),
            nextDueAt = states
                .filter { it.introduced && (it.dueAt ?: 0) > now }
                .minOfOrNull { it.dueAt ?: Long.MAX_VALUE },
            remainingToday = due + newFormulaCount.coerceAtMost(remainingNew) * ABILITIES_PER_FORMULA,
        )
    }

    private fun buildStats(
        formulas: List<Formula>,
        stateEntities: List<ReviewStateEntity>,
        logEntities: List<ReviewLogEntity>,
        modules: List<com.physcard.physics.domain.model.FormulaModule>,
    ): StudyStats {
        val states = stateEntities.map { it.toDomain() }
        val logs = logEntities.map { it.toDomain() }
        val byFormula = states.groupBy { it.formulaId }
        val today = LocalDate.parse(clock.dayKey())
        val todayKey = today.toString()
        val todayLogs = logs.filter {
            clock.localDate(Instant.ofEpochMilli(it.reviewedAt)).toString() == todayKey
        }
        val levelDistribution = MutableList(8) { 0 }
        formulas.forEach { formula ->
            val formulaStates = byFormula[formula.id].orEmpty().filter { it.reviewCount > 0 }
            if (formulaStates.isNotEmpty()) {
                val average = formulaStates.map { it.level }.average().roundToInt().coerceIn(1, 8)
                levelDistribution[average - 1] += 1
            }
        }
        val lastSeven = (6 downTo 0).map { offset ->
            val date = today.minusDays(offset.toLong())
            val key = date.toString()
            val dayLogs = logs.filter {
                clock.localDate(Instant.ofEpochMilli(it.reviewedAt)).toString() == key
            }
            DailyStat(
                epochDay = date.toEpochDay(),
                label = weekdayLabel(date.dayOfWeek.value),
                reviews = dayLogs.size,
                correct = dayLogs.count { it.correct },
            )
        }
        val learned = formulas.count { formula -> byFormula[formula.id].orEmpty().any { it.reviewCount > 0 } }
        val mastered = formulas.count { formula -> isFormulaMastered(byFormula[formula.id].orEmpty()) }
        val formulaTitles = formulas.associate { it.id to it.title }
        return StudyStats(
            totalReviews = logs.size,
            totalCorrect = logs.count { it.correct },
            totalStudyMinutes = logs.sumOf { it.durationMs } / 60_000,
            streakDays = computeStreak(logs),
            todayReviews = todayLogs.size,
            todayCorrect = todayLogs.count { it.correct },
            todayNewFormulas = todayLogs.filter { it.wasFormulaFirstReview }
                .map { it.formulaId }.distinct().size,
            learnedFormulas = learned,
            masteredFormulas = mastered,
            totalFormulas = formulas.size,
            levelDistribution = levelDistribution,
            lastSevenDays = lastSeven,
            moduleProgress = buildModuleProgress(formulas, byFormula),
            recentLogs = logs.sortedByDescending { it.reviewedAt }.take(12),
            recentReviews = logs.sortedByDescending { it.reviewedAt }.take(8).map { log ->
                RecentReview(
                    formulaTitle = formulaTitles[log.formulaId] ?: log.formulaId,
                    ability = log.ability,
                    correct = log.correct,
                    rating = log.rating,
                    reviewedAt = log.reviewedAt,
                )
            },
        )
    }

    private fun buildModuleProgress(
        formulas: List<Formula>,
        statesByFormula: Map<String, List<ReviewState>>,
    ): List<ModuleProgress> {
        val modules = contentRepository.modules.value
        if (modules.isEmpty()) return emptyList()
        return modules.sortedBy { it.orderIndex }.map { module ->
            val moduleFormulas = formulas.filter { it.moduleId == module.id }
            val averages = moduleFormulas.mapNotNull { formula ->
                val introduced = statesByFormula[formula.id].orEmpty().filter { it.reviewCount > 0 }
                if (introduced.isEmpty()) null else introduced.map { it.level }.average()
            }
            ModuleProgress(
                module = module,
                total = moduleFormulas.size,
                started = moduleFormulas.count { formula ->
                    statesByFormula[formula.id].orEmpty().any { it.reviewCount > 0 }
                },
                mastered = moduleFormulas.count { formula ->
                    isFormulaMastered(statesByFormula[formula.id].orEmpty())
                },
                averageLevel = if (averages.isEmpty()) 0f else averages.average().toFloat(),
            )
        }
    }

    private fun isFormulaMastered(states: List<ReviewState>): Boolean {
        val introduced = states.filter { it.reviewCount > 0 }
        return introduced.size >= 3 && introduced.map { it.level }.average() >= 5.0
    }

    private fun computeStreak(logs: List<ReviewLog>): Int {
        if (logs.isEmpty()) return 0
        val dates = logs.mapNotNull {
            runCatching { clock.localDate(Instant.ofEpochMilli(it.reviewedAt)) }.getOrNull()
        }.toSet()
        var cursor = clock.today()
        if (cursor !in dates) cursor = cursor.minusDays(1)
        var streak = 0
        while (cursor in dates) {
            streak++
            cursor = cursor.minusDays(1)
        }
        return streak
    }

    private fun minuteTicker(): Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(60_000)
        }
    }

    private fun weekdayLabel(dayOfWeek: Int): String = when (dayOfWeek) {
        1 -> "周一"
        2 -> "周二"
        3 -> "周三"
        4 -> "周四"
        5 -> "周五"
        6 -> "周六"
        else -> "周日"
    }

    private fun newStateEntity(formulaId: String, ability: Ability, now: Long): ReviewStateEntity {
        val key = cardKey(formulaId, ability)
        val cardId = (key.hashCode() and 0x7fffffff).coerceAtLeast(1)
        return ReviewStateEntity(
            cardKey = key,
            formulaId = formulaId,
            ability = ability.wireName,
            introduced = false,
            cardId = cardId,
            fsrsState = FsrsState.LEARNING.wireName,
            step = 0,
            stability = null,
            difficulty = null,
            dueAt = null,
            lastReviewAt = null,
            reviewCount = 0,
            lapseCount = 0,
            correctCount = 0,
            level = 1,
            levelPoints = 0,
            createdAt = now,
            updatedAt = now,
        )
    }

    private fun ReviewStateEntity.toDomain(): ReviewState = ReviewState(
        cardKey = cardKey,
        formulaId = formulaId,
        ability = Ability.fromWire(ability),
        introduced = introduced,
        snapshot = FsrsSnapshot(
            cardId = cardId,
            state = FsrsState.fromWire(fsrsState),
            step = step,
            stability = stability,
            difficulty = difficulty,
            dueAt = dueAt,
            lastReviewAt = lastReviewAt,
        ),
        reviewCount = reviewCount,
        lapseCount = lapseCount,
        correctCount = correctCount,
        level = level,
        levelPoints = levelPoints,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun ReviewLogEntity.toDomain(): ReviewLog = ReviewLog(
        id = id,
        cardKey = cardKey,
        formulaId = formulaId,
        ability = Ability.fromWire(ability),
        reviewedAt = reviewedAt,
        localDate = localDate,
        rating = ReviewRating.fromWire(rating),
        correct = correct,
        selectedOption = selectedOption,
        correctOption = correctOption,
        scheduledMinutes = scheduledMinutes,
        elapsedDays = elapsedDays,
        levelBefore = levelBefore,
        levelAfter = levelAfter,
        stabilityAfter = stabilityAfter,
        difficultyAfter = difficultyAfter,
        durationMs = durationMs,
        wasFirstReview = wasFirstReview,
        wasFormulaFirstReview = wasFormulaFirstReview,
    )

    companion object {
        private const val MAX_DUE_ITEMS = 20
        private const val MAX_TOTAL_ITEMS = 40
        private const val ABILITIES_PER_FORMULA = 5
        private const val MAX_REVIEW_DURATION_MS = 30 * 60 * 1000L

        fun cardKey(formulaId: String, ability: Ability): String =
            "$formulaId::${ability.wireName}"
    }
}
