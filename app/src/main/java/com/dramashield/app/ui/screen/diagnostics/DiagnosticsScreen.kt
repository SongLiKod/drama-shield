package com.dramashield.app.ui.screen.diagnostics

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dramashield.app.service.ServiceStateHolder
import com.dramashield.app.service.diagnostics.DiagnosticSnapshot
import com.dramashield.app.service.diagnostics.DiagnosticStateHolder
import com.dramashield.app.ui.components.ScreenTopBar
import com.dramashield.app.ui.components.SectionCard
import com.dramashield.app.ui.components.SectionDivider
import com.dramashield.app.ui.components.StatusBadge
import com.dramashield.app.ui.theme.LocalDramaShieldExtraColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DiagnosticsScreen() {
    val diagnostic by DiagnosticStateHolder.state.collectAsStateWithLifecycle()
    val running by ServiceStateHolder.isRunning.collectAsStateWithLifecycle()
    val extraColors = LocalDramaShieldExtraColors.current
    val ocrSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    Scaffold(
        topBar = { ScreenTopBar(title = "诊断") },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                SectionCard(
                    title = "服务与前台应用",
                    trailing = {
                        StatusBadge(
                            text = if (running) "运行中" else "未运行",
                            contentColor = if (running) {
                                extraColors.onSuccessContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            containerColor = if (running) {
                                extraColors.successContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                    }
                ) {
                    DiagnosticRow("无障碍服务", if (running) "已连接" else "未连接")
                    DiagnosticRow("前台包名", diagnostic.foregroundPackage ?: "暂无")
                    DiagnosticRow("前台应用", diagnostic.foregroundAppName ?: "暂无")
                    DiagnosticRow("白名单状态", if (diagnostic.whiteListed) "是" else "否")
                    DiagnosticRow("OCR 开关", if (diagnostic.ocrEnabled) "开启" else "关闭")
                    DiagnosticRow("OCR 支持", if (ocrSupported) "支持" else "系统低于 Android 11")
                }
            }

            item {
                SectionCard(title = "最近扫描") {
                    DiagnosticRow("最近事件", formatTime(diagnostic.lastEventAt))
                    DiagnosticRow("事件摘要", diagnostic.lastEventSummary.ifBlank { "暂无" })
                    DiagnosticRow("最近扫描", formatTime(diagnostic.lastScanAt))
                    DiagnosticRow("节点数量", diagnostic.lastScanNodeCount.toString())
                    DiagnosticRow("最近广告判定", diagnostic.lastAdReason ?: "暂无")
                }
            }

            item {
                SectionCard(title = "OCR 识别") {
                    DiagnosticRow("最近识别", formatTime(diagnostic.lastOcrAt))
                    DiagnosticRow("识别文字", diagnostic.lastOcrText.ifBlank { "暂无" })
                    DiagnosticRow("最近错误", diagnostic.lastOcrError ?: "无")
                }
            }

            item {
                SectionCard(title = "上滑验证") {
                    DiagnosticRow("最近上滑", formatTime(diagnostic.lastSwipeAt))
                    DiagnosticRow("触发原因", diagnostic.lastSwipeReason ?: "暂无")
                    DiagnosticRow(
                        "手势发出",
                        when (diagnostic.lastSwipeDispatch) {
                            true -> "是"
                            false -> "否"
                            null -> "未知"
                        }
                    )
                    DiagnosticRow(
                        "页面验证",
                        when (diagnostic.lastSwipeVerified) {
                            true -> "成功：集数已变化"
                            false -> "未通过：集数未变化"
                            null -> "未知"
                        }
                    )
                    DiagnosticRow("验证详情", diagnostic.lastSwipeDetail.ifBlank { "暂无" })
                }
            }

            item {
                SectionCard(title = "诊断说明") {
                    Text(
                        text = "诊断信息只保存在内存中，服务重启或点击下方按钮后清空；不会保存截图，也不会上传。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SectionDivider()
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(onClick = { DiagnosticStateHolder.clear() }) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteSweep,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(text = "清空诊断信息")
                        }
                    }
                }
            }

            item {
                Text(
                    text = "提示：如果 OCR 一直显示“暂无”，先确认设置中的屏幕文字识别已开启，并确认设备是 Android 11 及以上。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 12.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )
    }
}

private val diagnosticTimeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

private fun formatTime(timestamp: Long): String {
    if (timestamp <= 0L) return "暂无"
    return diagnosticTimeFormat.format(Date(timestamp))
}
