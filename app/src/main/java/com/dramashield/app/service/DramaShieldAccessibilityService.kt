package com.dramashield.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.dramashield.app.domain.model.InterceptLog
import com.dramashield.app.domain.model.InterceptRule
import com.dramashield.app.domain.model.RuleType
import com.dramashield.app.domain.repository.ConfigRepository
import com.dramashield.app.domain.repository.LogRepository
import com.dramashield.app.domain.repository.RuleRepository
import com.dramashield.app.service.action.ClickExecutor
import com.dramashield.app.service.action.FloatingCounterOverlay
import com.dramashield.app.service.action.SwipeTraceOverlay
import com.dramashield.app.service.detector.AdPopupDetector
import com.dramashield.app.service.detector.CustomTextDetector
import com.dramashield.app.service.detector.DetectionResult
import com.dramashield.app.service.detector.NodeScanner
import com.dramashield.app.service.detector.NodeSnapshot
import com.dramashield.app.service.detector.RedValleyDetector
import com.dramashield.app.service.ocr.ScreenTextOcr
import com.dramashield.app.service.rule.BuiltInRules
import com.dramashield.app.util.KnownPackages
import com.dramashield.app.util.log.AppLogger
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 无障碍主服务（文档 4）。
 *
 * 仅在白名单应用前台、总开关开启时检测；检测间隔可配置；
 * 所有节点遍历与点击均做异常保护，失败写入日志而不是崩溃。
 */
@AndroidEntryPoint
class DramaShieldAccessibilityService : AccessibilityService() {

    @Inject
    lateinit var configRepository: ConfigRepository

    @Inject
    lateinit var ruleRepository: RuleRepository

    @Inject
    lateinit var logRepository: LogRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var configJob: Job? = null
    private var pendingScanJob: Job? = null
    private var foregroundScanJob: Job? = null
    private var screenOcrJob: Job? = null
    private var foregroundWatchdogJob: Job? = null
    private var adSwipeJob: Job? = null

    @Volatile
    private var foregroundPackage: String? = null

    @Volatile
    private var lastWindowClass: String? = null

    @Volatile
    private var skipPlaybackAds: Boolean = true

    @Volatile
    private var swipeStartMinPercent: Int = 50

    @Volatile
    private var swipeStartMaxPercent: Int = 54

    @Volatile
    private var swipeEndMinPercent: Int = 8

    @Volatile
    private var swipeEndMaxPercent: Int = 10

    @Volatile
    private var swipeDurationMinMs: Long = 60L

    @Volatile
    private var swipeDurationMaxMs: Long = 120L

    @Volatile
    private var showSwipeTrace: Boolean = true

    @Volatile
    private var showFloatingCounter: Boolean = true

    @Volatile
    private var lastAdSwipeAt: Long = 0L

    @Volatile
    private var lastAdSkipLogAt: Long = 0L

    @Volatile
    private var ocrCustomTextSeenAt: Long = 0L

    @Volatile
    private var ocrSwipeSentForCurrentAd: Boolean = false

    private var lastOcrRequestLogAt: Long = 0L

    private val powerManager: PowerManager? by lazy {
        runCatching { getSystemService(Context.POWER_SERVICE) as? PowerManager }.getOrNull()
    }

    private val detecting = AtomicBoolean(false)
    private val appNameCache = ConcurrentHashMap<String, String>()

    private val redValleyDetector = RedValleyDetector()
    private val adPopupDetector = AdPopupDetector()
    private val customTextDetector = CustomTextDetector()
    private val clickExecutor: ClickExecutor by lazy { ClickExecutor(this) }
    private val swipeTraceOverlay: SwipeTraceOverlay by lazy { SwipeTraceOverlay(this) }
    private val floatingCounterOverlay: FloatingCounterOverlay by lazy {
        FloatingCounterOverlay(this)
    }
    private val screenTextOcr: ScreenTextOcr by lazy { ScreenTextOcr(this) }

    @Volatile
    private var globalSwitch: Boolean = false

