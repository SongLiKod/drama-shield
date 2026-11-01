package com.dramashield.app.ui.screen.rule

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dramashield.app.domain.model.InterceptRule
import com.dramashield.app.domain.model.RuleType
import com.dramashield.app.ui.components.EmptyState
import com.dramashield.app.ui.components.ScreenTopBar
import com.dramashield.app.ui.components.SectionCard
import com.dramashield.app.ui.components.StatusBadge
import com.dramashield.app.ui.theme.LocalDramaShieldExtraColors
import com.dramashield.app.util.AppConstants
import com.dramashield.app.util.KnownPackages
import com.dramashield.app.util.file.FileExportUtils

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RuleScreen(
    viewModel: RuleViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showImportWarning by rememberSaveable { mutableStateOf(false) }
    var customInput by rememberSaveable { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    val submitCustomKeyword = {
        if (customInput.isNotBlank()) {
            viewModel.addCustomFilterKeywords(customInput)
            customInput = ""
            keyboardController?.hide()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let(viewModel::importRulesFromUri)
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let(viewModel::exportRulesToUri)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is RuleEvent.Message -> android.widget.Toast.makeText(
                    context,
                    event.text,
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    if (showImportWarning) {
        ImportRiskDialog(
            onConfirm = {
                showImportWarning = false
                importLauncher.launch(
                    arrayOf("application/json", "text/plain", "application/octet-stream")
                )
            },
            onDismiss = { showImportWarning = false }
        )
    }

    Scaffold(
        topBar = { ScreenTopBar(title = "跳过规则") },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                SectionCard(title = "规则说明") {
                    Text(
                        text = "关键词仅用于弹窗过滤；播放广告通过广告页特征识别后直接向上滑动进入下一集，不点击广告页按钮，也不依赖关键词列表。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { showImportWarning = true },
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FileUpload,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "导入规则", maxLines = 1)
                    }
                    Button(
                        onClick = {
                            exportLauncher.launch(
                                FileExportUtils.suggestedFileName("drama_shield_rules")
                            )
                        },
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.small,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FileDownload,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "导出规则", maxLines = 1)
                    }
                }
            }

            item {
                CustomFilterCard(
                    keywords = uiState.customKeywords,
                    input = customInput,
                    onInputChange = { customInput = it },
                    onAdd = submitCustomKeyword,
                    onRemove = viewModel::removeCustomFilterKeyword
                )
            }

            if (uiState.isLoading) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    }
                }
            } else if (uiState.rules.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.Tune,
                        title = "暂无拦截规则",
                        description = "可导入 JSON 规则文件，或重新启动应用恢复内置规则。"
                    )
                }
            } else {
                items(
                    items = uiState.rules,
                    key = { it.rule.id }
                ) { item ->
                    RuleCard(
                        item = item,
                        onEnabledChange = { enabled ->
                            viewModel.setRuleEnabled(item.rule.id, enabled)
                        }
                    )
                }
            }

            item {
                SectionCard(title = "规则维护提示") {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = Icons.Outlined.Warning,
                            contentDescription = null,
                            tint = LocalDramaShieldExtraColors.current.warning,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "短剧厂商与广告 SDK 可能修改广告页样式、跳过按钮和控件 ID，导致规则失效。请仅导入可信来源的规则，导入前建议先导出备份。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CustomFilterCard(
    keywords: List<String>,
    input: String,
    onInputChange: (String) -> Unit,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit
) {
    SectionCard(
        title = "自定义跳过文字",
        trailing = {
            StatusBadge(
                text = "${keywords.size} 条",
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        }
    ) {
        Text(
            text = "直接输入弹窗里看到的文字即可，无需 JSON。此列表仅用于弹窗过滤；播放广告跳过使用广告页识别 + 上滑手势，不依赖关键词。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        text = "如：广告、跳过、开宝箱",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                textStyle = MaterialTheme.typography.bodyMedium,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onAdd() })
            )
            Button(
                onClick = onAdd,
                enabled = input.isNotBlank(),
                shape = MaterialTheme.shapes.small
            ) {
                Text(text = "添加")
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        if (keywords.isEmpty()) {
            Text(
                text = "尚未添加。可添加“跳过”“广告”“开宝箱”等广告页或弹窗文字。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                keywords.forEach { keyword ->
                    InputChip(
                        selected = false,
                        onClick = { onRemove(keyword) },
                        label = {
                            Text(
                                text = keyword,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "删除 $keyword",
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        shape = MaterialTheme.shapes.extraSmall
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "支持用逗号或换行一次添加多条；最多 ${AppConstants.MAX_CUSTOM_FILTER_KEYWORDS} 条，每条最多 ${AppConstants.MAX_CUSTOM_FILTER_LENGTH} 字。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RuleCard(
    item: RuleItemUi,
    onEnabledChange: (Boolean) -> Unit
) {
    val rule = item.rule
    val extraColors = LocalDramaShieldExtraColors.current
    val badgeContentColor = when (rule.type) {
        RuleType.RED_VALLEY -> extraColors.onWarningContainer
        RuleType.AD -> MaterialTheme.colorScheme.onPrimaryContainer
        RuleType.CUSTOM -> MaterialTheme.colorScheme.onSecondaryContainer
        RuleType.SYSTEM -> MaterialTheme.colorScheme.onErrorContainer
    }
    val badgeContainerColor = when (rule.type) {
        RuleType.RED_VALLEY -> extraColors.warningContainer
        RuleType.AD -> MaterialTheme.colorScheme.primaryContainer
        RuleType.CUSTOM -> MaterialTheme.colorScheme.secondaryContainer
        RuleType.SYSTEM -> MaterialTheme.colorScheme.errorContainer
    }

    SectionCard(
        modifier = Modifier.alpha(if (rule.enable) 1f else 0.62f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = ruleTitle(rule),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    StatusBadge(
                        text = rule.type.label,
                        contentColor = badgeContentColor,
                        containerColor = badgeContainerColor
                    )
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "规则 ID：${rule.id}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (rule.packages.isNotEmpty()) {
                    Text(
                        text = "仅限 ${rule.packages.joinToString("、") { KnownPackages.labelOf(it) }}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Switch(
                checked = rule.enable,
                onCheckedChange = onEnabledChange
            )
        }

        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "识别关键词",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            rule.keywords.forEach { keyword ->
                KeywordChip(text = keyword)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "关闭关键词",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            rule.closeKeywords.forEach { keyword ->
                KeywordChip(
                    text = keyword,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "已命中 ${item.hitCount} 次",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun KeywordChip(
    text: String,
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    androidx.compose.material3.Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = containerColor
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
        )
    }
}

private fun ruleTitle(rule: InterceptRule): String = when {
    rule.id.startsWith("hongguo_red") -> "红果红包样式规则"
    rule.id.startsWith("hongguo_ad") -> "红果广告识别规则"
    rule.type == RuleType.RED_VALLEY -> "红包样式规则"
    rule.type == RuleType.AD -> "通用广告识别规则"
    rule.type == RuleType.CUSTOM -> "自定义跳过规则"
    else -> "系统规则"
}

@Composable
private fun ImportRiskDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Outlined.Warning,
                contentDescription = null,
                tint = LocalDramaShieldExtraColors.current.warning
            )
        },
        title = { Text(text = "导入规则风险提示") },
        text = {
            Text(
                text = "规则决定广告识别与自动点击行为。请仅导入可信来源的 JSON 规则，避免规则误匹配导致误点、影响其他应用或泄露隐私。导入后将与当前规则按 ID 合并。",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = "继续导入")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "取消")
            }
        },
        shape = MaterialTheme.shapes.large
    )
}
