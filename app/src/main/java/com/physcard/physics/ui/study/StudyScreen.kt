package com.physcard.physics.ui.study

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.physcard.physics.domain.model.Ability
import com.physcard.physics.domain.model.Formula
import com.physcard.physics.domain.model.QuestionOption
import com.physcard.physics.domain.model.ReviewRating
import com.physcard.physics.domain.session.StudyItem
import com.physcard.physics.ui.components.AppBackground
import com.physcard.physics.ui.components.AppCard
import com.physcard.physics.ui.components.AppCardShape
import com.physcard.physics.ui.components.AppContentMaxWidth
import com.physcard.physics.ui.components.AppPillShape
import com.physcard.physics.ui.components.EmptyState
import com.physcard.physics.ui.components.Formula
import com.physcard.physics.ui.components.LevelBadge
import com.physcard.physics.ui.components.LoadingState
import com.physcard.physics.ui.components.OrbitMark
import com.physcard.physics.ui.components.Spacing
import com.physcard.physics.ui.components.ThinDivider
import com.physcard.physics.ui.components.formatInterval
import com.physcard.physics.ui.components.levelColor
import com.physcard.physics.ui.components.moduleAccent
import kotlin.math.roundToInt

@Composable
fun StudyRoute(
    viewModel: StudyViewModel,
    onExit: () -> Unit,
    onFinish: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    StudyScreen(
        state = state,
        onBeginQuestion = viewModel::beginQuestion,
        onSelectOption = viewModel::selectOption,
        onRate = viewModel::rate,
        onRetry = viewModel::retry,
        onExit = onExit,
        onFinish = onFinish,
    )
}

@Composable
fun StudyScreen(
    state: StudyUiState,
    onBeginQuestion: () -> Unit,
    onSelectOption: (Int) -> Unit,
    onRate: (ReviewRating) -> Unit,
    onRetry: () -> Unit,
    onExit: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showExitDialog by remember { mutableStateOf(false) }
    var showFormulaSheet by remember { mutableStateOf(false) }
    val inProgress = state.stage in setOf(
        StudyStage.INTRO,
        StudyStage.QUESTION,
        StudyStage.FEEDBACK,
    )
    BackHandler(enabled = state.stage != StudyStage.LOADING) {
        when (state.stage) {
            StudyStage.COMPLETE, StudyStage.EMPTY, StudyStage.ERROR -> onFinish()
            else -> showExitDialog = true
        }
    }
    AppBackground(modifier) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentAlignment = Alignment.TopCenter,
        ) {
            val contentWidth = maxWidth.coerceAtMost(AppContentMaxWidth)
            Column(
                modifier = Modifier
                    .width(contentWidth)
                    .fillMaxHeight(),
            ) {
                Crossfade(targetState = state.stage, label = "study-stage") { stage ->
                    when (stage) {
                        StudyStage.LOADING -> LoadingState(label = "正在为你安排这一组复习…")
                        StudyStage.EMPTY -> StudyEmptyState(onExit = onFinish)
                        StudyStage.ERROR -> StudyErrorState(
                            message = state.errorMessage ?: "加载失败",
                            onRetry = onRetry,
                            onExit = onExit,
                        )
                        StudyStage.INTRO -> state.item?.let { item ->
                            IntroPane(
                                item = item,
                                index = state.index,
                                total = state.total,
                                onClose = { showExitDialog = true },
                                onBegin = onBeginQuestion,
                            )
                        }
                        StudyStage.QUESTION -> state.item?.let { item ->
                            QuestionPane(
                                state = state,
                                item = item,
                                onClose = { showExitDialog = true },
                                onSelectOption = onSelectOption,
                            )
                        }
                        StudyStage.FEEDBACK -> state.item?.let { item ->
                            QuestionPane(
                                state = state,
                                item = item,
                                onClose = { showExitDialog = true },
                                onSelectOption = onSelectOption,
                                onRate = onRate,
                                onOpenFormula = { showFormulaSheet = true },
                            )
                        }
                        StudyStage.COMPLETE -> state.summary?.let { summary ->
                            SummaryPane(
                                state = state,
                                summary = summary,
                                onFinish = onFinish,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showExitDialog && inProgress) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("先离开这一组？") },
            text = {
                Text("已经评价的题目会保存；当前这道题如果还没有评价，下次会重新出现。")
            },
            confirmButton = {
                TextButton(onClick = {
                    showExitDialog = false
                    onExit()
                }) { Text("离开") }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) { Text("继续学习") }
            },
        )
    }

    val item = state.item
    if (showFormulaSheet && item != null) {
        ModalBottomSheet(onDismissRequest = { showFormulaSheet = false }) {
            FormulaReferenceSheet(formula = item.formula, ability = item.ability)
        }
    }
}

