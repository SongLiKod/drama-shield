package com.dramashield.app.domain.model

import android.graphics.Bitmap

/**
 * 白名单可选应用。
 */
data class AppItem(
    val packageName: String,
    val appName: String,
    val icon: Bitmap? = null
)
