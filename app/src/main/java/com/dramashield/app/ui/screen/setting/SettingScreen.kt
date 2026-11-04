package com.dramashield.app.ui.screen.setting

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dramashield.app.BuildConfig
import com.dramashield.app.domain.model.AppItem
import com.dramashield.app.ui.components.ScreenTopBar
import com.dramashield.app.ui.components.SectionCard
import com.dramashield.app.ui.components.SectionDivider
import com.dramashield.app.ui.components.SettingSwitchRow
import com.dramashield.app.ui.components.StatusBadge
import com.dramashield.app.ui.theme.LocalDramaShieldExtraColors
import com.dramashield.app.util.AppConstants
import com.dramashield.app.util.battery.BatteryOptimizationUtils
import kotlin.math.roundToInt

@Composable
fun SettingScreen(
    onOpenAbout: () -> Unit,
    viewModel: SettingViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    var query by rememberSaveable { mutableStateOf("") }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshBatteryStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val filteredApps = remember(uiState.apps, query) {
        val keyword = query.trim()
        if (keyword.isEmpty()) {
            uiState.apps
        } else {
            uiState.apps.filter { app ->
                app.appName.contains(keyword, ignoreCase = true) ||
                    app.packageName.contains(keyword, ignoreCase = true)
            }
        }
    }

    Scaffold(
        topBar = { ScreenTopBar(title = "设置") },
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
                    title = "应用白名单",
                    trailing = {
                        StatusBadge(
                            text = "已选 ${uiState.whiteList.size} 个",
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    }
                ) {
                    Text(
                        text = "仅勾选的应用在前台时执行广告跳过与弹窗检测，其他应用会被直接跳过，不消耗资源。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                text = "搜索应用名称或包名",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = null
                            )
                        },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(
                                        imageVector = Icons.Outlined.Clear,
                                        contentDescription = "清空搜索"
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = MaterialTheme.shapes.small,
                        textStyle = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    when {
                        uiState.isLoadingApps -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "正在读取已安装应用…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        uiState.apps.isEmpty() -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "未读取到应用列表",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = viewModel::reloadApps) {
                                    Icon(
                                        imageVector = Icons.Outlined.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text = "重新加载")
                                }
                            }
                        }

                        filteredApps.isEmpty() -> {
                            Text(
                                text = "未找到匹配的应用",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }

                        else -> {
                            Text(
                                text = "共 ${filteredApps.size} 个应用",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 320.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(
                                    items = filteredApps,
                                    key = { it.packageName }
                                ) { app ->
                                    AppWhitelistRow(
                                        app = app,
                                        checked = uiState.whiteList.contains(app.packageName),
                                        onCheckedChange = { enabled ->
                                            viewModel.setPackageEnabled(app.packageName, enabled)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(4.dp))
                ScanIntervalCard(
                    intervalMs = uiState.scanInterval,
                    onIntervalChange = viewModel::setScanInterval
                )
            }

            item {
                PlaybackAdSkipCard(
                    enabled = uiState.skipPlaybackAds,
                    onEnabledChange = viewModel::setSkipPlaybackAds
                )
            }

            item {
                OcrRecognitionCard(
                    enabled = uiState.ocrEnabled,
                    supported = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R,
                    onEnabledChange = viewModel::setOcrEnabled
                )
            }

            item {
                SwipeTuningCard(
                    uiState = uiState,
                    onStartRangeChange = { min, max ->
                        viewModel.setSwipeStartMinPercent(min)
                        viewModel.setSwipeStartMaxPercent(max)
                    },
                    onEndRangeChange = { min, max ->
                        viewModel.setSwipeEndMinPercent(min)
                        viewModel.setSwipeEndMaxPercent(max)
                    },
                    onDurationRangeChange = { min, max ->
                        viewModel.setSwipeDurationMinMs(min)
                        viewModel.setSwipeDurationMaxMs(max)
                    },
                    showTrace = uiState.showSwipeTrace,
                    onShowTraceChange = viewModel::setShowSwipeTrace,
                    showCounter = uiState.showFloatingCounter,
                    onShowCounterChange = viewModel::setShowFloatingCounter,
                    onReset = viewModel::resetSwipeGesture
                )
            }

            item {
                BatteryCard(
                    ignored = uiState.batteryOptimizationIgnored,
                    onOpenSettings = {
                        BatteryOptimizationUtils.openBatteryOptimizationSettings(context)
                    }
                )
            }

            item {
                AboutCard(
                    onOpenAbout = onOpenAbout,
                    onOpenRepo = { openUrl(context, AppConstants.REPO_URL) }
                )
            }

            item {
                Text(
                    text = AppConstants.PRIVACY_NOTE,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun AppWhitelistRow(
    app: AppItem,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(
                if (checked) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                } else {
                    androidx.compose.ui.graphics.Color.Transparent
                }
            )
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon(app = app)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.appName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun AppIcon(app: AppItem) {
    val bitmap = remember(app.icon) { app.icon?.asImageBitmap() }
    if (bitmap != null) {
        androidx.compose.foundation.Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp)),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = app.appName.take(1),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun ScanIntervalCard(
    intervalMs: Long,
    onIntervalChange: (Long) -> Unit
) {
    var sliderValue by remember { mutableFloatStateOf(intervalMs.toFloat()) }

    LaunchedEffect(intervalMs) {
        sliderValue = intervalMs.toFloat()
    }

    val sliderSteps = ((AppConstants.MAX_SCAN_INTERVAL_MS - AppConstants.MIN_SCAN_INTERVAL_MS) /
        AppConstants.SCAN_INTERVAL_STEP_MS).toInt() - 1

    SectionCard(title = "检测间隔") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Timer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "扫描频率",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "${sliderValue.toLong()} ms",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            valueRange = AppConstants.MIN_SCAN_INTERVAL_MS.toFloat()..
                AppConstants.MAX_SCAN_INTERVAL_MS.toFloat(),
            steps = sliderSteps,
            onValueChangeFinished = { onIntervalChange(sliderValue.toLong()) }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "200 ms · 更快",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "2000 ms · 更省电",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "默认 500 ms。间隔越短响应越快，但高频轮询会增加耗电；调整后立即生效。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SwipeTuningCard(
    uiState: SettingUiState,
    onStartRangeChange: (Int, Int) -> Unit,
    onEndRangeChange: (Int, Int) -> Unit,
    onDurationRangeChange: (Long, Long) -> Unit,
    showTrace: Boolean,
    onShowTraceChange: (Boolean) -> Unit,
    showCounter: Boolean,
    onShowCounterChange: (Boolean) -> Unit,
    onReset: () -> Unit
) {
    var startRange by remember(
        uiState.swipeStartMinPercent,
        uiState.swipeStartMaxPercent
    ) {
        mutableStateOf(
            uiState.swipeStartMinPercent.toFloat()..uiState.swipeStartMaxPercent.toFloat()
        )
    }
    var endRange by remember(
        uiState.swipeEndMinPercent,
        uiState.swipeEndMaxPercent
    ) {
        mutableStateOf(
            uiState.swipeEndMinPercent.toFloat()..uiState.swipeEndMaxPercent.toFloat()
        )
    }
    var durationRange by remember(
        uiState.swipeDurationMinMs,
        uiState.swipeDurationMaxMs
    ) {
        mutableStateOf(
            uiState.swipeDurationMinMs.toFloat()..uiState.swipeDurationMaxMs.toFloat()
        )
    }

    SectionCard(title = "上滑手势参数") {
        Text(
            text = "每次跳过广告都会在下面的范围内随机取值，避免固定轨迹被识别为脚本。只做上滑，不点击、不返回。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "起点高度：${startRange.start.roundToInt()}% – ${startRange.endInclusive.roundToInt()}%",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        RangeSlider(
            value = startRange,
            onValueChange = { startRange = it },
            valueRange = 0f..100f,
            steps = 99,
            onValueChangeFinished = {
                onStartRangeChange(
                    startRange.start.roundToInt(),
                    startRange.endInclusive.roundToInt()
                )
            }
        )

        Text(
            text = "终点高度：${endRange.start.roundToInt()}% – ${endRange.endInclusive.roundToInt()}%",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        RangeSlider(
            value = endRange,
            onValueChange = { endRange = it },
            valueRange = 0f..100f,
            steps = 99,
            onValueChangeFinished = {
                onEndRangeChange(
                    endRange.start.roundToInt(),
                    endRange.endInclusive.roundToInt()
                )
            }
        )

        Text(
            text = "滑动时长：${durationRange.start.roundToInt()} – ${durationRange.endInclusive.roundToInt()} ms",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        RangeSlider(
            value = durationRange,
            onValueChange = { durationRange = it },
            valueRange = 60f..6000f,
            steps = 98,
            onValueChangeFinished = {
                onDurationRangeChange(
                    durationRange.start.roundToInt().toLong(),
                    durationRange.endInclusive.roundToInt().toLong()
                )
            }
        )

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "默认：起点 50%–54%，终点 8%–10%，时长 60–120ms。起点/终点可在 0%–100% 调整，时长 60–6000ms 可调；终点必须低于起点，系统会自动修正。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = onReset,
            shape = MaterialTheme.shapes.small
        ) {
            Text(text = "恢复默认手势")
        }

        Spacer(modifier = Modifier.height(12.dp))
        SectionDivider()
        Spacer(modifier = Modifier.height(12.dp))
        SettingSwitchRow(
            title = "显示上滑轨迹",
            subtitle = "跳过广告时在屏幕上显示起点、终点、移动轨迹和实际时长；仅用于查看，不影响滑动。",
            checked = showTrace,
            onCheckedChange = onShowTraceChange
        )

        Spacer(modifier = Modifier.height(12.dp))
        SectionDivider()
        Spacer(modifier = Modifier.height(12.dp))
        SettingSwitchRow(
            title = "显示悬浮计数条",
            subtitle = "仅在已选择的白名单应用内显示，半透明可拖动；显示测/中/滑，计数到 999 后回到 0，长按悬浮条可清零。",
            checked = showCounter,
            onCheckedChange = onShowCounterChange
        )
    }
}

@Composable
private fun PlaybackAdSkipCard(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit
) {
    SectionCard(title = "播放广告自动跳过") {
        SettingSwitchRow(
            title = "自动跳过播放中的广告",
            subtitle = "检测到广告播放页后，直接向上滑动进入下一集，不点击页面上的广告按钮；部分广告需等待倒计时结束。",
            checked = enabled,
            onCheckedChange = onEnabledChange
        )
    }
}

@Composable
private fun OcrRecognitionCard(
    enabled: Boolean,
    supported: Boolean,
    onEnabledChange: (Boolean) -> Unit
) {
    SectionCard(title = "屏幕文字识别（OCR）") {
        SettingSwitchRow(
            title = "识别自绘广告文字",
            subtitle = if (supported) {
                "用于识别“上滑继续看短剧”等无障碍树读不到的广告文字。关闭后只使用无障碍节点识别。"
            } else {
                "当前系统低于 Android 11，不支持无障碍截图 OCR，只能使用无障碍节点识别。"
            },
            checked = enabled && supported,
            onCheckedChange = onEnabledChange,
            enabled = supported
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = AppConstants.OCR_PRIVACY_NOTE,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun BatteryCard(
    ignored: Boolean,
    onOpenSettings: () -> Unit
) {    val extraColors = LocalDramaShieldExtraColors.current
    SectionCard(
        title = "电池优化",
        trailing = {
            StatusBadge(
                text = if (ignored) "已忽略" else "可能受限",
                contentColor = if (ignored) extraColors.onSuccessContainer else extraColors.onWarningContainer,
                containerColor = if (ignored) extraColors.successContainer else extraColors.warningContainer
            )
        }
    ) {
        Text(
            text = if (ignored) {
                "系统已允许短剧护盾在后台持续运行，无障碍服务被终止的概率更低。"
            } else {
                "部分厂商的系统会限制后台服务。建议在电池优化设置中允许短剧护盾不受限制，防止无障碍服务被杀。"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = onOpenSettings,
            shape = MaterialTheme.shapes.small
        ) {
            Icon(
                imageVector = Icons.Outlined.BatteryAlert,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = "去设置")
        }
    }
}

@Composable
private fun AboutCard(
    onOpenAbout: () -> Unit,
    onOpenRepo: () -> Unit
) {
    SectionCard(title = "关于") {
        Row(verticalAlignment = Alignment.CenterVertically) {
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
                    text = "短剧护盾",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "版本 ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        SectionDivider()
        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .clickable(onClick = onOpenRepo)
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "仓库地址",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = AppConstants.REPO_URL,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onOpenRepo) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                    contentDescription = "打开仓库地址"
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "免责声明",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = AppConstants.DISCLAIMER,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))
        TextButton(
            onClick = onOpenAbout,
            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp)
        ) {
            Text(text = "查看完整关于页面")
        }
    }
}

private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
