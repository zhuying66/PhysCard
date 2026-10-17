package com.physcard.physics.ui.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.physcard.physics.BuildConfig
import com.physcard.physics.PhysicsApp
import com.physcard.physics.data.repository.AppSettings
import com.physcard.physics.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

sealed interface DataActionState {
    data object Idle : DataActionState
    data class Working(val label: String) : DataActionState
    data class Success(
        val title: String,
        val message: String,
        val warnings: List<String> = emptyList(),
    ) : DataActionState

    data class Error(val title: String, val message: String) : DataActionState
}

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as PhysicsApp).container
    private val appContext = application.applicationContext

    val settings: StateFlow<AppSettings> = container.settingsRepository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppSettings(),
    )

    private val _dataAction = MutableStateFlow<DataActionState>(DataActionState.Idle)
    val dataAction: StateFlow<DataActionState> = _dataAction.asStateFlow()

    fun updateTheme(mode: ThemeMode) {
        viewModelScope.launch { container.settingsRepository.updateTheme(mode) }
    }

    fun updateRetention(value: Float) {
        viewModelScope.launch {
            container.settingsRepository.updateDesiredRetention(value)
            container.fsrsScheduler.updateRetention(value)
        }
    }

    fun updateDailyNew(value: Int) {
        viewModelScope.launch { container.settingsRepository.updateDailyNewFormulas(value) }
    }

    fun exportTo(uri: Uri) {
        if (_dataAction.value is DataActionState.Working) return
        viewModelScope.launch {
            _dataAction.value = DataActionState.Working("正在导出题库…")
            runCatching {
                withContext(Dispatchers.IO) {
                    val payload = container.contentRepository.exportJson(BuildConfig.VERSION_NAME)
                    val bytes = payload.toByteArray(Charsets.UTF_8)
                    appContext.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                        output.write(bytes)
                    } ?: error("无法写入所选文件。")
                    bytes.size
                }
            }.onSuccess { bytes ->
                _dataAction.value = DataActionState.Success(
                    title = "导出完成",
                    message = "已写出约 ${bytes / 1024} KB 的公式与题目 JSON。学习进度仍保存在本机。",
                )
            }.onFailure { error ->
                _dataAction.value = DataActionState.Error(
                    title = "导出失败",
                    message = error.message ?: "无法写入文件。",
                )
            }
        }
    }

    fun importFrom(uri: Uri) {
        if (_dataAction.value is DataActionState.Working) return
        viewModelScope.launch {
            _dataAction.value = DataActionState.Working("正在校验并导入题库…")
            runCatching {
                withContext(Dispatchers.IO) {
                    val text = appContext.contentResolver.openInputStream(uri)?.use { input ->
                        input.bufferedReader(Charsets.UTF_8).readText()
                    } ?: error("无法读取所选文件。")
                    if (text.length > MAX_IMPORT_CHARS) {
                        error("文件过大，超过 8 MB 限制。")
                    }
                    val report = container.contentRepository.importJson(text)
                    container.studyRepository.ensureReviewStates()
                    report
                }
            }.onSuccess { report ->
                _dataAction.value = DataActionState.Success(
                    title = "导入完成",
                    message = buildString {
                        append("新增 ${report.importedFormulas} 个公式、${report.importedQuestions} 道题。")
                        if (report.skippedFormulas > 0) {
                            append("跳过重复公式 ${report.skippedFormulas} 个；")
                        }
                        if (report.skippedQuestions > 0) {
                            append("跳过重复或无效题目 ${report.skippedQuestions} 道。")
                        }
                    },
                    warnings = report.warnings,
                )
            }.onFailure { error ->
                _dataAction.value = DataActionState.Error(
                    title = "导入失败",
                    message = error.message ?: "文件格式不正确。",
                )
            }
        }
    }

    fun resetProgress(onDone: () -> Unit = {}) {
        if (_dataAction.value is DataActionState.Working) return
        viewModelScope.launch {
            _dataAction.value = DataActionState.Working("正在重置学习进度…")
            runCatching { container.studyRepository.resetProgress() }
                .onSuccess {
                    _dataAction.value = DataActionState.Success(
                        title = "进度已重置",
                        message = "公式与题目仍然保留，所有复习状态、等级和历史记录已清空。",
                    )
                    onDone()
                }
                .onFailure { error ->
                    _dataAction.value = DataActionState.Error(
                        title = "重置失败",
                        message = error.message ?: "请稍后重试。",
                    )
                }
        }
    }

    fun dismissDataAction() {
        _dataAction.value = DataActionState.Idle
    }

    fun suggestedExportName(): String {
        val date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
        return "physics-formulas-$date.json"
    }

    companion object {
        private const val MAX_IMPORT_CHARS = 8 * 1024 * 1024
    }
}
