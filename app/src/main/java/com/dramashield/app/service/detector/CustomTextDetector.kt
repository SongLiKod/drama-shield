package com.dramashield.app.service.detector

import com.dramashield.app.domain.model.InterceptRule
import com.dramashield.app.domain.model.RuleType

/**
 * 用户自定义过滤文字检测器。自定义文字会作为一条全局规则参与弹窗匹配。
 */
class CustomTextDetector(
    private val matcher: PopupMatcher = PopupMatcher()
) {
    fun detect(
        customKeywords: Set<String>,
        closeKeywords: List<String>,
        nodes: List<NodeSnapshot>,
        packageName: String,
        screenWidth: Int,
        screenHeight: Int
    ): DetectionResult? {
        if (customKeywords.isEmpty()) return null
        val customRule = InterceptRule(
            id = CUSTOM_RULE_ID,
            type = RuleType.CUSTOM,
            keywords = customKeywords.toList(),
            closeKeywords = closeKeywords,
            enable = true
        )
        return matcher.detect(
            rules = listOf(customRule),
            nodes = nodes,
            packageName = packageName,
            screenWidth = screenWidth,
            screenHeight = screenHeight
        )
    }

    companion object {
        const val CUSTOM_RULE_ID = "custom_text"
    }
}
