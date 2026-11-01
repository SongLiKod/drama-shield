package com.dramashield.app.service.detector

import android.graphics.PointF
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.dramashield.app.domain.model.InterceptRule
import java.util.Locale
import kotlin.math.hypot

data class DetectionResult(
    val rule: InterceptRule,
    val closeNode: AccessibilityNodeInfo?,
    val fallbackPoint: PointF?,
    val matchedKeyword: String = ""
)

/**
 * 通用弹窗匹配：
 * 1. 关键词命中规则；
 * 2. 在弹窗范围内寻找关闭关键词 / 关闭控件；
 * 3. 没有文字关闭按钮时，按弹窗右上角几何位置推断关闭控件或坐标兜底。
 *
 * 悬浮小气泡等非弹窗元素通常找不到弹窗容器，也不会产生关闭动作，避免误点。
 */
class PopupMatcher {

    fun detect(
        rules: List<InterceptRule>,
        nodes: List<NodeSnapshot>,
        packageName: String,
        screenWidth: Int,
        screenHeight: Int
    ): DetectionResult? {
        val applicableRules = rules
            .filter { rule ->
                rule.enable &&
                    rule.keywords.isNotEmpty() &&
                    (rule.packages.isEmpty() || rule.packages.contains(packageName))
            }
            // 定向规则优先于通用规则
            .sortedByDescending { it.packages.isNotEmpty() }

        val matchedRule: InterceptRule
        var matchedKeyword = ""
        val candidate = applicableRules.firstOrNull { rule ->
            val keyword = rule.keywords.firstOrNull { keyword ->
                nodes.any { node -> matchesAnyKeyword(node, listOf(keyword)) }
            }
            if (keyword != null) matchedKeyword = keyword
            keyword != null
        } ?: return null
        matchedRule = candidate

        val matchedNodes = nodes.filter { node -> matchesAnyKeyword(node, matchedRule.keywords) }
        val popupBounds = matchedNodes.asSequence()
            .mapNotNull { node -> findPopupBounds(node, screenWidth, screenHeight) }
            .firstOrNull()

        val closeNode = findCloseNode(nodes, matchedRule.closeKeywords, popupBounds)
            ?: popupBounds?.let { findCloseNodeByGeometry(nodes, it) }

        val fallbackPoint = popupBounds?.let { bounds ->
            PointF(
                bounds.right - bounds.width() * FALLBACK_X_INSET_RATIO,
                bounds.top + bounds.height() * FALLBACK_Y_INSET_RATIO
            )
        }

        // 没有关闭节点也没有可靠弹窗容器时不做任何点击，避免对悬浮气泡盲目兜底。
        if (closeNode == null && fallbackPoint == null) return null

        return DetectionResult(
            rule = matchedRule,
            closeNode = closeNode,
            fallbackPoint = fallbackPoint,
            matchedKeyword = matchedKeyword
        )
    }

    private fun matchesAnyKeyword(node: NodeSnapshot, keywords: List<String>): Boolean {
        val text = node.searchableText
        val resourceId = node.resourceId.lowercase(Locale.ROOT)
        return keywords.any { keyword ->
            val normalized = keyword.trim().lowercase(Locale.ROOT)
            normalized.isNotEmpty() && (text.contains(normalized) || resourceId.contains(normalized))
        }
    }

    /**
     * 优先精确匹配关闭文案，其次包含匹配，再尝试常见 close resourceId。
     * 如果已识别出弹窗范围，则只在弹窗范围内寻找，避免点击背景页面上的关闭按钮。
     */
    private fun findCloseNode(
        nodes: List<NodeSnapshot>,
        closeKeywords: List<String>,
        popupBounds: Rect?
    ): AccessibilityNodeInfo? {
        var bestNode: AccessibilityNodeInfo? = null
        var bestScore = 0
        val normalizedKeywords = closeKeywords
            .map { it.trim().lowercase(Locale.ROOT) }
            .filter { it.isNotEmpty() }
        val searchBounds = popupBounds?.let { expandedBounds(it) }

        nodes.forEach { snapshot ->
            if (searchBounds != null && !isWithin(snapshot.bounds, searchBounds)) return@forEach

            var score = 0
            val texts = listOf(snapshot.text, snapshot.contentDescription)
                .map { it.trim().lowercase(Locale.ROOT) }
                .filter { it.isNotEmpty() }

            texts.forEach { candidate ->
                normalizedKeywords.forEach { keyword ->
                    score = when {
                        candidate == keyword -> maxOf(score, EXACT_TEXT_SCORE)
                        candidate.contains(keyword) -> maxOf(score, CONTAINS_TEXT_SCORE)
                        else -> score
                    }
                }
            }

            val resourceId = snapshot.resourceId.lowercase(Locale.ROOT)
            if (resourceId.isNotEmpty() && CLOSE_ID_HINTS.any { resourceId.contains(it) }) {
                score = maxOf(score, CLOSE_ID_SCORE)
            }

            if (score > 0) {
                if (runCatching { snapshot.node.isClickable }.getOrDefault(false)) {
                    score += CLICKABLE_BONUS
                }
                if (isReasonableCloseButtonSize(snapshot.bounds)) {
                    score += SIZE_BONUS
                }
                if (score > bestScore) {
                    bestScore = score
                    bestNode = snapshot.node
                }
            }
        }
        return bestNode
    }

