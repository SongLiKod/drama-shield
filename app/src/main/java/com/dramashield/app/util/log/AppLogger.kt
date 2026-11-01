package com.dramashield.app.util.log

import android.util.Log

/**
 * 统一日志工具，避免服务内散落 Tag，同时保证异常场景不会向用户界面抛出。
 */
object AppLogger {
    private const val TAG = "DramaShield"

    fun d(message: String) {
        runCatching { Log.d(TAG, message) }
    }

    fun e(message: String, throwable: Throwable? = null) {
        runCatching { Log.e(TAG, message, throwable) }
    }
}
