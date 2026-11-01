package com.dramashield.app.service.action

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 点击执行器（文档 4.5）：
 * 优先点击关闭节点（含可点击父节点），失败时使用 dispatchGesture 坐标兜底。
 */
class ClickExecutor(
    private val service: AccessibilityService
) {

    suspend fun close(closeNode: AccessibilityNodeInfo?, fallbackPoint: PointF?): Boolean {
        if (closeNode != null) {
            if (clickNodeOrAncestor(closeNode)) return true

            val bounds = Rect()
            runCatching { closeNode.getBoundsInScreen(bounds) }
            if (bounds.width() > 0 && bounds.height() > 0) {
                if (tap(bounds.exactCenterX(), bounds.exactCenterY())) return true
            }
        }
        if (fallbackPoint != null) {
            return tap(fallbackPoint.x, fallbackPoint.y)
        }
        return false
    }

    private fun clickNodeOrAncestor(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth < MAX_PARENT_DEPTH) {
            val clicked = runCatching {
                current?.isClickable == true && current?.performAction(ACTION_CLICK) == true
            }.getOrDefault(false)
            if (clicked) return true

            current = runCatching { current?.parent }.getOrNull()
            depth++
        }
        return false
    }

    private suspend fun tap(x: Float, y: Float): Boolean = withTimeoutOrNull(TAP_TIMEOUT_MS) {
        suspendCancellableCoroutine { continuation ->
            val safeX = x.coerceAtLeast(1f)
            val safeY = y.coerceAtLeast(1f)
            val path = Path().apply { moveTo(safeX, safeY) }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0L, TAP_DURATION_MS))
                .build()

            val callback = object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    if (continuation.isActive) continuation.resume(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    if (continuation.isActive) continuation.resume(false)
                }
            }

            val dispatched = runCatching {
                service.dispatchGesture(gesture, callback, null)
            }.getOrDefault(false)

            if (!dispatched && continuation.isActive) {
                continuation.resume(false)
            }
        }
    } ?: false

    suspend fun swipe(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long = 320L
    ): Boolean = withTimeoutOrNull(SWIPE_TIMEOUT_MS) {
        suspendCancellableCoroutine { continuation ->
            val startXSafe = startX.coerceAtLeast(1f)
            val startYSafe = startY.coerceAtLeast(1f)
            val endXSafe = endX.coerceAtLeast(1f)
            val endYSafe = endY.coerceAtLeast(1f)
            val path = Path().apply {
                moveTo(startXSafe, startYSafe)
                lineTo(endXSafe, endYSafe)
            }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0L, durationMs))
                .build()

            val callback = object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    if (continuation.isActive) continuation.resume(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    if (continuation.isActive) continuation.resume(false)
                }
            }

            val dispatched = runCatching {
                service.dispatchGesture(gesture, callback, null)
            }.getOrDefault(false)

            if (!dispatched && continuation.isActive) {
                continuation.resume(false)
            }
        }
    } ?: false

    private companion object {
        const val MAX_PARENT_DEPTH = 6
        const val TAP_DURATION_MS = 60L
        const val TAP_TIMEOUT_MS = 1_500L
        const val SWIPE_TIMEOUT_MS = 2_000L
    }
}
