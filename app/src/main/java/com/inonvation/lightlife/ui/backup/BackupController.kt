package com.inonvation.lightlife.ui.backup

import com.inonvation.lightlife.data.AppRepository
import com.inonvation.lightlife.data.BackupData
import com.inonvation.lightlife.data.BackupManager
import com.inonvation.lightlife.data.DebugLogStore
import com.inonvation.lightlife.data.PointsStatsStore
import com.inonvation.lightlife.data.PointsTaskStateStore
import com.inonvation.lightlife.data.RestoreCounts
import com.inonvation.lightlife.data.TaskLogStore
import com.inonvation.lightlife.data.TokenExpiredException
import com.inonvation.lightlife.ui.AppUiState
import com.inonvation.lightlife.ui.theme.ColorTheme
import com.inonvation.lightlife.ui.theme.ThemeMode
import com.inonvation.lightlife.ui.theme.ThemePreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class BackupController(
    private val state: MutableStateFlow<AppUiState>,
    private val scope: CoroutineScope,
    private val repository: AppRepository,
    private val backupManager: BackupManager?,
    private val pointsStatsStore: PointsStatsStore?,
    private val taskStateStore: PointsTaskStateStore?,
    private val logStore: TaskLogStore?,
    private val themePreferences: ThemePreferences?,
    private val debugLogStore: DebugLogStore?,
    private val onRestoreFinished: () -> Unit,
    private val showToast: (String) -> Unit,
) {
    private var pendingBackup: BackupData? = null

    fun prepareBackupJson(): String {
        val s = state.value
        val backup = backupManager?.collect(
            appVersion = s.appVersion,
            token = if (s.backupPrivacySafe) null else repository.localToken(),
            orderHistory = s.orderHistory,
            pointsStats = pointsStatsStore,
            taskLogStore = logStore,
            themeMode = s.themeMode.name,
            colorTheme = s.colorTheme.name,
            hapticEnabled = s.hapticEnabled,
            autoCleanLogsEnabled = s.autoCleanLogsEnabled,
            simpleModeEnabled = s.simpleModeEnabled,
            userAgent = s.userAgent,
            taskStateStore = taskStateStore,
        ) ?: return ""
        return backupManager.toJson(backup)
    }

    fun restoreFromBackupJson(json: String) {
        debugLogStore?.d("VM", "restoreFromBackupJson: length=${json.length}")
        scope.launch {
            runCatching {
                var parseError: String? = null
                val backup = backupManager?.fromJson(json) { parseError = it }
                if (backup == null) {
                    showToast(parseError ?: "备份文件格式不匹配，请选择有效的 .lif 备份文件")
                    return@launch
                }
                val hasTokenNow = repository.localToken() != null
                val backupToken = backup.data.token
                val originalToken = repository.localToken()

                // 如果备份有 token，先校验是否有效
                if (!backupToken.isNullOrBlank()) {
                    repository.saveToken(backupToken)
                    val tokenExpired = try {
                        repository.validateToken(); false
                    } catch (e: TokenExpiredException) { true }
                    catch (e: Exception) {
                        if (originalToken != null) repository.saveToken(originalToken)
                        else repository.clearToken()
                        throw e
                    }

                    if (tokenExpired) {
                        // 恢复原来的 token
                        if (originalToken != null) repository.saveToken(originalToken)
                        else repository.clearToken()
                        if (hasTokenNow) {
                            pendingBackup = backup
                            state.update { it.copy(showBackupTokenExpiredDialog = true) }
                        } else {
                            state.update { it.copy(hasToken = false) }
                            showToast("备份文件登录凭证已过期，请使用验证码登录")
                        }
                        return@launch
                    }
                }

                // token 有效或备份没有 token，正常恢复
                val counts = doRestoreBackup(backup)
                showToast("已恢复 ${counts.orders} 条订单、${counts.logs} 条任务记录")
            }.onFailure { e ->
                showToast("导入失败：${e.message ?: "未知错误"}")
            }
        }
    }

    fun confirmBackupImportOrdersOnly() {
        debugLogStore?.d("VM", "confirmBackupImportOrdersOnly")
        val backup = pendingBackup ?: return
        pendingBackup = null
        state.update { it.copy(showBackupTokenExpiredDialog = false) }
        val originalToken = repository.localToken()
        doRestoreBackup(backup)
        // 恢复原来的 token（覆盖备份中的过期 token）
        if (originalToken != null) repository.saveToken(originalToken)
        state.update { s -> s.copy(hasToken = repository.localToken() != null) }
        onRestoreFinished()
        showToast("已导入订单和日志")
    }

    fun dismissBackupTokenExpiredDialog() {
        pendingBackup = null
        state.update { it.copy(showBackupTokenExpiredDialog = false) }
    }

    private fun doRestoreBackup(backup: BackupData): RestoreCounts {
        val counts = backupManager?.restore(backup) ?: RestoreCounts()
        state.update { it.copy(
            hasToken = repository.localToken() != null,
            orderHistory = repository.orderHistory(),
            totalPointsDeducted = pointsStatsStore?.getTotalDeductedAmount() ?: "0.00",
        )}
        backup.data.themeMode?.let { modeName ->
            try {
                val mode = ThemeMode.valueOf(modeName)
                themePreferences?.setThemeMode(mode)
                state.update { it.copy(themeMode = mode) }
            } catch (_: IllegalArgumentException) {}
        }
        backup.data.colorTheme?.let { themeName ->
            try {
                val theme = ColorTheme.valueOf(themeName)
                themePreferences?.setColorTheme(theme)
                state.update { it.copy(colorTheme = theme) }
            } catch (_: IllegalArgumentException) {}
        }
        backup.data.hapticEnabled?.let { enabled ->
            taskStateStore?.setHapticEnabled(enabled)
            state.update { s -> s.copy(hapticEnabled = enabled) }
        }
        backup.data.autoCleanLogsEnabled?.let { autoClean ->
            taskStateStore?.setAutoCleanLogsEnabled(autoClean)
            state.update { s -> s.copy(autoCleanLogsEnabled = autoClean) }
        }
        backup.data.userAgent?.let { ua ->
            if (ua.isNotBlank()) {
                taskStateStore?.setUserAgent(ua)
                state.update { s -> s.copy(userAgent = ua) }
            }
        }
        backup.data.simpleModeEnabled?.let { enabled ->
            taskStateStore?.setSimpleModeEnabled(enabled)
            state.update { s -> s.copy(simpleModeEnabled = enabled) }
        }
        return counts
    }
}