    @Volatile
    private var scanIntervalMs: Long = DEFAULT_SCAN_INTERVAL

    @Volatile
    private var whiteListPackages: Set<String> = emptySet()

    @Volatile
    private var currentRules: List<InterceptRule> = BuiltInRules.DEFAULT_RULES

    @Volatile
    private var customFilterKeywords: Set<String> = emptySet()

    @Volatile
    private var lastScanAt: Long = 0L

    private var lastEventLogAt: Long = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        ServiceStateHolder.setRunning(true)
        AppLogger.d("Accessibility service connected")
        observeConfiguration()
        serviceScope.launch { configRepository.ensureDefaultCustomFilterKeywords() }
        startForegroundWatchdog()
        startScreenOcrLoop()
        serviceScope.launch {
            delay(1_500L)
            ensureForegroundMonitoring()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val accessibilityEvent = event ?: return
        runCatching {
            if (!globalSwitch) return

            if (accessibilityEvent.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                val className = accessibilityEvent.className?.toString()
                if (!className.isNullOrBlank()) {
                    lastWindowClass = className
                }
            }

            if (accessibilityEvent.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                accessibilityEvent.eventType != AccessibilityEvent.TYPE_VIEW_FOCUSED &&
                accessibilityEvent.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            ) {
                return
            }

            val packageName = resolveEventPackage(accessibilityEvent) ?: return
            val canScan = shouldScan(packageName)
            if (canScan) {
                val now = SystemClock.uptimeMillis()
                if (now - lastEventLogAt > 1_000L) {
                    lastEventLogAt = now
                    AppLogger.d(
                        "Event type=${accessibilityEvent.eventType}, class=${accessibilityEvent.className}, " +
                            "text=${accessibilityEvent.text?.joinToString(" ").orEmpty().take(80)}, " +
                            "desc=${accessibilityEvent.contentDescription?.toString().orEmpty().take(80)}, " +
                            "pkg=$packageName"
                    )
                }
            }
            if (!canScan) {
                pendingScanJob?.cancel()
                stopForegroundScan()
                return
            }

            val adClass = activeAdWindow()
            if (skipPlaybackAds && adClass != null) {
                swipeToNextEpisode(packageName, "广告播放页 Activity「$adClass」")
                return
            }

            setForegroundPackage(packageName)
            scheduleDetection(packageName)
        }.onFailure { throwable ->
            AppLogger.e("onAccessibilityEvent failed", throwable)
        }
    }

