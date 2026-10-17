package com.physcard.physics.ui.home

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.physcard.physics.domain.model.HomeSnapshot
import com.physcard.physics.domain.model.ModuleProgress
import com.physcard.physics.ui.components.AppBackground
import com.physcard.physics.ui.components.AppCard
import com.physcard.physics.ui.components.AppCardShape
import com.physcard.physics.ui.components.AppPillShape
import com.physcard.physics.ui.components.MiniProgress
import com.physcard.physics.ui.components.OrbitMark
import com.physcard.physics.ui.components.ProgressRing
import com.physcard.physics.ui.components.SectionHeader
import com.physcard.physics.ui.components.Spacing
import com.physcard.physics.ui.components.StatBlock
import com.physcard.physics.ui.components.formatShortDate
import com.physcard.physics.ui.components.moduleAccent
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
    snapshot: HomeSnapshot,
    onStartStudy: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenModule: (String) -> Unit,
    onOpenStats: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val today = LocalDate.now()
    val greeting = when (LocalTime.now().hour) {
        in 5..10 -> "早上好"
        in 11..13 -> "中午好"
        in 14..17 -> "下午好"
        else -> "晚上好"
    }
    AppBackground(modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = Spacing.lg)
                .padding(top = Spacing.sm, bottom = 108.dp),
        ) {
            HomeHeader(
                streak = snapshot.streakDays,
                onOpenSettings = onOpenSettings,
            )
            Spacer(Modifier.height(Spacing.xl))
            Text(
                text = "${today.format(DATE_HEADER)}  ·  $greeting",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = "把公式，记成\n长期记忆。",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Normal,
            )
            Spacer(Modifier.height(Spacing.lg))
            TodayCard(
                snapshot = snapshot,
                onStartStudy = onStartStudy,
            )
            Spacer(Modifier.height(Spacing.lg))
            TodayStats(snapshot)
            Spacer(Modifier.height(Spacing.xxl))
            SectionHeader(
                title = "公式库",
                subtitle = "按高中物理知识模块整理",
                action = "全部",
                onAction = onOpenLibrary,
            )
            Spacer(Modifier.height(Spacing.md))
            ModuleGrid(
                modules = snapshot.modules,
                onOpenModule = onOpenModule,
            )
            Spacer(Modifier.height(Spacing.xxl))
            ProgressNote(snapshot, onOpenStats)
        }
    }
}

@Composable
private fun HomeHeader(streak: Int, onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OrbitMark(size = 34.dp)
        Spacer(Modifier.width(Spacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "物理公式记忆",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "PHYSICS · DAILY",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier
                .clip(AppPillShape)
                .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.LocalFireDepartment,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "$streak 天",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = onOpenSettings) {
            Icon(
                imageVector = Icons.Rounded.Settings,
                contentDescription = "设置",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TodayCard(snapshot: HomeSnapshot, onStartStudy: () -> Unit) {
    val dueAnimated by animateIntAsState(targetValue = snapshot.dueCount, label = "due")
    val remaining = snapshot.remainingToday
    val completed = snapshot.todayReviewed
    val totalForToday = completed + remaining
    val progress = if (totalForToday == 0) 0f else completed.toFloat() / totalForToday
    val hasWork = snapshot.hasStudyWork
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppCardShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.86f),
                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.84f),
                    ),
                ),
            )
            .padding(Spacing.xl),
    ) {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (snapshot.dueCount > 0) "今日待复习" else "今日复习",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f),
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = dueAnimated.toString(),
                            style = MaterialTheme.typography.displayMedium,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Light,
                        )
                        Text(
                            text = " 个能力点",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        text = if (snapshot.newFormulaCount > 0) {
                            "另有 ${snapshot.newFormulaCount} 个新公式 · 今日新学 ${snapshot.todayNewFormulas}/${snapshot.dailyNewLimit}"
                        } else {
                            "今天已复习 $completed 次 · 正确率 ${
                                (snapshot.todayAccuracy * 100).roundToInt()
                            }%"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.74f),
                    )
                }
                ProgressRing(
                    progress = progress,
                    size = 68.dp,
                    strokeWidth = 5.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                    trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f),
                ) {
                    Text(
                        text = "${(progress * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Spacer(Modifier.height(Spacing.lg))
            Button(
                onClick = onStartStudy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(15.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onPrimary,
                    contentColor = MaterialTheme.colorScheme.primary,
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
            ) {
                Text(
                    text = if (hasWork) "开始学习" else "自由练习",
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.width(Spacing.sm))
                Icon(
                    imageVector = Icons.Rounded.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = when {
                    remaining > 0 -> "预计 ${maxOf(1, remaining / 3)} 分钟 · 已为你排好顺序"
                    snapshot.newFormulaCount > 0 -> "可以开始解锁新的公式"
                    else -> "当前没有到期任务，去公式库逛逛"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.66f),
            )
        }
    }
}

@Composable
private fun TodayStats(snapshot: HomeSnapshot) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        StatBlock(
            value = snapshot.todayReviewed.toString(),
            label = "今日复习",
            modifier = Modifier.weight(1f),
        )
        StatBlock(
            value = snapshot.todayNewFormulas.toString(),
            label = "今日新学",
            unit = "个",
            modifier = Modifier.weight(1f),
        )
        StatBlock(
            value = snapshot.masteredFormulas.toString(),
            label = "已掌握",
            unit = "个",
            modifier = Modifier.weight(1f),
            accent = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun ModuleGrid(
    modules: List<ModuleProgress>,
    onOpenModule: (String) -> Unit,
) {
    if (modules.isEmpty()) {
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "正在整理公式模块…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        modules.chunked(2).forEach { rowModules ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                rowModules.forEach { progress ->
                    ModuleCard(
                        progress = progress,
                        modifier = Modifier.weight(1f),
                        onClick = { onOpenModule(progress.module.id) },
                    )
                }
                if (rowModules.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ModuleCard(
    progress: ModuleProgress,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val accent = moduleAccent(progress.module)
    val ratio = if (progress.total == 0) 0f else progress.started.toFloat() / progress.total
    Box(
        modifier = modifier
            .clip(AppCardShape)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(Spacing.lg),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(accent),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${progress.started}/${progress.total}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(Spacing.md))
            Text(
                text = progress.module.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = progress.module.description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(Spacing.md))
            MiniProgress(progress = ratio, color = accent)
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = if (progress.started == 0) "未开始" else "平均 Lv.${"%.1f".format(progress.averageLevel)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProgressNote(snapshot: HomeSnapshot, onOpenStats: () -> Unit) {
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenStats),
        background = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.52f),
        borderColor = Color.Transparent,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "学习统计",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "连续 ${snapshot.streakDays} 天 · 已掌握 ${snapshot.masteredFormulas} 个公式",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                )
            }
            Icon(
                imageVector = Icons.Rounded.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

private val DATE_HEADER = java.time.format.DateTimeFormatter.ofPattern(
    "M月d日 EEEE",
    java.util.Locale.CHINA,
)