    /**
     * 无文字关闭按钮时，在弹窗边缘寻找小而可点击的控件（常见为右上角 ×、底部居中的关闭圆钮）。
     */
    private fun findCloseNodeByGeometry(
        nodes: List<NodeSnapshot>,
        popupBounds: Rect
    ): AccessibilityNodeInfo? {
        val searchBounds = expandedBounds(popupBounds)
        val maxWidth = popupBounds.width() * MAX_GEOMETRY_CANDIDATE_WIDTH_RATIO
        val maxHeight = popupBounds.height() * MAX_GEOMETRY_CANDIDATE_HEIGHT_RATIO
        val maxArea = popupBounds.width().toLong() * popupBounds.height() * MAX_GEOMETRY_CANDIDATE_AREA_RATIO
        val edgeTolerance = minOf(popupBounds.width(), popupBounds.height()) * EDGE_TOLERANCE_RATIO
        val popupCenterX = popupBounds.exactCenterX()

        var bestNode: AccessibilityNodeInfo? = null
        var bestScore = Float.MAX_VALUE

        nodes.forEach { snapshot ->
            if (!isWithin(snapshot.bounds, searchBounds)) return@forEach
            if (!runCatching { snapshot.node.isClickable }.getOrDefault(false)) return@forEach

            val bounds = snapshot.bounds
            if (bounds.width() <= 0 || bounds.height() <= 0) return@forEach
            if (bounds.width() > maxWidth || bounds.height() > maxHeight) return@forEach
            if (bounds.width().toLong() * bounds.height() > maxArea) return@forEach

            val centerX = bounds.exactCenterX()
            val centerY = bounds.exactCenterY()
            val edgeDistance = distanceToPopupEdge(popupBounds, centerX, centerY)
            if (edgeDistance > edgeTolerance) return@forEach

            // 偏好右侧或底部居中的关闭按钮，降低误选左上角帮助 / 菜单控件的概率。
            val horizontalPreference = minOf(
                kotlin.math.abs(centerX - popupBounds.right),
                kotlin.math.abs(centerX - popupCenterX)
            )
            val areaRatio = (bounds.width().toLong() * bounds.height()).toFloat() /
                (popupBounds.width().toLong() * popupBounds.height()).toFloat()

            val score = edgeDistance + horizontalPreference * HORIZONTAL_PREFERENCE_WEIGHT +
                areaRatio * AREA_PREFERENCE_WEIGHT
            if (score < bestScore) {
                bestScore = score
                bestNode = snapshot.node
            }
        }
        return bestNode
    }

    private fun distanceToPopupEdge(bounds: Rect, x: Float, y: Float): Float {
        val outsideX = when {
            x < bounds.left -> bounds.left - x
            x > bounds.right -> x - bounds.right
            else -> 0f
        }
        val outsideY = when {
            y < bounds.top -> bounds.top - y
            y > bounds.bottom -> y - bounds.bottom
            else -> 0f
        }
        if (outsideX > 0f || outsideY > 0f) {
            return hypot(outsideX, outsideY)
        }

        val insideX = minOf(x - bounds.left, bounds.right - x)
        val insideY = minOf(y - bounds.top, bounds.bottom - y)
        return minOf(insideX, insideY)
    }

