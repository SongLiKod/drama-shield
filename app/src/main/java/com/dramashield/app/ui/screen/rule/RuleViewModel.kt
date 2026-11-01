package com.dramashield.app.ui.screen.rule

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dramashield.app.domain.model.InterceptRule
import com.dramashield.app.domain.repository.ConfigRepository
import com.dramashield.app.domain.repository.LogRepository
import com.dramashield.app.domain.repository.RuleRepository
import com.dramashield.app.service.rule.RuleParseException
import com.dramashield.app.util.AppConstants
import com.dramashield.app.util.file.FileExportUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RuleItemUi(
    val rule: InterceptRule,
    val hitCount: Int
)

data class RuleUiState(
    val rules: List<RuleItemUi> = emptyList(),
    val customKeywords: List<String> = emptyList(),
    val isLoading: Boolean = true
)

sealed interface RuleEvent {
    data class Message(val text: String) : RuleEvent
}

@HiltViewModel
class RuleViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ruleRepository: RuleRepository,
    private val configRepository: ConfigRepository,
    logRepository: LogRepository
) : ViewModel() {

    private val eventsChannel = Channel<RuleEvent>(Channel.BUFFERED)
    val events = eventsChannel.receiveAsFlow()

    val uiState: StateFlow<RuleUiState> = combine(
        ruleRepository.rules,
        logRepository.ruleHitCounts,
        configRepository.customFilterKeywords
    ) { rules, hitCounts, customKeywords ->
        RuleUiState(
            rules = rules.map { rule ->
                RuleItemUi(rule = rule, hitCount = hitCounts[rule.id] ?: 0)
            },
            customKeywords = customKeywords.sortedWith(String.CASE_INSENSITIVE_ORDER),
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = RuleUiState()
    )

    fun setRuleEnabled(ruleId: String, enabled: Boolean) {
        viewModelScope.launch {
            runCatching { ruleRepository.setRuleEnabled(ruleId, enabled) }
                .onFailure { eventsChannel.send(RuleEvent.Message("规则状态保存失败")) }
        }
    }

    /**
     * 支持一次输入多条：用逗号、顿号、分号或换行分隔，无需 JSON。
     */
    fun addCustomFilterKeywords(input: String) {
        val rawItems = input
            .split('\n', ',', '，', '、', ';', '；')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (rawItems.isEmpty()) {
            viewModelScope.launch { eventsChannel.send(RuleEvent.Message("请输入过滤文字")) }
            return
        }

        viewModelScope.launch {
            val existing = uiState.value.customKeywords
                .map { it.lowercase() }
                .toMutableSet()
            var added = 0
            var duplicate = 0
            var tooLong = 0
            var overLimit = 0

            rawItems.forEach { raw ->
                if (raw.length > AppConstants.MAX_CUSTOM_FILTER_LENGTH) {
                    tooLong++
                    return@forEach
                }
                if (existing.contains(raw.lowercase())) {
                    duplicate++
                    return@forEach
                }
                if (existing.size >= AppConstants.MAX_CUSTOM_FILTER_KEYWORDS) {
                    overLimit++
                    return@forEach
                }
                configRepository.addCustomFilterKeyword(raw)
                existing.add(raw.lowercase())
                added++
            }

            val message = buildString {
                if (added > 0) append("已添加 $added 条过滤文字")
                if (duplicate > 0) {
                    if (isNotEmpty()) append("；")
                    append("$duplicate 条已存在")
                }
                if (tooLong > 0) {
                    if (isNotEmpty()) append("；")
                    append("$tooLong 条超过 ${AppConstants.MAX_CUSTOM_FILTER_LENGTH} 字")
                }
                if (overLimit > 0) {
                    if (isNotEmpty()) append("；")
                    append("最多 ${AppConstants.MAX_CUSTOM_FILTER_KEYWORDS} 条")
                }
                if (isEmpty()) append("未添加任何文字")
            }
            eventsChannel.send(RuleEvent.Message(message))
        }
    }

    fun removeCustomFilterKeyword(keyword: String) {
        viewModelScope.launch {
            configRepository.removeCustomFilterKeyword(keyword)
            eventsChannel.send(RuleEvent.Message("已删除：$keyword"))
        }
    }

    fun importRulesFromUri(uri: Uri) {
        viewModelScope.launch {
            val result = runCatching {
                val json = FileExportUtils.readText(context, uri)
                ruleRepository.importRules(json)
            }
            result.onSuccess { importResult ->
                eventsChannel.send(
                    RuleEvent.Message(
                        "已导入 ${importResult.importedCount} 条规则，当前共 ${importResult.totalCount} 条"
                    )
                )
            }.onFailure { throwable ->
                val message = when (throwable) {
                    is RuleParseException -> throwable.message
                    is IOException -> throwable.message
                    else -> throwable.message
                } ?: "规则导入失败"
                eventsChannel.send(RuleEvent.Message("导入失败：$message"))
            }
        }
    }

    fun exportRulesToUri(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val json = ruleRepository.exportRulesJson()
                FileExportUtils.writeText(context, uri, json)
            }.onSuccess {
                eventsChannel.send(RuleEvent.Message("规则已导出"))
            }.onFailure {
                eventsChannel.send(RuleEvent.Message("导出失败：${it.message ?: "无法写入文件"}"))
            }
        }
    }
}
