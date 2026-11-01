package com.dramashield.app.domain.repository

import com.dramashield.app.domain.model.InterceptRule
import kotlinx.coroutines.flow.Flow

data class RuleImportResult(
    val importedCount: Int,
    val totalCount: Int
)

interface RuleRepository {
    val rules: Flow<List<InterceptRule>>

    suspend fun setRuleEnabled(ruleId: String, enabled: Boolean)

    /**
     * 导入 JSON 规则，与当前规则按 id 合并（同 id 覆盖），返回导入数量与合并后总数。
     * @throws com.dramashield.app.service.rule.RuleParseException 当 JSON 不合法时抛出
     */
    suspend fun importRules(json: String): RuleImportResult

    suspend fun exportRulesJson(): String
}
