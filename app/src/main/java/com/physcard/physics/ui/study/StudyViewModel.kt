package com.physcard.physics.ui.study

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.physcard.physics.PhysicsApp
import com.physcard.physics.domain.model.Ability
import com.physcard.physics.domain.model.ReviewRating
import com.physcard.physics.domain.session.SessionMode
import com.physcard.physics.domain.session.StudyItem
import com.physcard.physics.domain.session.StudySession
import com.physcard.physics.ui.components.formatInterval
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class StudyStage {
    LOADING,
    INTRO,
    QUESTION,
    FEEDBACK,
    COMPLETE,
    EMPTY,
    ERROR,
}

data class StudySummary(
    val answered: Int,
    val correct: Int,
    val durationMinutes: Long,
    val newFormulas: Int,
    val levelUps: List<String>,
    val ratings: Map<ReviewRating, Int>,
    val lastIntervalMinutes: Long?,
) {
    val accuracy: Float
        get() = if (answered == 0) 0f else correct.toFloat() / answered
}

data class StudyUiState(
    val stage: StudyStage = StudyStage.LOADING,
    val item: StudyItem? = null,
    val index: Int = 0,
    val total: Int = 0,
    val selectedIndex: Int? = null,
    val isCorrect: Boolean? = null,
    val previewIntervals: Map<ReviewRating, Long> = emptyMap(),
    val saving: Boolean = false,
    val errorMessage: String? = null,
    val summary: StudySummary? = null,
    val scheduleNotice: String? = null,
    val sessionMode: SessionMode = SessionMode.DAILY,
    val reviewedCount: Int = 0,
    val correctCount: Int = 0,
)

class StudyViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {
    private val container = (application as PhysicsApp).container
    private val formulaId: String? = savedStateHandle.get<String>("formulaId")?.takeIf { it.isNotBlank() }
    private val ability: Ability? = savedStateHandle.get<String>("ability")
        ?.takeIf { it.isNotBlank() }
        ?.let { raw -> runCatching { Ability.fromWire(raw) }.getOrNull() }

    private val _state = MutableStateFlow(StudyUiState())
    val state: StateFlow<StudyUiState> = _state.asStateFlow()

    private var session: StudySession? = null
    private var sessionStartedAt: Long = 0L
    private var questionStartedAt: Long = 0L
    private var pendingCorrect: Boolean = false
    private var pendingSelected: Int = -1
    private var reviewed = 0
    private var correct = 0
    private val levelUpMessages = mutableListOf<String>()
    private val ratingCounts = mutableMapOf<ReviewRating, Int>()
    private val newFormulaIds = mutableSetOf<String>()
    private var lastIntervalMinutes: Long? = null

    init {
        loadSession()
    }

    fun loadSession() {
        _state.value = StudyUiState(stage = StudyStage.LOADING)
        viewModelScope.launch {
            runCatching {
                val settings = container.settingsRepository.settings.first()
                val built = if (formulaId != null) {
                    container.studyRepository.buildFocusedSession(formulaId, ability)
                } else {
                    container.studyRepository.buildDailySession(settings.dailyNewFormulas)
                }
                built
            }.onSuccess { built ->
                session = built
                sessionStartedAt = System.currentTimeMillis()
                reviewed = 0
                correct = 0
                levelUpMessages.clear()
                ratingCounts.clear()
                newFormulaIds.clear()
                lastIntervalMinutes = null
                if (built.items.isEmpty()) {
                    _state.value = StudyUiState(
                        stage = StudyStage.EMPTY,
                        sessionMode = built.mode,
                    )
                } else {
                    presentItem(index = 0)
                }
            }.onFailure { error ->
                _state.value = StudyUiState(
                    stage = StudyStage.ERROR,
                    errorMessage = error.message ?: "无法开始本次学习。",
                )
            }
        }
    }

    fun beginQuestion() {
        val current = _state.value
        if (current.stage != StudyStage.INTRO) return
        questionStartedAt = System.currentTimeMillis()
        _state.value = current.copy(stage = StudyStage.QUESTION)
    }

