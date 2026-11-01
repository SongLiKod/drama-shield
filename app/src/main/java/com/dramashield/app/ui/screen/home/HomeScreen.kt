package com.dramashield.app.ui.screen.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dramashield.app.domain.model.ServiceStatus
import com.dramashield.app.ui.components.DisclaimerCard
import com.dramashield.app.ui.components.QuickEntryCard
import com.dramashield.app.ui.components.ScreenTopBar
import com.dramashield.app.ui.components.SectionCard
import com.dramashield.app.ui.components.SettingSwitchRow
import com.dramashield.app.ui.components.StatusBadge
import com.dramashield.app.ui.theme.LocalDramaShieldDarkTheme
import com.dramashield.app.ui.theme.LocalDramaShieldExtraColors
import com.dramashield.app.util.AppConstants
import com.dramashield.app.util.battery.BatteryOptimizationUtils
import com.dramashield.app.util.permission.AccessibilityUtils

@Composable
fun HomeScreen(
    onOpenWhiteList: () -> Unit,
    onOpenLogs: () -> Unit,
    onOpenAbout: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.onResume()
                Lifecycle.Event.ON_PAUSE -> viewModel.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                HomeEvent.NeedAccessibility -> {
                    android.widget.Toast.makeText(
                        context,
                        "请先授予无障碍权限",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    if (uiState.showGuideDialog) {
        AccessibilityGuideDialog(
            onConfirm = {
                viewModel.onGuideDismissed()
                AccessibilityUtils.openAccessibilitySettings(context)
            },
            onDismiss = viewModel::onGuideDismissed
        )
    }

    Scaffold(
        topBar = {
            ScreenTopBar(
                title = "短剧护盾",
                actions = {
                    IconButton(onClick = onOpenAbout) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = "关于"
                        )
                    }
                }
            )
        },
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
                StatusCard(
                    uiState = uiState,
                    onToggleSwitch = viewModel::setGlobalSwitch,
                    onOpenAccessibility = {
                        AccessibilityUtils.openAccessibilitySettings(context)
                    }
                )
            }

            item {
                SectionCard(title = "播放广告自动跳过") {
                    SettingSwitchRow(
                        title = "自动跳过播放中的广告",
                        subtitle = "检测到广告播放页后，直接向上滑动进入下一集，不点击页面上的广告按钮。",
                        checked = uiState.skipPlaybackAds,
                        onCheckedChange = viewModel::setSkipPlaybackAds
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        modifier = Modifier.weight(1f),
                        label = "今日跳过",
                        count = uiState.todayCount
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        label = "累计跳过",
                        count = uiState.totalCount
                    )
                }
            }

            item {
                AnimatedVisibility(
                    visible = !uiState.accessibilityEnabled || !uiState.batteryOptimizationIgnored,
                    enter = fadeIn(tween(220)),
                    exit = fadeOut(tween(160))
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        AnimatedVisibility(visible = !uiState.accessibilityEnabled) {
                            ReminderCard(
                                icon = Icons.Outlined.Lock,
                                title = "无障碍服务未开启",
                                description = "授予权限后，护盾才能自动跳过短剧播放中的广告。",
                                actionText = "去开启",
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                onAction = {
                                    AccessibilityUtils.openAccessibilitySettings(context)
                                }
                            )
                        }
                        AnimatedVisibility(visible = !uiState.batteryOptimizationIgnored) {
                            ReminderCard(
                                icon = Icons.Outlined.BatteryAlert,
                                title = "建议关闭电池优化",
                                description = "部分系统会限制后台服务，关闭电池优化可降低无障碍服务被终止的概率。",
                                actionText = "去设置",
                                containerColor = LocalDramaShieldExtraColors.current.warningContainer,
                                contentColor = LocalDramaShieldExtraColors.current.onWarningContainer,
                                onAction = {
                                    BatteryOptimizationUtils.openBatteryOptimizationSettings(context)
                                }
                            )
                        }
                    }
                }
            }

            item {
                QuickEntryCard(
                    icon = Icons.Outlined.Apps,
                    title = "应用白名单",
                    subtitle = if (uiState.whiteListCount == 0) {
                        "尚未选择应用，拦截不会生效"
                    } else {
                        "已启用 ${uiState.whiteListCount} 个应用"
                    },
                    onClick = onOpenWhiteList
                )
            }

            item {
                QuickEntryCard(
                    icon = Icons.AutoMirrored.Outlined.Article,
                    title = "查看跳过日志",
                    subtitle = "查看广告跳过记录、时间与结果",
                    onClick = onOpenLogs
                )
            }

            item {
                DisclaimerCard(disclaimer = AppConstants.DISCLAIMER)
            }

            item {
                Text(
                    text = "短剧护盾 · 全部数据仅保存在本机",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun StatusCard(
    uiState: HomeUiState,
    onToggleSwitch: (Boolean) -> Unit,
    onOpenAccessibility: () -> Unit
) {
    val extraColors = LocalDramaShieldExtraColors.current
    val statusColor: Color
    val statusContainer: Color
    val statusTitle: String
    val statusDescription: String

    when (uiState.serviceStatus) {
        ServiceStatus.RUNNING -> {
            statusColor = extraColors.success
            statusContainer = extraColors.successContainer
            statusTitle = "运行中"
            statusDescription = "护盾正在守护白名单应用，短剧播放广告会自动跳过。"
        }

        ServiceStatus.NOT_ENABLED -> {
            statusColor = MaterialTheme.colorScheme.onSurfaceVariant
            statusContainer = MaterialTheme.colorScheme.surfaceVariant
            statusTitle = "未开启"
            statusDescription = "请授予无障碍权限，护盾才能自动跳过播放中的广告。"
        }

        ServiceStatus.DISCONNECTED -> {
            statusColor = MaterialTheme.colorScheme.error
            statusContainer = MaterialTheme.colorScheme.errorContainer
            statusTitle = "断开"
            statusDescription = "无障碍服务被系统关闭或中断，请重新开启以恢复拦截。"
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (LocalDramaShieldDarkTheme.current) 1.dp else 3.dp
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "护盾状态",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    AnimatedContent(
                        targetState = statusTitle,
                        transitionSpec = {
                            (fadeIn(tween(220)) + slideInVertically { it / 2 })
                                .togetherWith(fadeOut(tween(120)))
                        },
                        label = "service-status"
                    ) { title ->
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                StatusBadge(
                    text = statusTitle,
                    contentColor = statusColor,
                    containerColor = statusContainer
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = statusDescription,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (uiState.serviceStatus != ServiceStatus.RUNNING) {
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = onOpenAccessibility,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = if (uiState.serviceStatus == ServiceStatus.NOT_ENABLED) {
                            "去开启无障碍权限"
                        } else {
                            "重新开启服务"
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(14.dp))

            SettingSwitchRow(
                title = "护盾总开关",
                subtitle = when {
                    !uiState.accessibilityEnabled -> "授予无障碍权限后可开启广告跳过"
                    !uiState.serviceRunning -> "服务未连接，请检查无障碍设置"
                    uiState.globalSwitch -> "仅在白名单应用内自动跳过播放广告"
                    else -> "已暂停所有广告跳过"
                },
                checked = uiState.accessibilityEnabled && uiState.globalSwitch,
                onCheckedChange = onToggleSwitch,
                enabled = uiState.accessibilityEnabled
            )

            if (!uiState.accessibilityEnabled) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "当前无权限，开关已置灰",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    count: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (LocalDramaShieldDarkTheme.current) 1.dp else 2.dp
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            AnimatedContent(
                targetState = count,
                transitionSpec = {
                    (fadeIn(tween(220)) + slideInVertically { it / 2 })
                        .togetherWith(fadeOut(tween(120)))
                },
                label = "stat-$label"
            ) { value ->
                Text(
                    text = value.toString(),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ReminderCard(
    icon: ImageVector,
    title: String,
    description: String,
    actionText: String,
    containerColor: Color,
    contentColor: Color,
    onAction: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = contentColor
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.85f)
                )
                TextButton(
                    onClick = onAction,
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp)
                ) {
                    Text(text = actionText, color = contentColor)
                }
            }
        }
    }
}

@Composable
private fun AccessibilityGuideDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Outlined.Shield,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text(text = "开启无障碍服务") },
        text = {
            Text(
                text = "短剧护盾依赖无障碍服务识别短剧播放中的广告，并自动点击跳过、关闭或退出。服务仅在本机运行，不会上传任何数据。",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = "去开启")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "稍后")
            }
        },
        shape = MaterialTheme.shapes.large
    )
}
