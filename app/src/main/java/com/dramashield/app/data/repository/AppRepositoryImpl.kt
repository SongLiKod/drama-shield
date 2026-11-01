package com.dramashield.app.data.repository

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import com.dramashield.app.domain.model.AppItem
import com.dramashield.app.domain.repository.AppRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class AppRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : AppRepository {

    override suspend fun getInstalledApps(): List<AppItem> = withContext(Dispatchers.IO) {
        val packageManager = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfos = runCatching {
            packageManager.queryIntentActivities(launcherIntent, 0)
        }.getOrElse { emptyList() }

        val seen = HashSet<String>()
        val apps = ArrayList<AppItem>(resolveInfos.size)

        resolveInfos.forEach { resolveInfo ->
            val packageName = resolveInfo.activityInfo?.packageName ?: return@forEach
            if (packageName == context.packageName) return@forEach
            if (!seen.add(packageName)) return@forEach

            val appInfo = runCatching {
                packageManager.getApplicationInfo(packageName, 0)
            }.getOrNull() ?: return@forEach

            val appName = runCatching {
                packageManager.getApplicationLabel(appInfo).toString()
            }.getOrDefault(packageName)

            val icon: Bitmap? = runCatching {
                appInfo.loadIcon(packageManager).toBitmap(ICON_SIZE, ICON_SIZE)
            }.getOrNull()

            if (appName.isNotBlank()) {
                apps += AppItem(packageName = packageName, appName = appName, icon = icon)
            }
        }

        apps.sortWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.appName })
        apps
    }

    private companion object {
        const val ICON_SIZE = 96
    }
}
