package com.dramashield.app.domain.repository

import kotlinx.coroutines.flow.Flow

interface ConfigRepository {
    val globalSwitch: Flow<Boolean>
    val scanInterval: Flow<Long>
    val whiteListPackages: Flow<Set<String>>
    val firstLaunchGuideShown: Flow<Boolean>

    val customFilterKeywords: Flow<Set<String>>
    val skipPlaybackAds: Flow<Boolean>
    val swipeStartMinPercent: Flow<Int>
    val swipeStartMaxPercent: Flow<Int>
    val swipeEndMinPercent: Flow<Int>
    val swipeEndMaxPercent: Flow<Int>
    val swipeDurationMinMs: Flow<Long>
    val swipeDurationMaxMs: Flow<Long>
    val showSwipeTrace: Flow<Boolean>
    val showFloatingCounter: Flow<Boolean>

    suspend fun setGlobalSwitch(enabled: Boolean)
    suspend fun setScanInterval(intervalMs: Long)
    suspend fun setPackageEnabled(packageName: String, enabled: Boolean)
    suspend fun markFirstLaunchGuideShown()
    suspend fun addCustomFilterKeyword(keyword: String)
    suspend fun removeCustomFilterKeyword(keyword: String)
    suspend fun clearCustomFilterKeywords()
    suspend fun ensureDefaultCustomFilterKeywords()
    suspend fun restoreDefaultCustomFilterKeywords()
    suspend fun setSkipPlaybackAds(enabled: Boolean)
    suspend fun setSwipeStartMinPercent(percent: Int)
    suspend fun setSwipeStartMaxPercent(percent: Int)
    suspend fun setSwipeEndMinPercent(percent: Int)
    suspend fun setSwipeEndMaxPercent(percent: Int)
    suspend fun setSwipeDurationMinMs(durationMs: Long)
    suspend fun setSwipeDurationMaxMs(durationMs: Long)
    suspend fun setShowSwipeTrace(enabled: Boolean)
    suspend fun setShowFloatingCounter(enabled: Boolean)
}