@Composable
private fun StudyTopBar(
    label: String,
    index: Int,
    total: Int,
    onClose: () -> Unit,
    level: Int? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.Rounded.Close, contentDescription = "退出学习")
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "第 ${index + 1} / $total 题",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (level != null && level > 0) {
            LevelBadge(level = level)
        }
    }
    LinearProgressIndicator(
        progress = { if (total == 0) 0f else (index + 1f) / total },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg)
            .height(3.dp)
            .clip(AppPillShape),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
        drawStopIndicator = {},
    )
}

@Composable
private fun IntroPane(
    item: StudyItem,
    index: Int,
    total: Int,
    onClose: () -> Unit,
    onBegin: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        StudyTopBar(
            label = if (item.isNewFormula) "新公式" else "继续学习",
            index = index,
            total = total,
            onClose = onClose,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg)
                .padding(top = Spacing.lg, bottom = Spacing.xl),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .clip(AppPillShape)
                        .background(moduleAccent(null).copy(alpha = 0.1f))
                        .padding(horizontal = 9.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = item.formula.chapter,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = item.ability.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(Spacing.md))
            Text(
                text = item.formula.title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(Spacing.lg))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(AppCardShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
                    .padding(horizontal = Spacing.lg, vertical = Spacing.xl),
            ) {
                Formula(
                    latex = item.formula.latex,
                    fontSize = 28.sp,
                    autoFit = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(Spacing.lg))
            Text(
                text = "适用条件",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = item.formula.conditions,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(Spacing.lg))
            Text(
                text = "物理量",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.xs))
            AppCard(modifier = Modifier.fillMaxWidth(), contentPadding = 0.dp) {
                Column {
                    item.formula.variables.forEachIndexed { variableIndex, variable ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.width(82.dp)) {
                                Formula(latex = variable.symbol, fontSize = 16.sp)
                            }
                            Text(
                                text = variable.name,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = variable.unit,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (variableIndex != item.formula.variables.lastIndex) {
                            ThinDivider(Modifier.padding(horizontal = Spacing.lg))
                        }
                    }
                }
            }
            if (item.formula.confusions.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.md))
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        Icons.Rounded.Lightbulb,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        text = "易错提醒：${item.formula.confusions.first().why}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        ) {
            Button(
                onClick = onBegin,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text("开始作答")
                Spacer(Modifier.width(Spacing.sm))
                Icon(Icons.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun QuestionPane(
    state: StudyUiState,
    item: StudyItem,
    onClose: () -> Unit,
    onSelectOption: (Int) -> Unit,
    onRate: ((ReviewRating) -> Unit)? = null,
    onOpenFormula: (() -> Unit)? = null,
) {
    val listState = rememberLazyListState()
    val feedback = state.stage == StudyStage.FEEDBACK
    LaunchedEffect(state.stage) {
        if (feedback) {
            val feedbackIndex = if (item.question.stimulusLatex != null) 8 else 7
            runCatching { listState.animateScrollToItem(feedbackIndex) }
        }
    }
    Column(Modifier.fillMaxSize()) {
        StudyTopBar(
            label = item.ability.title,
            index = state.index,
            total = state.total,
            onClose = onClose,
            level = item.state.level,
        )
        AnimatedVisibility(visible = state.scheduleNotice != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg, vertical = Spacing.xs)
                    .clip(AppPillShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f))
                    .padding(horizontal = Spacing.md, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = state.scheduleNotice.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = Spacing.lg,
                end = Spacing.lg,
                top = Spacing.md,
                bottom = Spacing.xxl,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = Ability.fromWire(item.ability.wireName).description,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (item.isNewFormula) {
                        Text(
                            text = "新公式",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            item {
                Text(
                    text = item.question.prompt,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
            if (item.question.stimulusLatex != null) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(AppCardShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant,
                                AppCardShape,
                            )
                            .padding(horizontal = Spacing.lg, vertical = Spacing.xl),
                    ) {
                        Formula(
                            latex = item.question.stimulusLatex,
                            fontSize = 24.sp,
                            autoFit = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            item {
                Text(
                    text = if (feedback) "已作答" else "选择一个答案",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
            items(item.question.options.size) { optionIndex ->
                val option = item.question.options[optionIndex]
                QuestionOptionCard(
                    index = optionIndex,
                    option = option,
                    correctIndex = item.question.correctIndex,
                    selectedIndex = state.selectedIndex,
                    revealed = feedback,
                    enabled = !feedback && !state.saving,
                    onClick = { onSelectOption(optionIndex) },
                )
            }
            if (feedback && state.isCorrect != null) {
                item {
                    Spacer(Modifier.height(Spacing.xs))
                    FeedbackPanel(
                        item = item,
                        correct = state.isCorrect,
                        explanation = item.question.explanation,
                        onOpenFormula = onOpenFormula,
                    )
                }
                item {
                    if (onRate != null) {
                        if (item.schedulesReview) {
                            RatingPanel(
                                state = state,
                                wrong = state.isCorrect == false,
                                onRate = onRate,
                            )
                        } else {
                            DrillContinuePanel(
                                saving = state.saving,
                                errorMessage = state.errorMessage,
                                onContinue = { onRate(ReviewRating.GOOD) },
                            )
                        }
                    }
                }
            }
            if (feedback) {
                item { Spacer(Modifier.height(Spacing.md)) }
            }
        }
    }
}

@Composable
private fun QuestionOptionCard(
    index: Int,
    option: QuestionOption,
    correctIndex: Int,
    selectedIndex: Int?,
    revealed: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val isCorrectOption = index == correctIndex
    val isSelected = selectedIndex == index
    val showCorrect = revealed && isCorrectOption
    val showWrong = revealed && isSelected && !isCorrectOption
    val background = when {
        showCorrect -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f)
        showWrong -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.75f)
        isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        else -> MaterialTheme.colorScheme.surface
    }
    val borderColor = when {
        showCorrect -> MaterialTheme.colorScheme.primary
        showWrong -> MaterialTheme.colorScheme.error
        isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    val contentAlpha = if (revealed && !isCorrectOption && !isSelected) 0.55f else 1f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .testTag("option-$index")
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(
                    when {
                        showCorrect -> MaterialTheme.colorScheme.primary
                        showWrong -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = ('A' + index).toString(),
                style = MaterialTheme.typography.labelMedium,
                color = when {
                    showCorrect -> MaterialTheme.colorScheme.onPrimary
                    showWrong -> MaterialTheme.colorScheme.onError
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        Spacer(Modifier.width(Spacing.md))
        // Text options wrap onto multiple lines. Formula options cannot break across lines,
        // so they scale down to fit instead — either way the whole option is visible without
        // a horizontal swipe, and every card keeps the same width.
        if (option.latex != null) {
            Formula(
                latex = option.latex,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
                autoFit = true,
                modifier = Modifier.weight(1f),
            )
        } else {
            Text(
                text = option.text.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
                modifier = Modifier.weight(1f),
            )
        }
        if (showCorrect) {
            Spacer(Modifier.width(Spacing.sm))
            Icon(
                Icons.Rounded.CheckCircle,
                contentDescription = "正确答案",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        } else if (showWrong) {
            Spacer(Modifier.width(Spacing.sm))
            Icon(
                Icons.Rounded.Cancel,
                contentDescription = "选择错误",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun FeedbackPanel(
    item: StudyItem,
    correct: Boolean,
    explanation: String,
    onOpenFormula: (() -> Unit)?,
) {
    val accent = if (correct) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        background = if (correct) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        } else {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        },
        borderColor = accent.copy(alpha = 0.24f),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (correct) Icons.Rounded.CheckCircle else Icons.Rounded.Cancel,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(Spacing.sm))
                Text(
                    text = if (correct) "回答正确" else "这次没有选对",
                    style = MaterialTheme.typography.titleMedium,
                    color = accent,
                )
            }
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = if (correct) {
                    "正确。把注意力放回公式本身，再根据刚才的回忆难度评价。"
                } else {
                    "先重新理解标准公式和适用条件，再用“忘记 / 困难”等评价记录真实感受。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.md))
            ThinDivider()
            Spacer(Modifier.height(Spacing.md))
            Text(
                text = "标准公式",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.xs))
            Formula(
                latex = item.formula.latex,
                fontSize = 23.sp,
                autoFit = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = item.formula.conditions,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.md))
            Text(
                text = explanation,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!correct && item.formula.confusions.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    text = "常见错误：${item.formula.confusions.first().why}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                )
            }
            if (onOpenFormula != null) {
                Spacer(Modifier.height(Spacing.sm))
                TextButton(onClick = onOpenFormula, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                    Icon(Icons.Rounded.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("查看完整公式卡")
                }
            }
        }
    }
}

@Composable
private fun DrillContinuePanel(
    saving: Boolean,
    errorMessage: String?,
    onContinue: () -> Unit,
) {
    Column(modifier = Modifier.padding(top = Spacing.md)) {
        Text(
            text = "加练题",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "这一题只用来多练一遍，不影响复习间隔和记忆等级。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.md))
        Button(
            onClick = onContinue,
            enabled = !saving,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text(if (saving) "正在保存…" else "继续下一题")
        }
        if (errorMessage != null) {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = Spacing.sm),
            )
        }
    }
}

@Composable
private fun RatingPanel(
    state: StudyUiState,
    wrong: Boolean,
    onRate: (ReviewRating) -> Unit,
) {
    Column(modifier = Modifier.padding(top = Spacing.md)) {
        Text(
            text = "这次回忆有多轻松？",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "答题对错用于诊断；复习间隔由你的记忆评价决定。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.md))
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                RatingButton(
                    rating = ReviewRating.AGAIN,
                    interval = state.previewIntervals[ReviewRating.AGAIN],
                    recommended = wrong,
                    saving = state.saving,
                    modifier = Modifier.weight(1f),
                    onClick = { onRate(ReviewRating.AGAIN) },
                )
                RatingButton(
                    rating = ReviewRating.HARD,
                    interval = state.previewIntervals[ReviewRating.HARD],
                    saving = state.saving,
                    modifier = Modifier.weight(1f),
                    onClick = { onRate(ReviewRating.HARD) },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                RatingButton(
                    rating = ReviewRating.GOOD,
                    interval = state.previewIntervals[ReviewRating.GOOD],
                    saving = state.saving,
                    modifier = Modifier.weight(1f),
                    onClick = { onRate(ReviewRating.GOOD) },
                )
                RatingButton(
                    rating = ReviewRating.EASY,
                    interval = state.previewIntervals[ReviewRating.EASY],
                    saving = state.saving,
                    modifier = Modifier.weight(1f),
                    onClick = { onRate(ReviewRating.EASY) },
                )
            }
        }
        if (state.saving) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.sm),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(15.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(Spacing.sm))
                Text(
                    text = "正在保存复习进度…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (state.errorMessage != null) {
            Text(
                text = state.errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = Spacing.sm),
            )
        }
    }
}

@Composable
private fun RatingButton(
    rating: ReviewRating,
    interval: Long?,
    modifier: Modifier = Modifier,
    recommended: Boolean = false,
    saving: Boolean = false,
    onClick: () -> Unit,
) {
    val accent = when (rating) {
        ReviewRating.AGAIN -> MaterialTheme.colorScheme.error
        ReviewRating.HARD -> MaterialTheme.colorScheme.tertiary
        ReviewRating.GOOD -> MaterialTheme.colorScheme.primary
        ReviewRating.EASY -> Color(0xFF0E8C8C)
    }
    val background = if (rating == ReviewRating.EASY && !isDarkUi()) {
        Color(0xFFDDF2F1)
    } else {
        accent.copy(alpha = 0.09f)
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .border(
                width = if (recommended) 1.6.dp else 1.dp,
                color = if (recommended) accent else accent.copy(alpha = 0.24f),
                shape = RoundedCornerShape(14.dp),
            )
            .testTag("rating-${rating.wireName}")
            .clickable(enabled = !saving, onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = rating.label,
                style = MaterialTheme.typography.titleSmall,
                color = accent,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = interval?.let(::formatInterval) ?: "…",
                style = MaterialTheme.typography.labelSmall,
                color = accent.copy(alpha = 0.82f),
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = rating.description,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun SummaryPane(
    state: StudyUiState,
    summary: StudySummary,
    onFinish: () -> Unit,
) {
    var showFormulaSheet by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg)
            .padding(top = Spacing.xxl, bottom = Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        OrbitMark(size = 68.dp)
        Spacer(Modifier.height(Spacing.lg))
        Text(
            text = "这一组完成了。",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = if (summary.accuracy >= 0.8f) "记忆路径正在变稳，保持这个节奏。" else "错题已经进入更短的复习间隔。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.xl))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            SummaryStat("${summary.answered}", "完成题目")
            SummaryStat("${(summary.accuracy * 100).roundToInt()}%", "正确率")
            SummaryStat(if (summary.durationMinutes == 0L) "<1" else "${summary.durationMinutes}", "分钟")
        }
        Spacer(Modifier.height(Spacing.xl))
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        text = "下一张卡片",
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = state.scheduleNotice ?: summary.lastIntervalMinutes?.let {
                        "预计 ${formatInterval(it)} 后复习"
                    } ?: "复习计划已更新",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (summary.newFormulas > 0) {
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        text = "本组接触了 ${summary.newFormulas} 个新公式，已分别建立五种能力卡。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (summary.levelUps.isNotEmpty()) {
                    Spacer(Modifier.height(Spacing.sm))
                    ThinDivider()
                    Spacer(Modifier.height(Spacing.sm))
                    summary.levelUps.forEach { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(Spacing.lg))
        RatingDistribution(summary.ratings)
        Spacer(Modifier.height(Spacing.xl))
        Button(
            onClick = onFinish,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Icon(Icons.Rounded.Home, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.sm))
            Text("返回首页")
        }
        Spacer(Modifier.height(Spacing.sm))
        OutlinedButton(
            onClick = { showFormulaSheet = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
        ) {
            Icon(Icons.Rounded.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.sm))
            Text("查看公式卡")
        }
    }

    val item = state.item
    if (showFormulaSheet && item != null) {
        ModalBottomSheet(onDismissRequest = { showFormulaSheet = false }) {
            FormulaReferenceSheet(formula = item.formula, ability = item.ability)
        }
    }
}

@Composable
private fun SummaryStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RatingDistribution(ratings: Map<ReviewRating, Int>) {
    val total = ratings.values.sum().coerceAtLeast(1)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppCardShape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(Spacing.lg),
    ) {
        Text(
            text = "记忆评价分布",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.md))
        ReviewRating.entries.forEach { rating ->
            val count = ratings[rating] ?: 0
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = rating.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(34.dp),
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(7.dp)
                        .clip(AppPillShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(count.toFloat() / total)
                            .height(7.dp)
                            .clip(AppPillShape)
                            .background(
                                when (rating) {
                                    ReviewRating.AGAIN -> MaterialTheme.colorScheme.error
                                    ReviewRating.HARD -> MaterialTheme.colorScheme.tertiary
                                    ReviewRating.GOOD -> MaterialTheme.colorScheme.primary
                                    ReviewRating.EASY -> Color(0xFF0E8C8C)
                                },
                            ),
                    )
                }
                Spacer(Modifier.width(Spacing.sm))
                Text(
                    text = "$count",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (rating != ReviewRating.EASY) Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun FormulaReferenceSheet(formula: Formula, ability: Ability) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xl)
            .padding(bottom = Spacing.xxl)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = formula.title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "${formula.chapter} · ${ability.title}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.md))
        Formula(
            latex = formula.latex,
            fontSize = 26.sp,
            autoFit = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Spacing.lg))
        Text(
            text = formula.conditions,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.lg))
        formula.variables.forEach { variable ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(78.dp)) {
                    Formula(latex = variable.symbol, fontSize = 15.sp)
                }
                Text(variable.name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Text(
                    variable.unit,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (formula.confusions.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.md))
            ThinDivider()
            Spacer(Modifier.height(Spacing.md))
            formula.confusions.forEach { confusion ->
                Text(
                    text = "易错：${confusion.why}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(Spacing.xs))
            }
        }
    }
}

@Composable
private fun StudyEmptyState(onExit: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        OrbitMark(size = 64.dp)
        Spacer(Modifier.height(Spacing.lg))
        Text(
            text = "今天没有到期任务",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = "FSRS 已经把下一批复习安排在更合适的时间。可以去公式库看看，或稍后回来。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.xl))
        Button(onClick = onExit, shape = RoundedCornerShape(14.dp)) {
            Text("返回首页")
        }
    }
}

@Composable
private fun StudyErrorState(message: String, onRetry: () -> Unit, onExit: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Rounded.Cancel,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.height(Spacing.md))
        Text(
            text = "这次没有加载成功",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.xl))
        Button(onClick = onRetry, shape = RoundedCornerShape(14.dp)) { Text("重试") }
        Spacer(Modifier.height(Spacing.sm))
        TextButton(onClick = onExit) { Text("返回") }
    }
}

@Composable
private fun isDarkUi(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.4f
