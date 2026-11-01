package com.dramashield.app.domain.model

/**
 * 拦截规则实体，对应文档 4.4 规则 JSON 结构。
 * [packages] 为空表示对所有白名单应用生效；非空表示仅对指定包名生效。
 */
data class InterceptRule(
    val id: String,
    val type: RuleType,
    val keywords: List<String>,
    val closeKeywords: List<String>,
    val enable: Boolean = true,
    val packages: List<String> = emptyList()
)