    private fun findPopupBounds(
        matchedNode: NodeSnapshot,
        screenWidth: Int,
        screenHeight: Int
    ): Rect? {
        if (screenWidth <= 0 || screenHeight <= 0) return null

        var current = runCatching { matchedNode.node.parent }.getOrNull()
        var depth = 0
        var best: Rect? = null

        while (current != null && depth < MAX_ANCESTOR_DEPTH) {
            val node = current ?: break
            val bounds = Rect()
            val success = runCatching { node.getBoundsInScreen(bounds) }.isSuccess
            if (success && isPopupLike(bounds, screenWidth, screenHeight)) {
                // 取最外层且仍小于全屏的容器，避免取到弹窗内部的小标题容器。
                if (best == null || area(bounds) > area(best)) {
                    best = Rect(bounds)
                }
            }
            current = runCatching { node.parent }.getOrNull()
            depth++
        }
        return best
    }

    private fun isPopupLike(bounds: Rect, screenWidth: Int, screenHeight: Int): Boolean {
        val widthRatio = bounds.width().toFloat() / screenWidth
        val heightRatio = bounds.height().toFloat() / screenHeight
        val areaRatio = widthRatio * heightRatio
        val hasHorizontalMargin =
            bounds.left >= screenWidth * MIN_HORIZONTAL_MARGIN_RATIO &&
                bounds.right <= screenWidth * (1f - MIN_HORIZONTAL_MARGIN_RATIO)
        val hasVerticalMargin =
            bounds.top >= screenHeight * MIN_TOP_MARGIN_RATIO &&
                bounds.bottom <= screenHeight * MAX_BOTTOM_RATIO

        return widthRatio in MIN_POPUP_WIDTH_RATIO..MAX_POPUP_WIDTH_RATIO &&
            heightRatio in MIN_POPUP_HEIGHT_RATIO..MAX_POPUP_HEIGHT_RATIO &&
            areaRatio in MIN_POPUP_AREA_RATIO..MAX_POPUP_AREA_RATIO &&
            hasHorizontalMargin &&
            hasVerticalMargin
    }

    private fun expandedBounds(bounds: Rect): Rect {
        val padding = maxOf(MIN_BOUNDS_PADDING_PX, (bounds.height() * BOUNDS_PADDING_RATIO).toInt())
        return Rect(bounds).apply { inset(-padding, -padding) }
    }

    private fun isWithin(child: Rect, parent: Rect): Boolean = Rect.intersects(child, parent)

    private fun isReasonableCloseButtonSize(bounds: Rect): Boolean =
        bounds.width() in 1..MAX_CLOSE_WIDTH && bounds.height() in 1..MAX_CLOSE_HEIGHT

    private fun area(rect: Rect): Long = rect.width().toLong() * rect.height()

    private companion object {
        const val EXACT_TEXT_SCORE = 100
        const val CONTAINS_TEXT_SCORE = 60
        const val CLOSE_ID_SCORE = 45
        const val CLICKABLE_BONUS = 20
        const val SIZE_BONUS = 10
        const val MAX_CLOSE_WIDTH = 720
        const val MAX_CLOSE_HEIGHT = 360

        const val MAX_ANCESTOR_DEPTH = 14
        const val MIN_HORIZONTAL_MARGIN_RATIO = 0.015f
        const val MIN_TOP_MARGIN_RATIO = 0.03f
        const val MAX_BOTTOM_RATIO = 0.985f
        const val MIN_POPUP_WIDTH_RATIO = 0.45f
        const val MAX_POPUP_WIDTH_RATIO = 0.96f
        const val MIN_POPUP_HEIGHT_RATIO = 0.22f
        const val MAX_POPUP_HEIGHT_RATIO = 0.92f
        const val MIN_POPUP_AREA_RATIO = 0.14f
        const val MAX_POPUP_AREA_RATIO = 0.86f

        const val MIN_BOUNDS_PADDING_PX = 40
        const val BOUNDS_PADDING_RATIO = 0.06f

        const val EDGE_TOLERANCE_RATIO = 0.14f
        const val HORIZONTAL_PREFERENCE_WEIGHT = 0.18f
        const val AREA_PREFERENCE_WEIGHT = 800f
        const val MAX_GEOMETRY_CANDIDATE_WIDTH_RATIO = 0.22f
        const val MAX_GEOMETRY_CANDIDATE_HEIGHT_RATIO = 0.18f
        const val MAX_GEOMETRY_CANDIDATE_AREA_RATIO = 0.035f

        const val FALLBACK_X_INSET_RATIO = 0.045f
        const val FALLBACK_Y_INSET_RATIO = 0.015f

        val CLOSE_ID_HINTS = listOf(
            "close", "btn_close", "iv_close", "dialog_close", "dismiss", "skip", "cancel"
        )
    }
}
