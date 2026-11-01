package com.dramashield.app.data.local

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface InterceptLogDao {

    @Insert
    suspend fun insert(entity: InterceptLogEntity): Long

    /**
     * 分页查询；type 为 null 时查询全部（含系统异常日志）。
     */
    @Query(
        """
        SELECT * FROM intercept_log
        WHERE (:ruleType IS NULL OR ruleType = :ruleType)
        ORDER BY timestamp DESC, id DESC
        """
    )
    fun pagingSource(ruleType: String?): PagingSource<Int, InterceptLogEntity>

    @Query("SELECT COUNT(*) FROM intercept_log WHERE isSuccess = 1 AND timestamp >= :startOfDay")
    fun observeSuccessCountSince(startOfDay: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM intercept_log WHERE isSuccess = 1")
    fun observeTotalSuccessCount(): Flow<Int>

    @Query("SELECT ruleId, COUNT(*) AS count FROM intercept_log GROUP BY ruleId")
    fun observeRuleHitCounts(): Flow<List<RuleHitCountEntity>>

    @Query("SELECT * FROM intercept_log ORDER BY timestamp DESC, id DESC")
    suspend fun getAll(): List<InterceptLogEntity>

    @Query("DELETE FROM intercept_log")
    suspend fun clear()
}
