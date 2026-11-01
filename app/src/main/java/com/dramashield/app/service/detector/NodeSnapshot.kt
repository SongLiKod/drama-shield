package com.dramashield.app.service.detector

import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale

/**
 * 一次视图遍历中节点的快照，避免检测过程中反复跨进程读取属性。
 */
data class NodeSnapshot(
    val node: AccessibilityNodeInfo,
    val text: String,
    val contentDescription: String,
    val resourceId: String,
    val className: String,
    val bounds: Rect,
    val isVisible: Boolean,
    val isScrollable: Boolean
) {
    val searchableText: String =
        listOf(text, contentDescription)
            .filter { it.isNotBlank() }
            .joinToString(separator = " ")
            .lowercase(Locale.ROOT)
}

/**
 * 遍历视图节点，读取文本、resourceId 与层级（文档 4.3 第 3 步）。
 * 限制节点数量与深度，防止异常页面导致遍历失控。
 */
object NodeScanner {
    private const val MAX_NODES = 1500
    private const val MAX_DEPTH = 50

    @Suppress("DEPRECATION")
    fun scan(root: AccessibilityNodeInfo): List<NodeSnapshot> {
        val snapshots = ArrayList<NodeSnapshot>(128)
        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        queue.addLast(root to 0)

        while (queue.isNotEmpty() && snapshots.size < MAX_NODES) {
            val (node, depth) = queue.removeFirst()
            val snapshot = runCatching { node.toSnapshot() }.getOrNull()
            if (snapshot != null) snapshots.add(snapshot)

            if (depth >= MAX_DEPTH) continue
            val childCount = runCatching { node.childCount }.getOrDefault(0)
            for (index in 0 until childCount) {
                if (snapshots.size + queue.size >= MAX_NODES) break
                val child = runCatching { node.getChild(index) }.getOrNull() ?: continue
                queue.addLast(child to depth + 1)
            }
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            while (queue.isNotEmpty()) {
                runCatching { queue.removeFirst().first.recycle() }
            }
        }
        return snapshots
    }

    private fun AccessibilityNodeInfo.toSnapshot(): NodeSnapshot {
        val bounds = Rect()
        getBoundsInScreen(bounds)
        return NodeSnapshot(
            node = this,
            text = text?.toString()?.trim().orEmpty(),
            contentDescription = contentDescription?.toString()?.trim().orEmpty(),
            resourceId = viewIdResourceName.orEmpty(),
            className = className?.toString().orEmpty(),
            bounds = bounds,
            isVisible = runCatching { isVisibleToUser }.getOrDefault(true),
            isScrollable = runCatching { isScrollable }.getOrDefault(false)
        )
    }
}
