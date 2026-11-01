package com.dramashield.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "drama_shield_settings"
)

/**
 * DataStore 配置存储（文档 5.1）：总开关、检测间隔、白名单、规则 JSON、首次引导标记。
 */
@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {

    val data: Flow<Preferences> = context.settingsDataStore.data.catch { throwable ->
        if (throwable is IOException) {
            emit(emptyPreferences())
        } else {
            throw throwable
        }
    }

    suspend fun update(transform: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.settingsDataStore.edit(transform)
    }

    object Keys {
        val GLOBAL_SWITCH = booleanPreferencesKey("global_switch")
        val SCAN_INTERVAL = longPreferencesKey("scan_interval")
        val WHITE_LIST_PACKAGES = stringSetPreferencesKey("white_list_packages")
        val RULES_JSON = stringPreferencesKey("rules_json")
        val FIRST_LAUNCH_GUIDE_SHOWN = booleanPreferencesKey("first_launch_guide_shown")
        val CUSTOM_FILTER_KEYWORDS = stringSetPreferencesKey("custom_filter_keywords")
        val CUSTOM_FILTER_DEFAULTS_VERSION = intPreferencesKey("custom_filter_defaults_version")
        val SKIP_PLAYBACK_ADS = booleanPreferencesKey("skip_playback_ads")
        val SWIPE_START_MIN_PERCENT = intPreferencesKey("swipe_start_min_percent")
        val SWIPE_START_MAX_PERCENT = intPreferencesKey("swipe_start_max_percent")
        val SWIPE_END_MIN_PERCENT = intPreferencesKey("swipe_end_min_percent")
        val SWIPE_END_MAX_PERCENT = intPreferencesKey("swipe_end_max_percent")
        val SWIPE_DURATION_MIN_MS = longPreferencesKey("swipe_duration_min_ms")
        val SWIPE_DURATION_MAX_MS = longPreferencesKey("swipe_duration_max_ms")
        val SHOW_SWIPE_TRACE = booleanPreferencesKey("show_swipe_trace")
        val SHOW_FLOATING_COUNTER = booleanPreferencesKey("show_floating_counter")
    }
}
