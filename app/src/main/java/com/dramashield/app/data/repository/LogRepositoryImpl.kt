package com.dramashield.app.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.dramashield.app.data.local.InterceptLogDao
import com.dramashield.app.data.local.InterceptLogEntity
import com.dramashield.app.domain.model.InterceptLog
import com.dramashield.app.domain.model.RuleType
import com.dramashield.app.domain.repository.LogRepository
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class LogRepositoryImpl @Inject constructor(
    private val dao: InterceptLogDao
) : LogRepository {

    override fun pagedLogs(type: RuleType?): Flow<PagingData<InterceptLog>> =
        Pager(
            config = PagingConfig(
                pageSize = PAGE_SIZE,
                initialLoadSize = PAGE_SIZE * 2,
                prefetchDistance = 8,
                enablePlaceholders = false
            ),
            pagingSourceFactory = { dao.pagingSource(type?.value) }
        ).flow.map { pagingData -> pagingData.map { it.toDomain() } }

    override val todaySuccessCount: Flow<Int> = flow {
        while (true) {
            emit(startOfToday())
            delay(60_000L)
        }
    }
        .flatMapLatest { dao.observeSuccessCountSince(it) }
        .distinctUntilChanged()

    override val totalSuccessCount: Flow<Int> = dao.observeTotalSuccessCount()

    override val ruleHitCounts: Flow<Map<String, Int>> = dao.observeRuleHitCounts()
        .map { list -> list.associate { it.ruleId to it.count } }

    override suspend fun insert(log: InterceptLog): Long = dao.insert(log.toEntity())

    override suspend fun clear() = dao.clear()

    override suspend fun exportLogsJson(): String {
        val array = JSONArray()
        dao.getAll().forEach { entity ->
            array.put(
                JSONObject().apply {
                    put("id", entity.id)
                    put("timestamp", entity.timestamp)
                    put("packageName", entity.packageName)
                    put("appName", entity.appName)
                    put("ruleType", entity.ruleType)
                    put("ruleId", entity.ruleId)
                    put("isSuccess", entity.isSuccess)
                    put("detail", entity.detail)
                }
            )
        }
        return array.toString(2)
    }

    private fun startOfToday(): Long =
        LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun InterceptLogEntity.toDomain(): InterceptLog = InterceptLog(
        id = id,
        timestamp = timestamp,
        packageName = packageName,
        appName = appName,
        ruleType = RuleType.fromValue(ruleType),
        ruleId = ruleId,
        isSuccess = isSuccess,
        detail = detail
    )

    private fun InterceptLog.toEntity(): InterceptLogEntity = InterceptLogEntity(
        id = id,
        timestamp = timestamp,
        packageName = packageName,
        appName = appName,
        ruleType = ruleType.value,
        ruleId = ruleId,
        isSuccess = isSuccess,
        detail = detail
    )

    companion object {
        private const val PAGE_SIZE = 20
    }
}
