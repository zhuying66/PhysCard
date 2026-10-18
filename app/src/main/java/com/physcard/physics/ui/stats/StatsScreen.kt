package com.physcard.physics.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.Icon
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.physcard.physics.domain.level.MemoryLevelPolicy
import com.physcard.physics.domain.model.DailyStat
import com.physcard.physics.domain.model.ModuleProgress
import com.physcard.physics.domain.model.RecentReview
import com.physcard.physics.domain.model.StudyStats
import com.physcard.physics.ui.components.AppBackground
import com.physcard.physics.ui.components.AppCard
import com.physcard.physics.ui.components.AppCardShape
import com.physcard.physics.ui.components.AppPillShape
import com.physcard.physics.ui.components.EmptyState
import com.physcard.physics.ui.components.MiniProgress
import com.physcard.physics.ui.components.ProgressRing
import com.physcard.physics.ui.components.Spacing
import com.physcard.physics.ui.components.appListBottomPadding
import com.physcard.physics.ui.components.appSystemInsets
import com.physcard.physics.ui.components.levelColor
import com.physcard.physics.ui.components.moduleAccent
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun StatsRoute(viewModel: StatsViewModel, onStartStudy: () -> Unit) {
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    StatsScreen(stats = stats, onStartStudy = onStartStudy)
}

@Composable
fun StatsScreen(
    stats: StudyStats,
    onStartStudy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppBackground(modifier) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .appSystemInsets(bottom = false),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = Spacing.lg,
                end = Spacing.lg,
                top = Spacing.sm,
                bottom = appListBottomPadding(),
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "学习统计",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = "把每一次回忆，变成可见的进步。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    StreakPill(stats.streakDays)
                }
            }
            item { StreakCard(stats) }
            item { OverviewGrid(stats) }
            if (stats.totalReviews == 0) {
                item {
                    EmptyState(
                        title = "还没有学习记录",
                        message = "完成第一组公式后，这里会出现正确率、连续天数和记忆等级分布。",
                        actionLabel = "开始第一组",
                        onAction = onStartStudy,
                    )
                }
            } else {
                item { WeeklyChart(stats.lastSevenDays) }
                item { LevelDistribution(stats) }
                item { ModuleProgressCard(stats.moduleProgress) }
                item { RecentActivity(stats.recentReviews) }
            }
        }
    }
}

@Composable
private fun StreakPill(days: Int) {
    Row(
        modifier = Modifier
            .clip(AppPillShape)
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.LocalFireDepartment,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = "连续 $days 天",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}

@Composable
private fun StreakCard(stats: StudyStats) {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        background = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
        borderColor = Color.Transparent,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "连续学习",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f),
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = stats.streakDays.toString(),
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Light,
                    )
                    Text(
                        text = " 天",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                Text(
                    text = if (stats.todayReviews > 0) {
                        "今天已复习 ${stats.todayReviews} 次，正确率 ${(stats.todayAccuracy * 100).roundToInt()}%"
                    } else {
                        "今天还没有复习，保持节奏更重要。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f),
                )
            }
            ProgressRing(
                progress = if (stats.todayReviews == 0) 0f else stats.todayAccuracy,
                size = 78.dp,
                strokeWidth = 6.dp,
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.14f),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${(stats.todayAccuracy * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = "今日",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                    )
                }
            }
        }
    }
}