    override fun onInterrupt() {
        ServiceStateHolder.setRunning(false)
        stopForegroundScan()
        AppLogger.d("Accessibility service interrupted")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        ServiceStateHolder.setRunning(false)
        stopForegroundScan()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        swipeTraceOverlay.hide()
        floatingCounterOverlay.hide()
        screenTextOcr.close()
        ServiceStateHolder.setRunning(false)
        pendingScanJob?.cancel()
        foregroundScanJob?.cancel()
        screenOcrJob?.cancel()
        foregroundWatchdogJob?.cancel()
        adSwipeJob?.cancel()
        configJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun observeConfiguration() {
        configJob?.cancel()
        configJob = serviceScope.launch {
            launch {
                configRepository.globalSwitch.collect {
                    globalSwitch = it
                    updateFloatingCounterVisibility()
                }
            }
            launch {
                configRepository.scanInterval.collect {
                    scanIntervalMs = it.coerceIn(MIN_SCAN_INTERVAL, MAX_SCAN_INTERVAL)
                }
            }
            launch {
                configRepository.whiteListPackages.collect {
                    whiteListPackages = it
                    updateFloatingCounterVisibility()
                    if (it.isNotEmpty()) ensureForegroundMonitoring()
                }
            }
            launch {
                ruleRepository.rules.collect { currentRules = it }
            }
            launch {
                configRepository.customFilterKeywords.collect { customFilterKeywords = it }
            }
            launch {
                configRepository.skipPlaybackAds.collect { skipPlaybackAds = it }
            }
            launch {
                configRepository.swipeStartMinPercent.collect { swipeStartMinPercent = it }
            }
            launch {
                configRepository.swipeStartMaxPercent.collect { swipeStartMaxPercent = it }
            }
            launch {
                configRepository.swipeEndMinPercent.collect { swipeEndMinPercent = it }
            }
            launch {
                configRepository.swipeEndMaxPercent.collect { swipeEndMaxPercent = it }
            }
            launch {
                configRepository.swipeDurationMinMs.collect { swipeDurationMinMs = it }
            }
            launch {
                configRepository.swipeDurationMaxMs.collect { swipeDurationMaxMs = it }
            }
            launch {
                configRepository.showSwipeTrace.collect { showSwipeTrace = it }
            }
            launch {
                configRepository.showFloatingCounter.collect { enabled ->
                    showFloatingCounter = enabled
                    updateFloatingCounterVisibility()
                }
            }
        }
    }

    private fun resolveEventPackage(event: AccessibilityEvent): String? {
        val fromEvent = event.packageName?.toString()?.takeIf { it.isNotBlank() }
        if (fromEvent != null) return fromEvent
        return runCatching { rootInActiveWindow?.packageName?.toString() }.getOrNull()
    }

    private fun shouldScan(packageName: String): Boolean {
        if (!globalSwitch) return false
        if (packageName == this.packageName) return false
        return whiteListPackages.contains(packageName)
    }

    /**
     * 白名单应用处于前台时，按配置间隔持续巡检，覆盖不产生窗口事件的自绘弹窗。
     */
    private fun setForegroundPackage(packageName: String) {
        if (foregroundPackage == packageName) return
        foregroundPackage = packageName
        AppLogger.d("Start foreground scan package=$packageName")
        updateFloatingCounterVisibility()
        foregroundScanJob?.cancel()
        foregroundScanJob = serviceScope.launch {
            while (isActive) {
                delay(scanIntervalMs)
                if (foregroundPackage != packageName) break
                if (!globalSwitch || !whiteListPackages.contains(packageName)) continue
                if (powerManager?.isInteractive == false) continue
                val adClass = activeAdWindow()
                if (skipPlaybackAds && adClass != null) {
                    swipeToNextEpisode(packageName, "广告播放页 Activity「$adClass」")
                    continue
                }

                val rootPackage = runCatching {
                    rootInActiveWindow?.packageName?.toString()
                }.getOrNull()
                if (rootPackage.isNullOrBlank() || rootPackage == packageName) {
                    lastScanAt = SystemClock.uptimeMillis()
                    analyzeAndClose(packageName)
                } else {
                    foregroundPackage = null
                    break
                }
            }
        }
    }

    private fun stopForegroundScan() {
        foregroundPackage = null
        foregroundScanJob?.cancel()
        foregroundScanJob = null
        updateFloatingCounterVisibility()
    }

    /**
     * 部分自绘广告页不产生无障碍事件；用轻量轮询确保前台巡检和 OCR 不会停住。
     */
    private fun startForegroundWatchdog() {
        foregroundWatchdogJob?.cancel()
        foregroundWatchdogJob = serviceScope.launch {
            while (isActive) {
                delay(FOREGROUND_WATCHDOG_INTERVAL_MS)
                if (!globalSwitch) {
                    if (foregroundPackage != null) stopForegroundScan()
                    continue
                }
                val packageName = runCatching {
                    rootInActiveWindow?.packageName?.toString()
                }.getOrNull() ?: continue
                if (whiteListPackages.contains(packageName)) {
                    if (foregroundPackage != packageName) setForegroundPackage(packageName)
                } else if (foregroundPackage != null) {
                    stopForegroundScan()
                }
            }
        }
    }

    /**
     * OCR 与节点扫描分开跑：节点扫描可能被拖慢到几秒一次，
     * 而广告文字识别需要稳定在约 1 秒一次。
     */
    private fun startScreenOcrLoop() {
        screenOcrJob?.cancel()
        screenOcrJob = serviceScope.launch {
            while (isActive) {
                delay(SCREEN_OCR_INTERVAL_MS)
                if (!globalSwitch || whiteListPackages.isEmpty()) continue
                if (powerManager?.isInteractive == false) continue
                val packageName = runCatching {
                    rootInActiveWindow?.packageName?.toString()
                }.getOrNull() ?: continue
                if (!whiteListPackages.contains(packageName)) continue
                scanVisibleCustomText(packageName)
            }
        }
    }

    private fun updateFloatingCounterVisibility() {
        val packageName = foregroundPackage
            ?: runCatching { rootInActiveWindow?.packageName?.toString() }.getOrNull()
        val show = showFloatingCounter &&
            globalSwitch &&
            packageName != null &&
            whiteListPackages.contains(packageName)
        if (show) {
            floatingCounterOverlay.show()
        } else {
            floatingCounterOverlay.hide()
        }
    }

    /**
     * 服务重启或配置加载完成后，主动根据当前前台包名恢复巡检，避免必须等待一次窗口事件。
     */
    private fun ensureForegroundMonitoring() {
        val packageName = runCatching {
            rootInActiveWindow?.packageName?.toString()
        }.getOrNull() ?: return
        if (!shouldScan(packageName)) return

        val adClass = activeAdWindow()
        if (skipPlaybackAds && adClass != null) {
            swipeToNextEpisode(packageName, "广告播放页 Activity「$adClass」")
            return
        }
        if (foregroundPackage != packageName) {
            setForegroundPackage(packageName)
        }
    }

    /**
     * 识别当前是否处于广告播放页。优先使用窗口状态事件的 Activity 类名，
     * 部分机型再用当前活动窗口标题兜底。
     */
    private fun activeAdWindow(): String? {
        val fromEvent = lastWindowClass
        if (isAdWindowClass(fromEvent)) return fromEvent

        val fromWindow = runCatching {
            windows.firstOrNull { it.isActive }?.title?.toString()
        }.getOrNull()
        return fromWindow?.takeIf { isAdWindowClass(it) }
    }

    private fun isAdWindowClass(className: String?): Boolean {
        if (className.isNullOrBlank()) return false
        val normalized = className.lowercase()
        return AD_ACTIVITY_MARKERS.any { normalized.contains(it) }
    }

    /**
     * 返回广告识别原因；null 表示不是播放广告页。
     */
    private fun detectPlaybackAd(
        nodes: List<NodeSnapshot>,
        screenHeight: Int,
        packageName: String
    ): String? {
        val textCount = nodes.count { it.text.isNotBlank() || it.contentDescription.isNotBlank() }
        if (packageName == KnownPackages.HONGGUO_FREE_DRAMA &&
            nodes.size <= 60 &&
            textCount <= 2
        ) {
            return "红果广告页空节点特征"
        }

        val topLimit = screenHeight * AD_LABEL_TOP_RATIO
        val topAdLabel = nodes.firstOrNull { node ->
            if (!node.isVisible) return@firstOrNull false
            val label = node.text.trim()
            node.bounds.top < topLimit && (
                label == "广告" ||
                    label.startsWith("广告") ||
                    label.contains("赞助")
                )
        }
        if (topAdLabel != null) {
            return "顶部广告标记「${topAdLabel.text}」"
        }

        if (hasLiveRoomAdCue(nodes)) {
            return "直播间广告特征「更多直播/直播间」"
        }

        val visibleTexts = nodes.filter { it.isVisible }
            .joinToString(separator = " ") { "${it.text} ${it.contentDescription}" }
        val hasAdExitHint = visibleTexts.contains("上滑继续观看短剧")
        val normalizedTexts = normalizeForMatch(visibleTexts)
        val customHit = customFilterKeywords.firstOrNull { keyword ->
            keyword.isNotBlank() && normalizeForMatch(keyword) in normalizedTexts
        }
        if (customHit != null) {
            return "自定义文字「$customHit」"
        }
        val hasPlayerCue = visibleTexts.contains("选集") ||
            visibleTexts.contains("全屏观看") ||
            visibleTexts.contains("倍速") ||
            EPISODE_REGEX.containsMatchIn(visibleTexts)
        val gameAdCue = GAME_AD_CUES.firstOrNull { visibleTexts.contains(it) }
        if (gameAdCue != null && hasAdExitHint) {
            return "游戏广告特征「$gameAdCue」"
        }
        if (hasAdExitHint && !hasPlayerCue) {
            return "广告页提示「上滑继续观看短剧」"
        }
        val sample = nodes.asSequence()
            .filter { it.text.isNotBlank() }
            .take(10)
            .joinToString(" | ") { it.text.take(20) }
        AppLogger.d("No ad cue nodes=${nodes.size}, sample=$sample")
        return null
    }

    private fun normalizeForMatch(text: String): String =
        text.lowercase()
            .replace("观看", "看")
            .replace(Regex("\\s+"), "")

    /**
     * 无障碍节点读不到自绘广告文字时，用屏幕截图 + 中文 OCR 兜底。
     * 只在白名单应用、开启播放广告跳过、并且配置了自定义文字时运行。
     */
    private fun scanVisibleCustomText(packageName: String) {
        if (!skipPlaybackAds || customFilterKeywords.isEmpty()) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val nowLog = SystemClock.uptimeMillis()
        if (nowLog - lastOcrRequestLogAt > 10_000L) {
            lastOcrRequestLogAt = nowLog
            AppLogger.d("OCR request package=$packageName keywords=${customFilterKeywords.size}")
        }
        screenTextOcr.scan { rawText ->            if (!serviceScope.isActive || !globalSwitch || !skipPlaybackAds) return@scan
            if (!shouldScan(packageName)) return@scan
            val normalizedScreen = normalizeForMatch(rawText)
            val matchedKeywords = customFilterKeywords.filter { keyword ->
                keyword.isNotBlank() && normalizeForMatch(keyword) in normalizedScreen
            }
            val targetHit = matchedKeywords.firstOrNull { keyword ->
                val normalized = normalizeForMatch(keyword)
                "上滑继续看短剧" in normalized || "上滑继续观看短剧" in normalized
            }
            val builtInHit = when {
                "上滑继续看短剧" in normalizedScreen -> "上滑继续看短剧"
                "上滑继续观看短剧" in normalizedScreen -> "上滑继续观看短剧"
                else -> null
            }
            val hit = targetHit ?: matchedKeywords.firstOrNull() ?: builtInHit
            val now = SystemClock.uptimeMillis()
            if (hit == null) {
                // 连续一段时间看不到广告文字，才允许下一次广告再上滑，避免同一广告被反复上滑。
                if (ocrSwipeSentForCurrentAd && now - ocrCustomTextSeenAt > OCR_AD_GONE_RESET_MS) {
                    ocrSwipeSentForCurrentAd = false
                }
                return@scan
            }
            ocrCustomTextSeenAt = now
            if (ocrSwipeSentForCurrentAd) return@scan
            ocrSwipeSentForCurrentAd = true
            if (matchedKeywords.size > 1) {
                AppLogger.d("OCR matched keywords=${matchedKeywords.joinToString(" | ")}")
            }
            AppLogger.d("OCR hit「$hit」package=$packageName")
            swipeToNextEpisode(packageName, "屏幕文字「$hit」")
        }
    }

    /**
     * 广告直播间通常没有“广告”标签，但会出现广告专用直播/优惠券浮层。
     */
    private fun hasLiveRoomAdCue(nodes: List<NodeSnapshot>): Boolean {
        val texts = nodes.filter { it.isVisible }
            .joinToString(separator = " ") { "${it.text} ${it.contentDescription}" }
        if (texts.contains("更多直播")) return true
        val liveRoom = texts.contains("直播间") || texts.contains("点击进入直播间")
        val couponOrShop = texts.contains("立即用券") ||
            texts.contains("惊喜券") ||
            texts.contains("当前直播间可用") ||
            texts.contains("进店") ||
            texts.contains("任务Banner") ||
            texts.contains("活动Banner") ||
            texts.contains("上滑继续观看短剧")
        return liveRoom && couponOrShop
    }

    private data class SwipeSpec(
        val startX: Int,
        val startY: Int,
        val endX: Int,
        val endY: Int,
        val durationMs: Long,
        val startPercent: Int,
        val endPercent: Int
    )

    /**
     * 识别到广告后只做一次上滑：不点击、不返回、不滚动。
     */
    private fun swipeToNextEpisode(packageName: String, reason: String) {
        if (!skipPlaybackAds || !shouldScan(packageName)) return
        if (adSwipeJob?.isActive == true) return

        val now = System.currentTimeMillis()
        if (now - lastAdSwipeAt < AD_SWIPE_COOLDOWN_MS) return
        lastAdSwipeAt = now

        adSwipeJob = serviceScope.launch {
            val screen = currentScreenSize()
            val spec = nextSwipeSpec(screen)
            AppLogger.d("Swipe trace enabled=$showSwipeTrace")
            if (showSwipeTrace) {
                swipeTraceOverlay.show(
                    startX = spec.startX.toFloat(),
                    startY = spec.startY.toFloat(),
                    endX = spec.endX.toFloat(),
                    endY = spec.endY.toFloat(),
                    durationMs = spec.durationMs
                )
            }
            AppLogger.d(
                "Ad detected, swipe up: package=$packageName, reason=$reason, " +
                    "(${spec.startX},${spec.startY})->(${spec.endX},${spec.endY}), " +
                    "duration=${spec.durationMs}ms"
            )
            val swipeSuccess = clickExecutor.swipe(
                startX = spec.startX.toFloat(),
                startY = spec.startY.toFloat(),
                endX = spec.endX.toFloat(),
                endY = spec.endY.toFloat(),
                durationMs = spec.durationMs
            )
            AppLogger.d("Ad swipe up result: success=$swipeSuccess")
            val detail = "广告页规则 playback_ad_skip，命中「$reason」；" +
                "上滑 (${spec.startX},${spec.startY})→(${spec.endX},${spec.endY})，" +
                "起点 ${spec.startPercent}%、终点 ${spec.endPercent}%，时长 ${spec.durationMs}ms"
            floatingCounterOverlay.recordHit()
            if (swipeSuccess) floatingCounterOverlay.recordSwipe()
            logAdSkip(packageName = packageName, success = swipeSuccess, detail = detail)
        }
    }

    private fun nextSwipeSpec(screen: ScreenSize): SwipeSpec {
        val startMin = swipeStartMinPercent.coerceIn(0, 100)
        val startMax = maxOf(startMin, swipeStartMaxPercent.coerceIn(0, 100))
        val startPercent = Random.nextInt(startMin, startMax + 1)

        val endMin = swipeEndMinPercent.coerceIn(0, 100)
        val endMaxLimit = minOf(swipeEndMaxPercent.coerceIn(0, 100), startPercent - 1)
            .coerceAtLeast(0)
        val endPercent = if (endMaxLimit <= endMin) {
            endMin
        } else {
            Random.nextInt(endMin, endMaxLimit + 1)
        }

        val durationMin = swipeDurationMinMs.coerceIn(60L, 6000L)
        val durationMax = maxOf(durationMin, swipeDurationMaxMs.coerceIn(60L, 6000L))
        val duration = if (durationMax <= durationMin) {
            durationMin
        } else {
            Random.nextLong(durationMin, durationMax + 1)
        }

        val startX = (screen.width * 0.5f).toInt()
        val endX = startX
        return SwipeSpec(
            startX = startX,
            startY = (screen.height * startPercent / 100f).toInt(),
            endX = endX,
            endY = (screen.height * endPercent / 100f).toInt(),
            durationMs = duration,
            startPercent = startPercent,
            endPercent = endPercent
        )
    }

    private suspend fun logAdSkip(packageName: String, success: Boolean, detail: String) {
        val now = System.currentTimeMillis()
        if (now - lastAdSkipLogAt < AD_SKIP_LOG_COOLDOWN_MS) return
        lastAdSkipLogAt = now
        insertLog(
            packageName = packageName,
            type = RuleType.AD,
            ruleId = PLAYBACK_AD_SKIP_RULE_ID,
            success = success,
            detail = detail
        )
    }

    /**
     * 按配置间隔节流；若事件发生在间隔内，则延迟到间隔结束再扫描，避免漏检。
     */
    private fun scheduleDetection(packageName: String) {
        val now = SystemClock.uptimeMillis()
        val elapsed = now - lastScanAt
        if (elapsed >= scanIntervalMs) {
            pendingScanJob?.cancel()
            lastScanAt = now
            analyzeAndClose(packageName)
            return
        }

        pendingScanJob?.cancel()
        pendingScanJob = serviceScope.launch {
            delay(scanIntervalMs - elapsed)
            if (!isActive || !globalSwitch || !whiteListPackages.contains(packageName)) return@launch

            val foregroundPackage = runCatching {
                rootInActiveWindow?.packageName?.toString()
            }.getOrNull()
            if (foregroundPackage != packageName) return@launch

            lastScanAt = SystemClock.uptimeMillis()
            analyzeAndClose(packageName)
        }
    }

    private fun analyzeAndClose(packageName: String) {
        if (!detecting.compareAndSet(false, true)) return

        serviceScope.launch {
            try {
                val root = rootInActiveWindow ?: return@launch
                // 双保险：真正扫描前再次确认前台包名属于白名单，避免切换应用后的误扫描。
                val rootPackage = root.packageName?.toString()
                if (!rootPackage.isNullOrBlank() && rootPackage != packageName) {
                    recycleSingleNode(root)
                    return@launch
                }
                val nodes = NodeScanner.scan(root)
                val screen = currentScreenSize()
                try {
                    // 检测次数 = 实际完成的无障碍扫描次数（正常页面也会增长）。
                    floatingCounterOverlay.recordDetection()
                    // 广告页优先：只上滑，不进入任何关键词点击/关闭逻辑。
                    val adReason = detectPlaybackAd(nodes, screen.height, packageName)
                    if (skipPlaybackAds && adReason != null) {
                        swipeToNextEpisode(packageName, adReason)
                        return@launch
                    }

                    val detection = detect(
                        nodes = nodes,
                        packageName = packageName,
                        screenWidth = screen.width,
                        screenHeight = screen.height
                    )
                    if (detection == null) {
                        return@launch
                    }
                    AppLogger.d(
                        "Matched rule=${detection.rule.id}, closeNode=${detection.closeNode != null}, " +
                            "fallback=${detection.fallbackPoint}, package=$packageName"
                    )
                    val success = clickExecutor.close(
                        closeNode = detection.closeNode,
                        fallbackPoint = detection.fallbackPoint
                    )
                    AppLogger.d("Close rule=${detection.rule.id}, success=$success")
                    insertLog(
                        packageName = packageName,
                        type = detection.rule.type,
                        ruleId = detection.rule.id,
                        success = success,
                        detail = "规则 ${detection.rule.id}（${detection.rule.type.label}）" +
                            "命中「${detection.matchedKeyword}」；点击关闭"
                    )
                } finally {
                    recycleNodes(root, nodes)
                }
            } catch (throwable: Throwable) {
                // 节点解析失败不崩溃，记录失败日志（需求 3.5）
                AppLogger.e("Popup detection failed", throwable)
                insertLog(
                    packageName = packageName,
                    type = RuleType.SYSTEM,
                    ruleId = PARSE_ERROR_RULE_ID,
                    success = false,
                    detail = "节点解析异常：${throwable.message ?: throwable.javaClass.simpleName}"
                )
            } finally {
                detecting.set(false)
            }
        }
    }

    /**
     * 红谷/红果红包优先，其次广告，最后用户自定义过滤文字。
     */
    private fun detect(
        nodes: List<NodeSnapshot>,
        packageName: String,
        screenWidth: Int,
        screenHeight: Int
    ): DetectionResult? =
        redValleyDetector.detect(
            rules = currentRules,
            nodes = nodes,
            packageName = packageName,
            screenWidth = screenWidth,
            screenHeight = screenHeight
        ) ?: adPopupDetector.detect(
            rules = currentRules,
            nodes = nodes,
            packageName = packageName,
            screenWidth = screenWidth,
            screenHeight = screenHeight
        ) ?: customTextDetector.detect(
            customKeywords = customFilterKeywords,
            closeKeywords = customCloseKeywords(),
            nodes = nodes,
            packageName = packageName,
            screenWidth = screenWidth,
            screenHeight = screenHeight
        )

    private fun customCloseKeywords(): List<String> =
        (currentRules.filter { it.enable }.flatMap { it.closeKeywords } + DEFAULT_CLOSE_KEYWORDS)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

    private data class ScreenSize(val width: Int, val height: Int)

    private fun currentScreenSize(): ScreenSize {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = getSystemService(WindowManager::class.java).currentWindowMetrics.bounds
            ScreenSize(bounds.width(), bounds.height())
        } else {
            val displayMetrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(displayMetrics)
            ScreenSize(displayMetrics.widthPixels, displayMetrics.heightPixels)
        }
    }

