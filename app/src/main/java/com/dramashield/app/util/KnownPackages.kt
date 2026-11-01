package com.dramashield.app.util

/**
 * 已知短剧应用的包名。用于内置规则定向生效与界面友好展示。
 */
object KnownPackages {
    const val HONGGUO_FREE_DRAMA = "com.phoenix.read"

    private val labels = mapOf(
        HONGGUO_FREE_DRAMA to "红果免费短剧"
    )

    fun labelOf(packageName: String): String = labels[packageName] ?: packageName
}
