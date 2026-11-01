package com.dramashield.app.domain.repository

import androidx.paging.PagingData
import com.dramashield.app.domain.model.InterceptLog
import com.dramashield.app.domain.model.RuleType
import kotlinx.coroutines.flow.Flow

interface LogRepository {
    fun pagedLogs(type: RuleType?): Flow<PagingData<InterceptLog>>

    val todaySuccessCount: Flow<Int>
    val totalSuccessCount: Flow<Int>
    val ruleHitCounts: Flow<Map<String, Int>>

    suspend fun insert(log: InterceptLog): Long

    suspend fun clear()

    suspend fun exportLogsJson(): String
}
