package com.physcard.physics.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.physcard.physics.domain.level.MemoryLevelPolicy
import com.physcard.physics.domain.model.FormulaModule
import com.physcard.physics.ui.theme.MathFont
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

object Spacing {
    val xxs: Dp = 2.dp
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 24.dp
    val xxl: Dp = 32.dp
    val huge: Dp = 48.dp
}

val AppCardShape = RoundedCornerShape(22.dp)
val AppSmallShape = RoundedCornerShape(14.dp)
val AppPillShape = RoundedCornerShape(100.dp)

@Composable
fun AppBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val background = MaterialTheme.colorScheme.background
    val glow = MaterialTheme.colorScheme.primary.copy(alpha = if (isDarkUi()) 0.10f else 0.075f)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(background)
            .drawBehind {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(glow, Color.Transparent),
                        center = Offset(size.width * 0.86f, size.height * 0.04f),
                        radius = size.width * 0.82f,
                    ),
                )
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, background.copy(alpha = 0.82f)),
                        startY = size.height * 0.55f,
                        endY = size.height,
                    ),
                )
            },
    ) {
        content()
    }
}

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    contentPadding: Dp = Spacing.lg,
    background: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f),
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(AppCardShape)
            .background(background)
            .border(1.dp, borderColor, AppCardShape)
            .padding(contentPadding),
    ) {
        content()
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (action != null && onAction != null) {
            Text(
                text = action,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(AppPillShape)
                    .clickable(onClick = onAction)
                    .padding(horizontal = Spacing.md, vertical = Spacing.xs),
            )
        }
    }
}

@Composable
fun LevelBadge(
    level: Int,
    modifier: Modifier = Modifier,
    showTitle: Boolean = false,
) {
    val color = levelColor(level)
    val safe = level.coerceIn(1, 8)
    Row(
        modifier = modifier
            .clip(AppPillShape)
            .background(color.copy(alpha = 0.13f))
            .border(1.dp, color.copy(alpha = 0.32f), AppPillShape)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(
            text = if (showTitle) "Lv.$safe ${MemoryLevelPolicy.LEVEL_TITLES[safe - 1]}" else "Lv.$safe",
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun AbilityChip(
    title: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val background = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val border = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    val base = Modifier
        .clip(AppPillShape)
        .background(background)
        .border(1.dp, border, AppPillShape)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(horizontal = 12.dp, vertical = 7.dp)
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = contentColor,
        modifier = modifier.then(base),
    )
}

@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        OrbitMark(size = 58.dp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
        Spacer(Modifier.height(Spacing.lg))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = Spacing.xl),
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(Spacing.lg))
            androidx.compose.material3.Button(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
fun LoadingState(modifier: Modifier = Modifier, label: String = "正在准备学习数据…") {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 2.dp,
            modifier = Modifier.size(34.dp),
        )
        Spacer(Modifier.height(Spacing.lg))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun OrbitMark(
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Canvas(modifier = modifier.size(size)) {
        val stroke = this.size.minDimension * 0.055f
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        drawCircle(
            color = color.copy(alpha = 0.22f),
            radius = this.size.minDimension * 0.45f,
            style = Stroke(width = stroke),
        )
        drawArc(
            color = color,
            startAngle = 200f,
            sweepAngle = 250f,
            useCenter = false,
            topLeft = Offset(this.size.width * 0.05f, this.size.height * 0.05f),
            size = Size(this.size.width * 0.9f, this.size.height * 0.9f),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        drawCircle(color = color, radius = this.size.minDimension * 0.11f, center = center)
        drawCircle(
            color = color,
            radius = this.size.minDimension * 0.075f,
            center = Offset(this.size.width * 0.86f, this.size.height * 0.5f),
        )
    }
}

@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 92.dp,
    strokeWidth: Dp = 7.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    centerContent: @Composable () -> Unit = {},
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        label = "progress-ring",
    )
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(this.size.width - stroke, this.size.height - stroke),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * animated,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(this.size.width - stroke, this.size.height - stroke),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        centerContent()
    }
}

@Composable
fun DotSeparator(color: Color = MaterialTheme.colorScheme.outline) {
    Box(
        Modifier
            .padding(horizontal = 7.dp)
            .size(3.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.55f)),
    )
}

fun levelColor(level: Int): Color {
    return when (level.coerceIn(1, 8)) {
        1 -> Color(0xFF8C958D)
        2 -> Color(0xFF6F9877)
        3 -> Color(0xFF4E8B67)
        4 -> Color(0xFF2E7A57)
        5 -> Color(0xFF1E6B4A)
        6 -> Color(0xFF3D7E8A)
        7 -> Color(0xFF506F9A)
        else -> Color(0xFFB1842F)
    }
}

@Composable
fun isDarkUi(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.4f

@Composable
fun moduleAccent(module: FormulaModule?): Color {
    val base = module?.accent ?: MaterialTheme.colorScheme.primary
    return if (isDarkUi()) lerp(base, Color.White, 0.34f) else base
}

fun formatInterval(minutes: Long): String {
    if (minutes <= 0) return "现在"
    if (minutes < 60) return "$minutes 分钟"
    val hours = minutes / 60.0
    if (hours < 24) {
        return if (hours % 1.0 < 0.05) "${hours.toInt()} 小时" else String.format(Locale.CHINA, "%.1f 小时", hours)
    }
    val days = minutes / (60.0 * 24.0)
    return when {
        days < 30 -> "${days.roundToInt()} 天"
        days < 365 -> String.format(Locale.CHINA, "%.1f 个月", days / 30.0)
        else -> String.format(Locale.CHINA, "%.1f 年", days / 365.0)
    }
}

fun formatDue(dueAt: Long?, now: Long = System.currentTimeMillis()): String {
    if (dueAt == null) return "未安排"
    if (dueAt <= now) return "已到期"
    val zone = ZoneId.systemDefault()
    val dueDate = Instant.ofEpochMilli(dueAt).atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val days = java.time.temporal.ChronoUnit.DAYS.between(today, dueDate)
    return when {
        days <= 0L -> "今天"
        days == 1L -> "明天"
        days < 7L -> "$days 天后"
        else -> dueDate.format(DateTimeFormatter.ofPattern("M月d日", Locale.CHINA))
    }
}

fun formatShortDate(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA))

fun durationText(minutes: Long): String {
    if (minutes <= 0) return "不足 1 分钟"
    if (minutes < 60) return "$minutes 分钟"
    val hours = minutes / 60
    val rest = minutes % 60
    return if (rest == 0L) "$hours 小时" else "$hours 小时 $rest 分钟"
}

fun formulaPreview(latex: String, max: Int = 32): String {
    val plain = formulaPlainText(latex)
    return if (plain.length <= max) plain else plain.take(max - 1) + "…"
}

@Composable
fun ThinDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
    )
}

@Composable
fun StatBlock(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.onBackground,
    unit: String? = null,
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontFamily = FontFamily.SansSerif,
                color = accent,
            )
            if (unit != null) {
                Text(
                    text = unit,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 3.dp, bottom = 3.dp),
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun MiniProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    track: Color = MaterialTheme.colorScheme.surfaceVariant,
    height: Dp = 5.dp,
) {
    Box(
        modifier = modifier
            .height(height)
            .clip(AppPillShape)
            .background(track),
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(height)
                .clip(AppPillShape)
                .background(color),
        )
    }
}

@Composable
fun FormulaMark(latex: String, color: Color = MaterialTheme.colorScheme.primary) {
    Text(
        text = latex,
        color = color,
        fontFamily = MathFont,
        fontSize = 17.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
    )
}
