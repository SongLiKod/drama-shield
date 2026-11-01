package com.dramashield.app.domain.repository

import com.dramashield.app.domain.model.AppItem

interface AppRepository {
    suspend fun getInstalledApps(): List<AppItem>
}
