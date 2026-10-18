package com.physcard.physics.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.physcard.physics.ui.components.AppBackground
import com.physcard.physics.ui.components.AppCardShape
import com.physcard.physics.ui.components.AppPillShape
import com.physcard.physics.ui.components.AbilityChip
import com.physcard.physics.ui.components.Formula
import com.physcard.physics.ui.components.LevelBadge
import com.physcard.physics.ui.components.EmptyState
import com.physcard.physics.ui.components.LoadingState
import com.physcard.physics.ui.components.Spacing
import com.physcard.physics.ui.components.appListBottomPadding
import com.physcard.physics.ui.components.appSystemInsets
import com.physcard.physics.ui.components.formulaPlainText
import com.physcard.physics.ui.components.moduleAccent


@Composable
fun LibraryRoute(
    viewModel: LibraryViewModel,
    initialModule: String?,
    onOpenFormula: (String) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    LibraryScreen(
        state = state,
        initialModule = initialModule,
        onOpenFormula = onOpenFormula,
    )
}

@Composable
fun LibraryScreen(
    state: LibraryUiState,
    initialModule: String?,
    onOpenFormula: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedModule by rememberSaveable(initialModule) {
        mutableStateOf(initialModule?.takeIf { it.isNotBlank() })
    }
    val keyboard = LocalSoftwareKeyboardController.current
    val filtered = remember(state.formulas, query, selectedModule) {
        val needle = query.trim().lowercase()
        state.formulas.filter { card ->
            (selectedModule == null || card.formula.moduleId == selectedModule) &&
                (needle.isEmpty() ||
                    card.formula.title.lowercase().contains(needle) ||
                    card.formula.chapter.lowercase().contains(needle) ||
                    card.formula.latex.lowercase().contains(needle) ||
                    formulaPlainText(card.formula.latex).lowercase().contains(needle) ||
                    card.module?.name?.lowercase()?.contains(needle) == true)
        }
    }
    AppBackground(modifier) {
        if (state.loading) {
            LoadingState(label = "正在整理公式库…")
            return@AppBackground
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .appSystemInsets(bottom = false),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "公式库",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "人教版高中物理 · ${state.formulas.size} 个核心公式",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = "${filtered.size}",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.height(Spacing.md))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text("搜索公式、章节或符号", style = MaterialTheme.typography.bodyMedium)
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Rounded.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Rounded.Close, contentDescription = "清空搜索")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                    ),
                )
                Spacer(Modifier.height(Spacing.sm))
            }
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = Spacing.lg,
                ),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                item {
                    AbilityChip(
                        title = "全部",
                        selected = selectedModule == null,
                        onClick = { selectedModule = null },
                    )
                }
                items(state.modules, key = { it.id }) { module ->
                    AbilityChip(
                        title = module.name,
                        selected = selectedModule == module.id,
                        onClick = {
                            selectedModule = if (selectedModule == module.id) null else module.id
                        },
                    )
                }
            }
            Spacer(Modifier.height(Spacing.sm))
            if (filtered.isEmpty()) {
                EmptyState(
                    title = "没有找到公式",
                    message = "换一个关键词，或切换到其他模块看看。",
                    actionLabel = if (query.isNotEmpty() || selectedModule != null) "清除筛选" else null,
                    onAction = {
                        query = ""
                        selectedModule = null
                    },
                    modifier = Modifier.padding(top = Spacing.xl),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = Spacing.lg,
                        end = Spacing.lg,
                        top = Spacing.xs,
                        bottom = appListBottomPadding(),
                    ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    items(filtered, key = { it.formula.id }) { card ->
                        FormulaLibraryCard(card = card, onClick = { onOpenFormula(card.formula.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun FormulaLibraryCard(card: FormulaCardUi, onClick: () -> Unit) {
    val accent = moduleAccent(card.module)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppCardShape)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(48.dp)
                .clip(AppPillShape)
                .background(accent.copy(alpha = 0.8f)),
        )
        Spacer(Modifier.width(Spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = card.formula.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (card.dueCount > 0) {
                    Spacer(Modifier.width(Spacing.xs))
                    Box(
                        Modifier
                            .clip(AppPillShape)
                            .background(MaterialTheme.colorScheme.tertiaryContainer)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "待复习 ${card.dueCount}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }
            }
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(accent),
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = card.module?.name ?: card.formula.moduleId,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = " · ${card.formula.chapter}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(Spacing.xs))
            Box(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            ) {
                Formula(
                    latex = card.formula.latex,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f),
                )
            }
        }
        Spacer(Modifier.width(Spacing.sm))
        if (card.averageLevel > 0) {
            LevelBadge(level = card.averageLevel)
        } else {
            Text(
                text = "未学",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}