@Composable
private fun OverviewGrid(stats: StudyStats) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            OverviewCell(
                value = stats.totalReviews.toString(),
                label = "累计复习",
                modifier = Modifier.weight(1f),
            )
            OverviewCell(
                value = "${(stats.accuracy * 100).roundToInt()}%",
                label = "总正确率",
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            OverviewCell(
                value = if (stats.totalStudyMinutes < 1) "<1" else stats.totalStudyMinutes.toString(),
                label = "学习分钟",
                modifier = Modifier.weight(1f),
            )
            OverviewCell(
                value = "${stats.masteredFormulas}/${stats.totalFormulas}",
                label = "已掌握公式",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun OverviewCell(value: String, label: String, modifier: Modifier = Modifier) {
    AppCard(modifier = modifier, contentPadding = Spacing.md) {
        Column {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WeeklyChart(days: List<DailyStat>) {
    val max = remember(days) { days.maxOfOrNull { it.reviews }?.coerceAtLeast(1) ?: 1 }
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "最近 7 天",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "每天的复习次数",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = "共 ${days.sumOf { it.reviews }} 次",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(Spacing.lg))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(132.dp),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.Bottom,
            ) {
                days.forEachIndexed { index, day ->
                    val isToday = index == days.lastIndex
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        Text(
                            text = if (day.reviews == 0) "" else day.reviews.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(3.dp))
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.52f)
                                    .fillMaxHeight((day.reviews.toFloat() / max).coerceIn(0.035f, 1f))
                                    .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                    .background(
                                        if (isToday) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                        },
                                    ),
                            )
                        }
                        Spacer(Modifier.height(Spacing.xs))
                        Text(
                            text = day.label.removePrefix("周"),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isToday) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LevelDistribution(stats: StudyStats) {
    val total = stats.levelDistribution.sum().coerceAtLeast(1)
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Text(
                text = "记忆等级分布",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "按公式综合等级统计 · 等级由多次表现与稳定性共同决定",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.md))
            stats.levelDistribution.forEachIndexed { index, count ->
                val level = index + 1
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Lv.$level",
                        style = MaterialTheme.typography.labelSmall,
                        color = levelColor(level),
                        modifier = Modifier.width(34.dp),
                    )
                    Text(
                        text = MemoryLevelPolicy.LEVEL_TITLES[index],
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(38.dp),
                    )
                    Box(modifier = Modifier.weight(1f)) {
                        MiniProgress(
                            progress = count.toFloat() / total,
                            color = levelColor(level),
                            height = 6.dp,
                        )
                    }
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        text = "$count",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(24.dp),
                    )
                }
                if (index != stats.levelDistribution.lastIndex) Spacer(Modifier.height(7.dp))
            }
        }
    }
}

@Composable
private fun ModuleProgressCard(modules: List<ModuleProgress>) {
    if (modules.isEmpty()) return
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Text(
                text = "模块掌握度",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(Spacing.md))
            modules.forEachIndexed { index, progress ->
                val accent = moduleAccent(progress.module)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(accent),
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        text = progress.module.name,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.width(58.dp),
                    )
                    Box(modifier = Modifier.weight(1f)) {
                        MiniProgress(
                            progress = if (progress.total == 0) 0f else progress.started.toFloat() / progress.total,
                            color = accent,
                            height = 5.dp,
                        )
                    }
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        text = "${progress.started}/${progress.total}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (index != modules.lastIndex) Spacer(Modifier.height(9.dp))
            }
        }
    }
}

@Composable
private fun RecentActivity(reviews: List<RecentReview>) {
    if (reviews.isEmpty()) return
    Column {
        Text(
            text = "最近复习",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(Spacing.sm))
        AppCard(modifier = Modifier.fillMaxWidth(), contentPadding = 0.dp) {
            Column {
                reviews.forEachIndexed { index, review ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = if (review.correct) {
                                Icons.Rounded.CheckCircle
                            } else {
                                Icons.Rounded.Cancel
                            },
                            contentDescription = null,
                            tint = if (review.correct) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(Spacing.sm))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = review.formulaTitle,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "${review.ability.title} · ${review.rating.label} · ${recentTime(review.reviewedAt)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                    if (index != reviews.lastIndex) {
                        Box(
                            Modifier
                                .padding(start = 50.dp, end = Spacing.lg)
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                        )
                    }
                }
            }
        }
    }
}

private fun recentTime(epochMillis: Long): String {
    val zone = ZoneId.systemDefault()
    val date = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
    val time = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalTime()
    val today = LocalDate.now(zone)
    val formattedTime = time.format(DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA))
    return when {
        date == today -> "今天 $formattedTime"
        date == today.minusDays(1) -> "昨天 $formattedTime"
        else -> date.format(DateTimeFormatter.ofPattern("M月d日", Locale.CHINA))
    }
}
