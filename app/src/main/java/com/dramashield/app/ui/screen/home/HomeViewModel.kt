package com.dramashield.app.ui.screen.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dramashield.app.domain.model.ServiceStatus
import com.dramashield.app.domain.repository.ConfigRepository
import com.dramashield.app.domain.repository.LogRepository
import com.dramashield.app.service.ServiceStateHolder
import com.dramashield.app.util.battery.BatteryOptimizationUtils
import com.dramashield.app.util.permission.AccessibilityUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class HomeUiState(
    val globalSwitch: Boolean = true,
    val accessibilityEnabled: Boolean = false,
    val serviceRunning: Boolean = false,
    val todayCount: Int = 0,
    val totalCount: Int = 0,
    val whiteListCount: Int = 0,
    val batteryOptimizationIgnored: Boolean = false,
    val skipPlaybackAds: Boolean = true,
    val showGuideDialog: Boolean = false,
    val serviceStatus: ServiceStatus = ServiceStatus.NOT_ENABLED
)

sealed interface HomeEvent {
    data object NeedAccessibility : HomeEvent
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val configRepository: ConfigRepository,
    private val logRepository: LogRepository
) : ViewModel() {

    private val accessibilityEnabled = MutableStateFlow(
        AccessibilityUtils.isServiceEnabled(context)
    )
    private val batteryOptimizationIgnored = MutableStateFlow(
        BatteryOptimizationUtils.isIgnoringBatteryOptimizations(context)
    )

    private var statusPollingJob: Job? = null

    private val eventsChannel = Channel<HomeEvent>(Channel.BUFFERED)
    val events = eventsChannel.receiveAsFlow()

    private val switchAndWhiteList = combine(
        configRepository.globalSwitch,
        configRepository.whiteListPackages
    ) { globalSwitch, whiteList ->
        globalSwitch to whiteList.size
    }

    private val counts = combine(
        logRepository.todaySuccessCount,
        logRepository.totalSuccessCount
    ) { today, total -> today to total }

    val uiState: StateFlow<HomeUiState> = combine(
        switchAndWhiteList,
        accessibilityEnabled,
        ServiceStateHolder.isRunning,
        counts,
        batteryOptimizationIgnored
    ) { config, accessibility, running, count, batteryIgnored ->
        HomeUiState(
            globalSwitch = config.first,
            whiteListCount = config.second,
            accessibilityEnabled = accessibility,
            serviceRunning = running,
            todayCount = count.first,
            totalCount = count.second,
            batteryOptimizationIgnored = batteryIgnored,
            serviceStatus = when {
                !accessibility -> ServiceStatus.NOT_ENABLED
                running -> ServiceStatus.RUNNING
                else -> ServiceStatus.DISCONNECTED
            }
        )
    }
        .combine(configRepository.firstLaunchGuideShown) { state, guideShown ->
            state.copy(showGuideDialog = !guideShown && !state.accessibilityEnabled)
        }
        .combine(configRepository.skipPlaybackAds) { state, skipAds ->
            state.copy(skipPlaybackAds = skipAds)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = HomeUiState(
                accessibilityEnabled = AccessibilityUtils.isServiceEnabled(context),
                serviceRunning = ServiceStateHolder.isRunning.value,
                batteryOptimizationIgnored = BatteryOptimizationUtils.isIgnoringBatteryOptimizations(context)
            )
        )

    init {
        startStatusPolling()
    }

    fun onResume() {
        refreshStatus()
        startStatusPolling()
    }

    fun onPause() {
        statusPollingJob?.cancel()
        statusPollingJob = null
    }

    fun setGlobalSwitch(enabled: Boolean) {
        if (!accessibilityEnabled.value) {
            eventsChannel.trySend(HomeEvent.NeedAccessibility)
            return
        }
        viewModelScope.launch {
            configRepository.setGlobalSwitch(enabled)
        }
    }

    fun setSkipPlaybackAds(enabled: Boolean) {
        viewModelScope.launch {
            configRepository.setSkipPlaybackAds(enabled)
        }
    }

    fun onGuideDismissed() {
        viewModelScope.launch { configRepository.markFirstLaunchGuideShown() }
    }

    private fun startStatusPolling() {
        statusPollingJob?.cancel()
        statusPollingJob = viewModelScope.launch {
            while (isActive) {
                refreshStatus()
                delay(STATUS_POLL_INTERVAL_MS)
            }
        }
    }

    private fun refreshStatus() {
        accessibilityEnabled.value = AccessibilityUtils.isServiceEnabled(context)
        batteryOptimizationIgnored.value =
            BatteryOptimizationUtils.isIgnoringBatteryOptimizations(context)
    }

    private companion object {
        const val STATUS_POLL_INTERVAL_MS = 1_000L
    }
}
