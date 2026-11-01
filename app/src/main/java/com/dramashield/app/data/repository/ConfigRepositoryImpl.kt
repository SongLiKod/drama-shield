package com.dramashield.app.data.repository

import com.dramashield.app.data.local.SettingsDataStore
import com.dramashield.app.domain.repository.ConfigRepository
import com.dramashield.app.util.AppConstants
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Singleton
class ConfigRepositoryImpl @Inject constructor(
    private val settingsDataStore: SettingsDataStore
) : ConfigRepository {

    override val globalSwitch: Flow<Boolean> = settingsDataStore.data
        .map { it[SettingsDataStore.Keys.GLOBAL_SWITCH] ?: true }
        .distinctUntilChanged()

    override val scanInterval: Flow<Long> = settingsDataStore.data
        .map { (it[SettingsDataStore.Keys.SCAN_INTERVAL] ?: DEFAULT_INTERVAL).coerceIn(MIN_INTERVAL, MAX_INTERVAL) }
        .distinctUntilChanged()

    override val whiteListPackages: Flow<Set<String>> = settingsDataStore.data
        .map { it[SettingsDataStore.Keys.WHITE_LIST_PACKAGES] ?: emptySet() }
        .distinctUntilChanged()

    override val firstLaunchGuideShown: Flow<Boolean> = settingsDataStore.data
        .map { it[SettingsDataStore.Keys.FIRST_LAUNCH_GUIDE_SHOWN] ?: false }
        .distinctUntilChanged()

    override val customFilterKeywords: Flow<Set<String>> = settingsDataStore.data
        .map { it[SettingsDataStore.Keys.CUSTOM_FILTER_KEYWORDS] ?: emptySet() }
        .distinctUntilChanged()

    override val skipPlaybackAds: Flow<Boolean> = settingsDataStore.data
        .map { it[SettingsDataStore.Keys.SKIP_PLAYBACK_ADS] ?: true }
        .distinctUntilChanged()

    override val swipeStartMinPercent: Flow<Int> = settingsDataStore.data
        .map { (it[SettingsDataStore.Keys.SWIPE_START_MIN_PERCENT] ?: 50).coerceIn(0, 100) }
        .distinctUntilChanged()

    override val swipeStartMaxPercent: Flow<Int> = settingsDataStore.data
        .map { (it[SettingsDataStore.Keys.SWIPE_START_MAX_PERCENT] ?: 54).coerceIn(0, 100) }
        .distinctUntilChanged()

    override val swipeEndMinPercent: Flow<Int> = settingsDataStore.data
        .map { (it[SettingsDataStore.Keys.SWIPE_END_MIN_PERCENT] ?: 8).coerceIn(0, 100) }
        .distinctUntilChanged()

    override val swipeEndMaxPercent: Flow<Int> = settingsDataStore.data
        .map { (it[SettingsDataStore.Keys.SWIPE_END_MAX_PERCENT] ?: 10).coerceIn(0, 100) }
        .distinctUntilChanged()

    override val swipeDurationMinMs: Flow<Long> = settingsDataStore.data
        .map { (it[SettingsDataStore.Keys.SWIPE_DURATION_MIN_MS] ?: 60L).coerceIn(60L, 6000L) }
        .distinctUntilChanged()

    override val swipeDurationMaxMs: Flow<Long> = settingsDataStore.data
        .map { (it[SettingsDataStore.Keys.SWIPE_DURATION_MAX_MS] ?: 120L).coerceIn(60L, 6000L) }
        .distinctUntilChanged()

    override val showSwipeTrace: Flow<Boolean> = settingsDataStore.data
        .map { it[SettingsDataStore.Keys.SHOW_SWIPE_TRACE] ?: true }
        .distinctUntilChanged()

    override val showFloatingCounter: Flow<Boolean> = settingsDataStore.data
        .map { it[SettingsDataStore.Keys.SHOW_FLOATING_COUNTER] ?: true }
        .distinctUntilChanged()

    override suspend fun setGlobalSwitch(enabled: Boolean) {
        settingsDataStore.update { it[SettingsDataStore.Keys.GLOBAL_SWITCH] = enabled }
    }

    override suspend fun setScanInterval(intervalMs: Long) {
        settingsDataStore.update {
            it[SettingsDataStore.Keys.SCAN_INTERVAL] = intervalMs.coerceIn(MIN_INTERVAL, MAX_INTERVAL)
        }
    }

    override suspend fun setPackageEnabled(packageName: String, enabled: Boolean) {
        if (packageName.isBlank()) return
        settingsDataStore.update { preferences ->
            val current = preferences[SettingsDataStore.Keys.WHITE_LIST_PACKAGES] ?: emptySet()
            preferences[SettingsDataStore.Keys.WHITE_LIST_PACKAGES] =
                if (enabled) current + packageName else current - packageName
        }
    }

    override suspend fun markFirstLaunchGuideShown() {
        settingsDataStore.update { it[SettingsDataStore.Keys.FIRST_LAUNCH_GUIDE_SHOWN] = true }
    }

    override suspend fun addCustomFilterKeyword(keyword: String) {
        val normalized = keyword.trim().take(AppConstants.MAX_CUSTOM_FILTER_LENGTH)
        if (normalized.isEmpty()) return
        settingsDataStore.update { preferences ->
            val current = preferences[SettingsDataStore.Keys.CUSTOM_FILTER_KEYWORDS] ?: emptySet()
            val alreadyExists = current.any { it.equals(normalized, ignoreCase = true) }
            if (!alreadyExists && current.size < AppConstants.MAX_CUSTOM_FILTER_KEYWORDS) {
                preferences[SettingsDataStore.Keys.CUSTOM_FILTER_KEYWORDS] = current + normalized
            }
        }
    }

    override suspend fun removeCustomFilterKeyword(keyword: String) {
        settingsDataStore.update { preferences ->
            val current = preferences[SettingsDataStore.Keys.CUSTOM_FILTER_KEYWORDS] ?: emptySet()
            preferences[SettingsDataStore.Keys.CUSTOM_FILTER_KEYWORDS] =
                current.filterNot { it.equals(keyword, ignoreCase = true) }.toSet()
        }
    }

    override suspend fun clearCustomFilterKeywords() {
        settingsDataStore.update { it[SettingsDataStore.Keys.CUSTOM_FILTER_KEYWORDS] = emptySet() }
    }

    override suspend fun setSkipPlaybackAds(enabled: Boolean) {
        settingsDataStore.update { it[SettingsDataStore.Keys.SKIP_PLAYBACK_ADS] = enabled }
    }

    override suspend fun setSwipeStartMinPercent(percent: Int) {
        settingsDataStore.update {
            it[SettingsDataStore.Keys.SWIPE_START_MIN_PERCENT] = percent.coerceIn(0, 100)
        }
    }

    override suspend fun setSwipeStartMaxPercent(percent: Int) {
        settingsDataStore.update {
            it[SettingsDataStore.Keys.SWIPE_START_MAX_PERCENT] = percent.coerceIn(0, 100)
        }
    }

    override suspend fun setSwipeEndMinPercent(percent: Int) {
        settingsDataStore.update {
            it[SettingsDataStore.Keys.SWIPE_END_MIN_PERCENT] = percent.coerceIn(0, 100)
        }
    }

    override suspend fun setSwipeEndMaxPercent(percent: Int) {
        settingsDataStore.update {
            it[SettingsDataStore.Keys.SWIPE_END_MAX_PERCENT] = percent.coerceIn(0, 100)
        }
    }

    override suspend fun setSwipeDurationMinMs(durationMs: Long) {
        settingsDataStore.update {
            it[SettingsDataStore.Keys.SWIPE_DURATION_MIN_MS] = durationMs.coerceIn(60L, 6000L)
        }
    }

    override suspend fun setSwipeDurationMaxMs(durationMs: Long) {
        settingsDataStore.update {
            it[SettingsDataStore.Keys.SWIPE_DURATION_MAX_MS] = durationMs.coerceIn(60L, 6000L)
        }
    }

    override suspend fun setShowSwipeTrace(enabled: Boolean) {
        settingsDataStore.update { it[SettingsDataStore.Keys.SHOW_SWIPE_TRACE] = enabled }
    }

    override suspend fun setShowFloatingCounter(enabled: Boolean) {
        settingsDataStore.update { it[SettingsDataStore.Keys.SHOW_FLOATING_COUNTER] = enabled }
    }

    companion object {
        const val MIN_INTERVAL = 200L
        const val MAX_INTERVAL = 2000L
        const val DEFAULT_INTERVAL = 500L
    }
}
