package com.dramashield.app.domain.model

/**
 * 规则 / 日志类型。
 * 文档定义 redvalley 与 ad；system 用于节点解析异常时记录失败日志（需求 3.5）。
 */
enum class RuleType(val value: String, val label: String) {
    RED_VALLEY("redvalley", "红谷"),
    AD("ad", "广告"),
    CUSTOM("custom", "自定义"),
    SYSTEM("system", "系统");

    companion object {
        fun fromValue(value: String): RuleType =
            entries.firstOrNull { it.value.equals(value, ignoreCase = true) } ?: SYSTEM
    }
}
