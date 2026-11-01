package com.dramashield.app.service.detector

import com.dramashield.app.domain.model.InterceptRule
import com.dramashield.app.domain.model.RuleType

/**
 * 短剧插屏 / 倒计时广告弹窗检测器。
 */
class AdPopupDetector(
    private val matcher: PopupMatcher = PopupMatcher()
) {
    fun detect(
        rules: List<InterceptRule>,
        nodes: List<NodeSnapshot>,
        packageName: String,
        screenWidth: Int,
        screenHeight: Int
    ): DetectionResult? = matcher.detect(
        rules = rules.filter { it.type == RuleType.AD },
        nodes = nodes,
        packageName = packageName,
        screenWidth = screenWidth,
        screenHeight = screenHeight
    )
}
