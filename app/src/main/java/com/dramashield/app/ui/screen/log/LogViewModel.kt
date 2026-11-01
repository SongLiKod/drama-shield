package com.dramashield.app.ui.screen.log

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.dramashield.app.domain.model.InterceptLog
import com.dramashield.app.domain.model.RuleType
import com.dramashield.app.domain.repository.LogRepository
import com.dramashield.app.util.file.FileExportUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

sealed interface LogEvent {
    data class Message(val text: String) : LogEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LogViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logRepository: LogRepository
) : ViewModel() {

    private val filter = MutableStateFlow<RuleType?>(null)
    val selectedFilter: StateFlow<RuleType?> = filter.asStateFlow()

    val logs: Flow<PagingData<InterceptLog>> = filter
        .flatMapLatest { type -> logRepository.pagedLogs(type) }
        .cachedIn(viewModelScope)

    private val eventsChannel = Channel<LogEvent>(Channel.BUFFERED)
    val events = eventsChannel.receiveAsFlow()

    fun setFilter(type: RuleType?) {
        filter.value = type
    }

    fun clearLogs() {
        viewModelScope.launch {
            runCatching { logRepository.clear() }
                .onSuccess { eventsChannel.send(LogEvent.Message("日志已清空")) }
                .onFailure { eventsChannel.send(LogEvent.Message("清空失败，请稍后重试")) }
        }
    }

    fun exportLogsToUri(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val json = logRepository.exportLogsJson()
                FileExportUtils.writeText(context, uri, json)
            }.onSuccess {
                eventsChannel.send(LogEvent.Message("日志已导出"))
            }.onFailure {
                eventsChannel.send(LogEvent.Message("导出失败：${it.message ?: "无法写入文件"}"))
            }
        }
    }
}
