package com.dramashield.app.data.repository

import androidx.datastore.preferences.core.Preferences
import com.dramashield.app.data.local.SettingsDataStore
import com.dramashield.app.domain.model.InterceptRule
import com.dramashield.app.domain.repository.RuleImportResult
import com.dramashield.app.domain.repository.RuleRepository
import com.dramashield.app.service.rule.BuiltInRules
import com.dramashield.app.service.rule.RuleJsonParser
import com.dramashield.app.service.rule.RuleParseException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@Singleton
class RuleRepositoryImpl @Inject constructor(
    private val settingsDataStore: SettingsDataStore
) : RuleRepository {

    override val rules: Flow<List<InterceptRule>> = settingsDataStore.data
        .map { preferences -> rulesFrom(preferences) }
        .distinctUntilChanged()

    override suspend fun setRuleEnabled(ruleId: String, enabled: Boolean) {
        settingsDataStore.update { preferences ->
            val updated = rulesFrom(preferences).map { rule ->
                if (rule.id == ruleId) rule.copy(enable = enabled) else rule
            }
            preferences[SettingsDataStore.Keys.RULES_JSON] = RuleJsonParser.toJson(updated)
        }
    }

    override suspend fun importRules(json: String): RuleImportResult {
        val imported = RuleJsonParser.parse(json)
        var totalCount = 0
        settingsDataStore.update { preferences ->
            val merged = LinkedHashMap<String, InterceptRule>()
            rulesFrom(preferences).forEach { merged[it.id] = it }
            imported.forEach { merged[it.id] = it }
            if (merged.size > RuleJsonParser.MAX_RULES) {
                throw RuleParseException("合并后规则总数不能超过 ${RuleJsonParser.MAX_RULES} 条")
            }
            totalCount = merged.size
            preferences[SettingsDataStore.Keys.RULES_JSON] =
                RuleJsonParser.toJson(merged.values.toList())
        }
        return RuleImportResult(importedCount = imported.size, totalCount = totalCount)
    }

    override suspend fun exportRulesJson(): String =
        RuleJsonParser.toJson(rules.first())

    private fun rulesFrom(preferences: Preferences): List<InterceptRule> {
        val stored = preferences[SettingsDataStore.Keys.RULES_JSON]
            ?: return BuiltInRules.DEFAULT_RULES
        return RuleJsonParser.parseLenient(stored, BuiltInRules.DEFAULT_RULES)
    }
}
