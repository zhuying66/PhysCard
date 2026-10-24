package com.physcard.physics.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.physcard.physics.BuildConfig
import com.physcard.physics.data.repository.AppSettings
import com.physcard.physics.ui.components.AppBackground
import com.physcard.physics.ui.components.AppCard
import com.physcard.physics.ui.components.AppPillShape
import com.physcard.physics.ui.components.Spacing
import com.physcard.physics.ui.components.appListBottomPadding
import com.physcard.physics.ui.components.appSystemInsets
import com.physcard.physics.ui.theme.ThemeMode
import kotlin.math.roundToInt

@Composable
fun SettingsRoute(viewModel: SettingsViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val action by viewModel.dataAction.collectAsStateWithLifecycle()
    SettingsScreen(
        settings = settings,
        action = action,
        onThemeChange = viewModel::updateTheme,
        onRetentionChange = viewModel::updateRetention,
        onDailyNewChange = viewModel::updateDailyNew,
        onExport = viewModel::exportTo,
        onImport = viewModel::importFrom,
        onReset = { viewModel.resetProgress() },
        onDismissAction = viewModel::dismissDataAction,
        suggestedExportName = viewModel.suggestedExportName(),
    )
}

@Composable
fun SettingsScreen(
    settings: AppSettings,
    action: DataActionState,
    onThemeChange: (ThemeMode) -> Unit,
    onRetentionChange: (Float) -> Unit,
    onDailyNewChange: (Int) -> Unit,
    onExport: (android.net.Uri) -> Unit,
    onImport: (android.net.Uri) -> Unit,
    onReset: () -> Unit,
    onDismissAction: () -> Unit,
    suggestedExportName: String,
    modifier: Modifier = Modifier,
) {
    var showResetDialog by remember { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(onExport) }
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(onImport) }
    val working = action is DataActionState.Working

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
                Text(
                    text = "设置",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "让复习节奏适合你，而不是相反。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (action is DataActionState.Working) {
                item {
                    Column {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(AppPillShape),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = action.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                SettingsSection(title = "学习节奏", icon = Icons.Rounded.Tune) {
                    ThemeSetting(settings.themeMode, onThemeChange)
                    ThinSettingDivider()
                    RetentionSetting(
                        retention = settings.desiredRetention,
                        onSelect = onRetentionChange,
                    )
                    ThinSettingDivider()
                    DailyNewSetting(
                        value = settings.dailyNewFormulas,
                        enabled = !working,
                        onChange = onDailyNewChange,
                    )
                }
            }
            item {
                SettingsSection(title = "题库数据", icon = Icons.Rounded.Download) {
                    DataRow(
                        title = "导出公式与题目",
                        subtitle = "生成可检查、可备份的 JSON；不包含学习进度",
                        button = "导出",
                        enabled = !working,
                        onClick = { exportLauncher.launch(suggestedExportName) },
                    )
                    ThinSettingDivider()
                    DataRow(
                        title = "导入自定义题库",
                        subtitle = "自动校验格式，跳过重复公式与题目",
                        button = "导入",
                        enabled = !working,
                        onClick = {
                            importLauncher.launch(
                                arrayOf("application/json", "text/plain", "application/octet-stream"),
                            )
                        },
                    )
                    ThinSettingDivider()
                    DataRow(
                        title = "重置学习进度",
                        subtitle = "保留公式内容，清空复习状态、等级与历史",
                        button = "重置",
                        destructive = true,
                        enabled = !working,
                        onClick = { showResetDialog = true },
                    )
                }
            }
            item {
                AboutCard(settings)
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("重置全部学习进度？") },
            text = {
                Text("这会删除所有 FSRS 调度状态、八级等级、复习历史和统计数据，且无法撤销。公式与题目本身会保留。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetDialog = false
                        onReset()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text("确认重置") }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("取消") }
            },
        )
    }

    when (action) {
        is DataActionState.Success -> AlertDialog(
            onDismissRequest = onDismissAction,
            title = { Text(action.title) },
            text = {
                Column {
                    Text(action.message)
                    if (action.warnings.isNotEmpty()) {
                        Spacer(Modifier.height(Spacing.sm))
                        action.warnings.forEach { warning ->
                            Text(
                                text = "· $warning",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismissAction) { Text("知道了") }
            },
        )
        is DataActionState.Error -> AlertDialog(
            onDismissRequest = onDismissAction,
            title = { Text(action.title) },
            text = { Text(action.message) },
            confirmButton = {
                TextButton(onClick = onDismissAction) { Text("关闭") }
            },
        )
        else -> Unit
    }
}

@Composable
private fun SettingsSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(Spacing.sm))
        AppCard(modifier = Modifier.fillMaxWidth(), contentPadding = 0.dp) {
            Column { content() }
        }
    }
}

