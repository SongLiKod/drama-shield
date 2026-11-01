package com.dramashield.app.service.detector

import com.dramashield.app.domain.model.InterceptRule
import com.dramashield.app.domain.model.RuleType

/**
 * 红谷 / 红果红包弹窗检测器。
 */
class RedValleyDetector(
    private val matcher: PopupMatcher = PopupMatcher()
) {
    fun detect(
        rules: List<InterceptRule>,
        nodes: List<NodeSnapshot>,
        packageName: String,
        screenWidth: Int,
        screenHeight: Int
    ): DetectionResult? = matcher.detect(
        rules = rules.filter { it.type == RuleType.RED_VALLEY },
        nodes = nodes,
        packageName = packageName,
        screenWidth = screenWidth,
        screenHeight = screenHeight
    )
}
