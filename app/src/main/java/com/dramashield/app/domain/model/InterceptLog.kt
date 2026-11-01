package com.dramashield.app.domain.model

/**
 * 拦截日志实体，对应文档 5.2 intercept_log 表。
 */
data class InterceptLog(
    val id: Long = 0L,
    val timestamp: Long,
    val packageName: String,
    val appName: String,
    val ruleType: RuleType,
    val ruleId: String,
    val isSuccess: Boolean,
    val detail: String = ""
)