    private suspend fun insertLog(
        packageName: String,
        type: RuleType,
        ruleId: String,
        success: Boolean,
        detail: String = ""
    ) {
        runCatching {
            logRepository.insert(
                InterceptLog(
                    timestamp = System.currentTimeMillis(),
                    packageName = packageName,
                    appName = resolveAppName(packageName),
                    ruleType = type,
                    ruleId = ruleId,
                    isSuccess = success,
                    detail = detail
                )
            )
        }.onFailure { AppLogger.e("Insert intercept log failed", it) }
    }

    private fun resolveAppName(packageName: String): String = appNameCache.getOrPut(packageName) {
        runCatching {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        }.getOrDefault(packageName)
    }

    @Suppress("DEPRECATION")
    private fun recycleNodes(root: android.view.accessibility.AccessibilityNodeInfo, nodes: List<NodeSnapshot>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
        if (nodes.isEmpty()) {
            runCatching { root.recycle() }
            return
        }
        nodes.forEach { runCatching { it.node.recycle() } }
    }

    @Suppress("DEPRECATION")
    private fun recycleSingleNode(node: android.view.accessibility.AccessibilityNodeInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
        runCatching { node.recycle() }
    }

    private companion object {
        const val DEFAULT_SCAN_INTERVAL = 500L
        const val SCREEN_OCR_INTERVAL_MS = 1_200L
        const val FOREGROUND_WATCHDOG_INTERVAL_MS = 2_500L
        const val OCR_AD_GONE_RESET_MS = 20_000L
        const val MIN_SCAN_INTERVAL = 200L
        const val MAX_SCAN_INTERVAL = 2000L
        const val PARSE_ERROR_RULE_ID = "parse_error"
        const val PLAYBACK_AD_SKIP_RULE_ID = "playback_ad_skip"
        const val AD_SKIP_LOG_COOLDOWN_MS = 2_500L
        const val AD_SWIPE_COOLDOWN_MS = 1_800L
        val AD_LABEL_TOP_RATIO = 0.22f
        val EPISODE_REGEX = Regex("第\\s*\\d+\\s*集")
        val GAME_AD_CUES = listOf(
            "点击进入游戏",
            "应用亮点",
            "商家推荐",
            "精彩应用",
            "点击进入直播间",
            "立即打开"
        )
        val AD_ACTIVITY_MARKERS = listOf(
            "excitingvideo",
            "openadsdk",
            "rewardvideo",
            "rewardedvideo",
            "interstitial",
            "splashad",
            ".adactivity",
            "adfullscreen",
            "adplayer"
        )
        val DEFAULT_CLOSE_KEYWORDS = listOf(
            "关闭", "×", "✕", "跳过", "取消", "我知道了", "以后再说"
        )
    }
}