    fun selectOption(index: Int) {
        val current = _state.value
        val item = current.item ?: return
        if (current.stage != StudyStage.QUESTION || current.saving) return
        if (index !in item.question.options.indices) return
        pendingSelected = index
        pendingCorrect = index == item.question.correctIndex
        _state.value = current.copy(
            stage = StudyStage.FEEDBACK,
            selectedIndex = index,
            isCorrect = pendingCorrect,
            previewIntervals = emptyMap(),
            errorMessage = null,
            scheduleNotice = null,
        )
        viewModelScope.launch {
            val preview = runCatching {
                container.studyRepository.previewIntervals(item)
            }.getOrDefault(emptyMap())
            val latest = _state.value
            if (latest.stage == StudyStage.FEEDBACK && latest.item?.question?.id == item.question.id) {
                _state.value = latest.copy(previewIntervals = preview)
            }
        }
    }

    fun rate(rating: ReviewRating) {
        val current = _state.value
        val item = current.item ?: return
        if (current.stage != StudyStage.FEEDBACK || current.saving) return
        val selected = pendingSelected
        val isCorrect = pendingCorrect
        _state.value = current.copy(saving = true, errorMessage = null)
        viewModelScope.launch {
            val duration = (System.currentTimeMillis() - questionStartedAt).coerceAtLeast(500L)
            runCatching {
                container.studyRepository.recordReview(
                    item = item,
                    rating = rating,
                    correct = isCorrect,
                    selectedOption = selected,
                    durationMs = duration,
                )
            }.onSuccess { outcome ->
                reviewed++
                if (isCorrect) correct++
                ratingCounts[rating] = (ratingCounts[rating] ?: 0) + 1
                if (item.isNewFormula) newFormulaIds += item.formula.id
                if (outcome.level.leveledUp) {
                    levelUpMessages += "${item.ability.title} 提升到 Lv.${outcome.level.levelAfter}"
                }
                val notice = if (!outcome.scheduled) {
                    "加练题，不计入复习计划"
                } else {
                    lastIntervalMinutes = outcome.fsrs.intervalMinutes
                    buildString {
                        append("下次复习：")
                        append(formatInterval(outcome.fsrs.intervalMinutes))
                        if (outcome.level.leveledUp) {
                            append(" · Lv.")
                            append(outcome.level.levelAfter)
                        }
                    }
                }
                val nextIndex = current.index + 1
                if (nextIndex >= (session?.items?.size ?: 0)) {
                    completeSession(notice)
                } else {
                    presentItem(index = nextIndex, notice = notice)
                }
            }.onFailure { error ->
                _state.value = current.copy(
                    saving = false,
                    errorMessage = error.message ?: "保存复习记录失败，请重试。",
                )
            }
        }
    }

    private fun presentItem(index: Int, notice: String? = null) {
        val currentSession = session ?: return
        val item = currentSession.items[index]
        questionStartedAt = System.currentTimeMillis()
        _state.value = StudyUiState(
            stage = if (item.showFormulaIntro) StudyStage.INTRO else StudyStage.QUESTION,
            item = item,
            index = index,
            total = currentSession.items.size,
            previewIntervals = emptyMap(),
            summary = null,
            scheduleNotice = notice,
            sessionMode = currentSession.mode,
            reviewedCount = reviewed,
            correctCount = correct,
        )
    }

    private fun completeSession(notice: String?) {
        val currentSession = session ?: return
        val summary = StudySummary(
            answered = reviewed,
            correct = correct,
            durationMinutes = ((System.currentTimeMillis() - sessionStartedAt) / 60_000).coerceAtLeast(0),
            newFormulas = newFormulaIds.size,
            levelUps = levelUpMessages.toList(),
            ratings = ratingCounts.toMap(),
            lastIntervalMinutes = lastIntervalMinutes,
        )
        _state.value = StudyUiState(
            stage = StudyStage.COMPLETE,
            item = currentSession.items.lastOrNull(),
            index = currentSession.items.size,
            total = currentSession.items.size,
            summary = summary,
            scheduleNotice = notice,
            sessionMode = currentSession.mode,
            reviewedCount = reviewed,
            correctCount = correct,
        )
    }

    fun retry() {
        loadSession()
    }

    val sessionMode: SessionMode
        get() = session?.mode ?: SessionMode.DAILY
}
