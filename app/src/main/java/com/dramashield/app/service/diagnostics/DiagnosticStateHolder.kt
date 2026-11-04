package com.dramashield.app.service.diagnostics

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 无障碍服务写入、诊断页面读取的最近状态。
 * 只保留少量文本和结果，不保存截图或完整页面内容。
 */
data class DiagnosticSnapshot(
    val serviceRunning: Boolean = false,
    val foregroundPackage: String? = null,
    val foregroundAppName: String? = null,
    val whiteListed: Boolean = false,
    val ocrEnabled: Boolean = true,
    val lastEventAt: Long = 0L,
    val lastEventSummary: String = "",
    val lastScanAt: Long = 0L,
    val lastScanNodeCount: Int = 0,
    val lastAdReason: String? = null,
    val lastOcrAt: Long = 0L,
    val lastOcrText: String = "",
    val lastOcrError: String? = null,
    val lastSwipeAt: Long = 0L,
    val lastSwipeReason: String? = null,
    val lastSwipeDispatch: Boolean? = null,
    val lastSwipeVerified: Boolean? = null,
    val lastSwipeDetail: String = ""
)

object DiagnosticStateHolder {
    private val _state = MutableStateFlow(DiagnosticSnapshot())
    val state: StateFlow<DiagnosticSnapshot> = _state.asStateFlow()

    fun update(transform: (DiagnosticSnapshot) -> DiagnosticSnapshot) {
        _state.update(transform)
    }

    fun clear() {
        val current = _state.value
        _state.value = DiagnosticSnapshot(
            serviceRunning = current.serviceRunning,
            ocrEnabled = current.ocrEnabled
        )
    }
}
