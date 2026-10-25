package com.physcard.physics.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.physcard.physics.domain.model.Ability
import com.physcard.physics.domain.model.Formula
import com.physcard.physics.ui.components.AppBackground
import com.physcard.physics.ui.components.AppCard
import com.physcard.physics.ui.components.AppCardShape
import com.physcard.physics.ui.components.AppContentMaxWidth
import com.physcard.physics.ui.components.AppPillShape
import com.physcard.physics.ui.components.EmptyState
import com.physcard.physics.ui.components.Formula
import com.physcard.physics.ui.components.LevelBadge
import com.physcard.physics.ui.components.LoadingState
import com.physcard.physics.ui.components.SectionHeader
import com.physcard.physics.ui.components.Spacing
import com.physcard.physics.ui.components.ThinDivider
import com.physcard.physics.ui.components.appSystemInsets
import com.physcard.physics.ui.components.formatDue
import com.physcard.physics.ui.components.levelColor
import com.physcard.physics.ui.components.moduleAccent as accentFor

@Composable
fun FormulaDetailRoute(
    viewModel: FormulaDetailViewModel,
    onBack: () -> Unit,
    onPractice: (String, Ability?) -> Unit,
    onOpenFormula: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    FormulaDetailScreen(
        state = state,
        onBack = onBack,
        onPractice = onPractice,
        onOpenFormula = onOpenFormula,
    )
}

@Composable
fun FormulaDetailScreen(
    state: FormulaDetailUiState,
    onBack: () -> Unit,
    onPractice: (String, Ability?) -> Unit,
    onOpenFormula: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppBackground(modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .appSystemInsets(bottom = false),
        ) {
            DetailTopBar(onBack = onBack, title = state.formula?.title ?: "公式详情")
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = AppContentMaxWidth)
                    .align(Alignment.CenterHorizontally),
            ) {
                when {
                    state.loading -> LoadingState(label = "正在读取公式…")
                    state.error != null -> EmptyState(
                        title = "无法打开公式",
                        message = state.error ?: "未知错误",
                        actionLabel = "返回",
                        onAction = onBack,
                    )
                    state.formula != null -> {
                        val formula = state.formula
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .navigationBarsPadding(),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                start = Spacing.lg,
                                end = Spacing.lg,
                                top = Spacing.xs,
                                bottom = Spacing.xxl,
                            ),
                            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                        ) {
                            item {
                                FormulaHero(
                                    formula = formula,
                                    moduleName = state.module?.name ?: formula.moduleId,
                                    moduleAccent = accentFor(state.module),
                                    averageLevel = state.averageLevel,
                                    introducedAbilities = state.introducedAbilities,
                                    onPractice = {
                                        // Top button: practise the whole test set for this formula.
                                        onPractice(formula.id, null)
                                    },
                                )
                            }
                            item {
                                Text(
                                    text = "适用条件",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onBackground,
                                )
                                Spacer(Modifier.height(Spacing.sm))
                                AppCard(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = formula.conditions,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            item {
                                Text(
                                    text = "物理量",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onBackground,
                                )
                                Spacer(Modifier.height(Spacing.sm))
                                VariableTable(formula)
                            }
                            if (formula.confusions.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "常见错误",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onBackground,
                                    )
                                    Spacer(Modifier.height(Spacing.sm))
                                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                        formula.confusions.forEach { confusion ->
                                            ConfusionCard(
                                                wrong = confusion.wrong,
                                                why = confusion.why,
                                            )
                                        }
                                    }
                                }
                            }
                            item {
                                SectionHeader(
                                    title = "五种考查能力",
                                    subtitle = "每种能力独立安排复习，互不掩盖",
                                )
                            }
                            items(state.abilities, key = { it.ability.wireName }) { status ->
                                AbilityRow(
                                    status = status,
                                    onPractice = { onPractice(formula.id, status.ability) },
                                )
                            }
                            if (state.related.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "相关公式",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onBackground,
                                    )
                                    Spacer(Modifier.height(Spacing.sm))
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                        items(state.related, key = { it.id }) { related ->
                                            RelatedFormulaChip(
                                                formula = related,
                                                onClick = { onOpenFormula(related.id) },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailTopBar(onBack: () -> Unit, title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Rounded.ArrowBack, contentDescription = "返回")
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun FormulaHero(
    formula: Formula,
    moduleName: String,
    moduleAccent: Color,
    averageLevel: Int,
    introducedAbilities: Int,
    onPractice: () -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier
                    .clip(AppPillShape)
                    .background(moduleAccent.copy(alpha = 0.12f))
                    .padding(horizontal = 9.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(moduleAccent),
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = moduleName,
                    style = MaterialTheme.typography.labelSmall,
                    color = moduleAccent,
                )
            }
            Spacer(Modifier.width(Spacing.sm))
            Text(
                text = formula.chapter,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            if (averageLevel > 0) {
                LevelBadge(level = averageLevel, showTitle = true)
            }
        }
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = formula.title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(Spacing.md))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(AppCardShape)
                .background(
                    androidx.compose.ui.graphics.Brush.linearGradient(
                        listOf(
                            moduleAccent.copy(alpha = 0.13f),
                            MaterialTheme.colorScheme.surface,
                        ),
                    ),
                )
                .padding(horizontal = Spacing.lg, vertical = Spacing.xl),
        ) {
            Formula(
                latex = formula.latex,
                fontSize = 28.sp,
                autoFit = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(Spacing.md))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (introducedAbilities == 0) "尚未开始学习" else "已学 $introducedAbilities/5 种能力",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            FilledTonalButton(
                onClick = onPractice,
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("专项练习")
            }
        }
    }
}

@Composable
private fun VariableTable(formula: Formula) {
    AppCard(modifier = Modifier.fillMaxWidth(), contentPadding = 0.dp) {
        Column {
            formula.variables.forEachIndexed { index, variable ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier.width(84.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Formula(latex = variable.symbol, fontSize = 16.sp)
                    }
                    Text(
                        text = variable.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = variable.unit,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (index != formula.variables.lastIndex) {
                    ThinDivider(Modifier.padding(horizontal = Spacing.lg))
                }
            }
        }
    }
}

@Composable
private fun ConfusionCard(wrong: String, why: String) {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        background = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
        borderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "常见误写",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(Spacing.xs))
            Formula(
                latex = wrong,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.error,
                autoFit = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = why,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AbilityRow(status: AbilityStatus, onPractice: () -> Unit) {
    val state = status.state
    val level = state?.level ?: 0
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            // The whole card is the tap target, not just the chevron.
            .clickable(onClick = onPractice),
        contentPadding = Spacing.md,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (level > 0) {
                            levelColor(level).copy(alpha = 0.12f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = status.ability.shortTitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (level > 0) levelColor(level) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = status.ability.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (level > 0) {
                        Spacer(Modifier.width(6.dp))
                        LevelBadge(level = level)
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = when {
                        state == null || state.reviewCount == 0 -> status.ability.description
                        else -> "复习 ${state.reviewCount} 次 · ${formatDue(state.dueAt)}"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = "练习${status.ability.title}的全部题目",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun RelatedFormulaChip(formula: Formula, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(AppPillShape)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = formula.title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