@Composable
private fun ThemeSetting(current: ThemeMode, onChange: (ThemeMode) -> Unit) {
    Column(modifier = Modifier.padding(Spacing.lg)) {
        Text("外观", style = MaterialTheme.typography.titleSmall)
        Text(
            "支持跟随系统、浅色和深色模式。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.md))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            ThemeMode.entries.forEach { mode ->
                val selected = current == mode
                val label = when (mode) {
                    ThemeMode.SYSTEM -> "跟随系统"
                    ThemeMode.LIGHT -> "浅色"
                    ThemeMode.DARK -> "深色"
                }
                Box(
                    modifier = Modifier
                        .clip(AppPillShape)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        )
                        .clickable { onChange(mode) }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun RetentionSetting(
    retention: Float,
    onSelect: (Float) -> Unit,
) {
    Column(modifier = Modifier.padding(Spacing.lg)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("目标保持率", style = MaterialTheme.typography.titleSmall)
                Text(
                    "FSRS 会按这个保持率安排间隔；越高，复习越频繁。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "${(retention * 100).roundToInt()}%",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(Spacing.md))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            listOf(0.80f, 0.85f, 0.90f, 0.95f, 0.97f).forEach { option ->
                val selected = kotlin.math.abs(retention - option) < 0.001f
                Box(
                    modifier = Modifier
                        .clip(AppPillShape)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant,
                        )
                        .clickable { onSelect(option) }
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                ) {
                    Text(
                        text = "${(option * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = "80% 更轻松 · 90% 推荐 · 97% 更密集",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
private fun DailyNewSetting(value: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("每日新公式上限", style = MaterialTheme.typography.titleSmall)
            Text(
                "每个公式会建立 5 张独立能力卡；建议 2–4 个。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { onChange(value - 1) },
                enabled = enabled && value > 1,
            ) {
                Icon(Icons.Rounded.Remove, contentDescription = "减少")
            }
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.width(24.dp),
            )
            IconButton(
                onClick = { onChange(value + 1) },
                enabled = enabled && value < 8,
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "增加")
            }
        }
    }
}

@Composable
private fun DataRow(
    title: String,
    subtitle: String,
    button: String,
    enabled: Boolean,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(Spacing.sm))
        if (destructive) {
            OutlinedButton(
                onClick = onClick,
                enabled = enabled,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) { Text(button) }
        } else {
            OutlinedButton(
                onClick = onClick,
                enabled = enabled,
                shape = RoundedCornerShape(10.dp),
            ) { Text(button) }
        }
    }
}

@Composable
private fun AboutCard(settings: AppSettings) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                ) {
                    Text(
                        text = "F",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.width(Spacing.sm))
                Column {
                    Text("物理公式记忆", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "版本 ${BuildConfig.VERSION_NAME} · 完全离线",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(Spacing.md))
            Text(
                text = "内容依据人教版高中物理（2019 版）必修与选择性必修教材的知识体系整理，共 91 个核心公式、1365 道选择题（每个公式的每种能力各 3 道）。公式与解析均内置，不依赖网络。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = "调度：官方 java-fsrs 1.0.0（FSRS-5，MIT），目标保持率 ${
                    (settings.desiredRetention * 100).roundToInt()
                }%。等级系统独立于 FSRS，不会覆盖复习日期。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = "本应用不申请网络权限，不包含账号系统、广告或分析 SDK。所有复习记录保存在本机 Room 数据库。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.sm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.FileOpen,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "发现的题库问题可先导出 JSON 检查，再在维护流程中修正。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

@Composable
private fun ThinSettingDivider() {
    Box(
        Modifier
            .padding(horizontal = Spacing.lg)
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
    )
}
