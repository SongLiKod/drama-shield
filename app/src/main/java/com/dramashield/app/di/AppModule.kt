package com.dramashield.app.di

import android.content.Context
import androidx.room.Room
import com.dramashield.app.data.local.DramaShieldDatabase
import com.dramashield.app.data.local.InterceptLogDao
import com.dramashield.app.data.repository.AppRepositoryImpl
import com.dramashield.app.data.repository.ConfigRepositoryImpl
import com.dramashield.app.data.repository.LogRepositoryImpl
import com.dramashield.app.data.repository.RuleRepositoryImpl
import com.dramashield.app.domain.repository.AppRepository
import com.dramashield.app.domain.repository.ConfigRepository
import com.dramashield.app.domain.repository.LogRepository
import com.dramashield.app.domain.repository.RuleRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindConfigRepository(impl: ConfigRepositoryImpl): ConfigRepository

    @Binds
    @Singleton
    abstract fun bindRuleRepository(impl: RuleRepositoryImpl): RuleRepository

    @Binds
    @Singleton
    abstract fun bindLogRepository(impl: LogRepositoryImpl): LogRepository

    @Binds
    @Singleton
    abstract fun bindAppRepository(impl: AppRepositoryImpl): AppRepository
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): DramaShieldDatabase =
        Room.databaseBuilder(
            context,
            DramaShieldDatabase::class.java,
            "drama_shield.db"
        ).addMigrations(DramaShieldDatabase.MIGRATION_1_2).build()

    @Provides
    fun provideInterceptLogDao(database: DramaShieldDatabase): InterceptLogDao =
        database.interceptLogDao()
}
