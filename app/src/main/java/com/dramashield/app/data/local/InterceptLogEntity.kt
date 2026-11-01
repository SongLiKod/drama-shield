package com.dramashield.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 文档 5.2 拦截日志表。
 */
@Entity(
    tableName = "intercept_log",
    indices = [Index("timestamp"), Index("ruleType")]
)
data class InterceptLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val timestamp: Long,
    val packageName: String,
    val appName: String,
    val ruleType: String,
    val ruleId: String,
    val isSuccess: Boolean,
    val detail: String = ""
)

data class RuleHitCountEntity(
    val ruleId: String,
    val count: Int
)
