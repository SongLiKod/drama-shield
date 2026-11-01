package com.dramashield.app.ui.screen.log

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.dramashield.app.domain.model.InterceptLog
import com.dramashield.app.domain.model.RuleType
import com.dramashield.app.ui.components.EmptyState
import com.dramashield.app.ui.components.ScreenTopBar
import com.dramashield.app.ui.components.SectionCard
import com.dramashield.app.ui.components.StatusBadge
import com.dramashield.app.ui.theme.LocalDramaShieldExtraColors
import com.dramashield.app.util.file.FileExportUtils
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private data class FilterOption(val label: String, val type: RuleType?)

private val filterOptions = listOf(
    FilterOption("全部", null),
    FilterOption("红谷", RuleType.RED_VALLEY),
    FilterOption("广告", RuleType.AD),
    FilterOption("自定义", RuleType.CUSTOM)
)

@Composable
fun LogScreen(
    viewModel: LogViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
    val logs = viewModel.logs.collectAsLazyPagingItems()
    var showClearConfirm by rememberSaveable { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let(viewModel::exportLogsToUri)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is LogEvent.Message -> android.widget.Toast.makeText(
                    context,
                    event.text,
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            icon = {
                Icon(
                    imageVector = Icons.Outlined.DeleteSweep,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text(text = "清空全部日志？") },
            text = {
                Text(
                    text = "清空后无法恢复，但不会影响拦截规则、白名单与检测间隔设置。",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirm = false
                        viewModel.clearLogs()
                    }
                ) {
                    Text(text = "清空", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text(text = "取消")
                }
            },
            shape = MaterialTheme.shapes.large
        )
    }

    val isRefreshing = logs.loadState.refresh is LoadState.Loading
    val showEmpty = !isRefreshing && logs.itemCount == 0

    Scaffold(
        topBar = { ScreenTopBar(title = "跳过日志") },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SectionCard {
                    Text(
                        text = "筛选",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        filterOptions.forEach { option ->
                            FilterChip(
                                selected = selectedFilter == option.type,
                                onClick = { viewModel.setFilter(option.type) },
                                label = { Text(text = option.label) },
                                shape = MaterialTheme.shapes.small,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                exportLauncher.launch(
                                    FileExportUtils.suggestedFileName("drama_shield_logs")
                                )
                            },
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.FileDownload,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "导出日志", maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = { showClearConfirm = true },
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.small,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteSweep,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "清空日志", maxLines = 1)
                        }
                    }
                }
            }

            if (isRefreshing && logs.itemCount == 0) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    }
                }
            }

            if (showEmpty) {
                item {
                    EmptyState(
                        icon = Icons.AutoMirrored.Outlined.Article,
                        title = "暂无跳过记录",
                        description = "开启护盾并配置白名单后，播放广告自动跳过与弹窗拦截记录会显示在这里。"
                    )
                }
            }

            items(
                count = logs.itemCount,
                key = logs.itemKey { it.id }
            ) { index ->
                logs[index]?.let { log ->
                    LogCard(log = log)
                }
            }

            if (logs.loadState.append is LoadState.Loading) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LogCard(log: InterceptLog) {
    val extraColors = LocalDramaShieldExtraColors.current
    val typeContentColor = when (log.ruleType) {
        RuleType.RED_VALLEY -> extraColors.onWarningContainer
        RuleType.AD -> MaterialTheme.colorScheme.onPrimaryContainer
        RuleType.CUSTOM -> MaterialTheme.colorScheme.onSecondaryContainer
        RuleType.SYSTEM -> MaterialTheme.colorScheme.onErrorContainer
    }
    val typeContainerColor = when (log.ruleType) {
        RuleType.RED_VALLEY -> extraColors.warningContainer
        RuleType.AD -> MaterialTheme.colorScheme.primaryContainer
        RuleType.CUSTOM -> MaterialTheme.colorScheme.secondaryContainer
        RuleType.SYSTEM -> MaterialTheme.colorScheme.errorContainer
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusBadge(
                    text = log.ruleType.label,
                    contentColor = typeContentColor,
                    containerColor = typeContainerColor
                )
                Spacer(modifier = Modifier.width(8.dp))
                StatusBadge(
                    text = if (log.isSuccess) "成功" else "失败",
                    contentColor = if (log.isSuccess) {
                        extraColors.onSuccessContainer
                    } else {
                        MaterialTheme.colorScheme.onErrorContainer
                    },
                    containerColor = if (log.isSuccess) {
                        extraColors.successContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    }
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = formatTimestamp(log.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = log.appName,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = log.packageName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "命中规则：${displayRuleId(log.ruleId)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (log.detail.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = log.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")

private fun formatTimestamp(timestamp: Long): String =
    Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .format(timeFormatter)

private fun displayRuleId(ruleId: String): String = when (ruleId) {
    "playback_ad_skip" -> "播放广告自动跳过"
    "custom_text" -> "自定义跳过文字"
    "parse_error" -> "节点解析异常"
    else -> ruleId
}
