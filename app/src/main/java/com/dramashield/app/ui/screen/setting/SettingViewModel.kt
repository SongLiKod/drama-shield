package com.dramashield.app.ui.screen.setting

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dramashield.app.domain.model.AppItem
import com.dramashield.app.domain.repository.AppRepository
import com.dramashield.app.domain.repository.ConfigRepository
import com.dramashield.app.util.AppConstants
import com.dramashield.app.util.battery.BatteryOptimizationUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingUiState(
    val apps: List<AppItem> = emptyList(),
    val isLoadingApps: Boolean = true,
    val whiteList: Set<String> = emptySet(),
    val scanInterval: Long = AppConstants.DEFAULT_SCAN_INTERVAL_MS,
    val batteryOptimizationIgnored: Boolean = false,
    val skipPlaybackAds: Boolean = true,
    val swipeStartMinPercent: Int = 50,
    val swipeStartMaxPercent: Int = 54,
    val swipeEndMinPercent: Int = 8,
    val swipeEndMaxPercent: Int = 10,
    val swipeDurationMinMs: Long = 60L,
    val swipeDurationMaxMs: Long = 120L,
    val showSwipeTrace: Boolean = true,
    val showFloatingCounter: Boolean = true,
    val ocrEnabled: Boolean = true
)

private data class GestureConfig(
    val startMin: Int,
    val startMax: Int,
    val endMin: Int,
    val endMax: Int,
    val durationMin: Long,
    val durationMax: Long
)

@HiltViewModel
class SettingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appRepository: AppRepository,
    private val configRepository: ConfigRepository
) : ViewModel() {

    private val apps = MutableStateFlow<List<AppItem>>(emptyList())
    private val loadingApps = MutableStateFlow(true)
    private val batteryOptimizationIgnored = MutableStateFlow(
        BatteryOptimizationUtils.isIgnoringBatteryOptimizations(context)
    )

    private val gestureConfig = combine(
        configRepository.swipeStartMinPercent,
        configRepository.swipeStartMaxPercent,
        configRepository.swipeEndMinPercent,
        configRepository.swipeEndMaxPercent,
        configRepository.swipeDurationMinMs
    ) { startMin, startMax, endMin, endMax, durationMin ->
        GestureConfig(startMin, startMax, endMin, endMax, durationMin, 120L)
    }.combine(configRepository.swipeDurationMaxMs) { config, durationMax ->
        config.copy(durationMax = durationMax)
    }

    val uiState: StateFlow<SettingUiState> = combine(
        apps,
        loadingApps,
        configRepository.whiteListPackages,
        configRepository.scanInterval,
        batteryOptimizationIgnored
    ) { appList, loading, whiteList, interval, batteryIgnored ->
        SettingUiState(
            apps = appList,
            isLoadingApps = loading,
            whiteList = whiteList,
            scanInterval = interval,
            batteryOptimizationIgnored = batteryIgnored
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = SettingUiState(
            isLoadingApps = true,
            batteryOptimizationIgnored = BatteryOptimizationUtils.isIgnoringBatteryOptimizations(context)
        )
    ).combine(configRepository.skipPlaybackAds) { state, skipAds ->
        state.copy(skipPlaybackAds = skipAds)
    }.combine(gestureConfig) { state, gesture ->
        state.copy(
            swipeStartMinPercent = gesture.startMin,
            swipeStartMaxPercent = gesture.startMax,
            swipeEndMinPercent = gesture.endMin,
            swipeEndMaxPercent = gesture.endMax,
            swipeDurationMinMs = gesture.durationMin,
            swipeDurationMaxMs = gesture.durationMax
        )
    }.combine(configRepository.showSwipeTrace) { state, showTrace ->
        state.copy(showSwipeTrace = showTrace)
    }.combine(configRepository.showFloatingCounter) { state, showCounter ->
        state.copy(showFloatingCounter = showCounter)
    }.combine(configRepository.ocrEnabled) { state, ocrEnabled ->
        state.copy(ocrEnabled = ocrEnabled)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = SettingUiState(
            isLoadingApps = true,
            batteryOptimizationIgnored = BatteryOptimizationUtils.isIgnoringBatteryOptimizations(context)
        )
    )

    init {
        reloadApps()
    }

    fun reloadApps() {
        viewModelScope.launch {
            loadingApps.value = true
            val installed = runCatching { appRepository.getInstalledApps() }.getOrDefault(emptyList())
            apps.value = installed
            loadingApps.value = false
        }
    }

    fun setPackageEnabled(packageName: String, enabled: Boolean) {
        viewModelScope.launch {
            configRepository.setPackageEnabled(packageName, enabled)
        }
    }

    fun setScanInterval(intervalMs: Long) {
        viewModelScope.launch {
            configRepository.setScanInterval(intervalMs)
        }
    }

    fun setSkipPlaybackAds(enabled: Boolean) {
        viewModelScope.launch {
            configRepository.setSkipPlaybackAds(enabled)
        }
    }

    fun setSwipeStartMinPercent(percent: Int) {
        viewModelScope.launch { configRepository.setSwipeStartMinPercent(percent) }
    }

    fun setSwipeStartMaxPercent(percent: Int) {
        viewModelScope.launch { configRepository.setSwipeStartMaxPercent(percent) }
    }

    fun setSwipeEndMinPercent(percent: Int) {
        viewModelScope.launch { configRepository.setSwipeEndMinPercent(percent) }
    }

    fun setSwipeEndMaxPercent(percent: Int) {
        viewModelScope.launch { configRepository.setSwipeEndMaxPercent(percent) }
    }

    fun setSwipeDurationMinMs(durationMs: Long) {
        viewModelScope.launch { configRepository.setSwipeDurationMinMs(durationMs) }
    }

    fun setSwipeDurationMaxMs(durationMs: Long) {
        viewModelScope.launch { configRepository.setSwipeDurationMaxMs(durationMs) }
    }

    fun setShowSwipeTrace(enabled: Boolean) {
        viewModelScope.launch { configRepository.setShowSwipeTrace(enabled) }
    }

    fun setShowFloatingCounter(enabled: Boolean) {
        viewModelScope.launch { configRepository.setShowFloatingCounter(enabled) }
    }

    fun setOcrEnabled(enabled: Boolean) {
        viewModelScope.launch { configRepository.setOcrEnabled(enabled) }
    }

    fun resetSwipeGesture() {
        viewModelScope.launch {
            configRepository.setSwipeStartMinPercent(50)
            configRepository.setSwipeStartMaxPercent(54)
            configRepository.setSwipeEndMinPercent(8)
            configRepository.setSwipeEndMaxPercent(10)
            configRepository.setSwipeDurationMinMs(60L)
            configRepository.setSwipeDurationMaxMs(120L)
        }
    }

    fun refreshBatteryStatus() {
        batteryOptimizationIgnored.value =
            BatteryOptimizationUtils.isIgnoringBatteryOptimizations(context)
    }
}
