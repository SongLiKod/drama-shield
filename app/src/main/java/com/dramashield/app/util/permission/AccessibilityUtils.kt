package com.dramashield.app.util.permission

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import com.dramashield.app.service.DramaShieldAccessibilityService

/**
 * 无障碍权限检测与引导（文档 8）。
 */
object AccessibilityUtils {

    fun isServiceEnabled(
        context: Context,
        serviceClass: Class<*> = DramaShieldAccessibilityService::class.java
    ): Boolean {
        val expected = ComponentName(context, serviceClass)

        val enabledFromSettings = runCatching {
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
        }.getOrNull()

        val matchedInSettings = enabledFromSettings
            ?.split(':')
            ?.filter { it.isNotBlank() }
            ?.any { flattened ->
                val component = ComponentName.unflattenFromString(flattened)
                component != null &&
                    component.packageName == expected.packageName &&
                    component.className == expected.className
            } ?: false

        if (matchedInSettings) return true

        val manager = runCatching {
            context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        }.getOrNull() ?: return false

        return runCatching {
            manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .any { info ->
                    val serviceInfo = info.resolveInfo?.serviceInfo ?: return@any false
                    serviceInfo.packageName == expected.packageName &&
                        serviceInfo.name == expected.className
                }
        }.getOrDefault(false)
    }

    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val launched = runCatching { context.startActivity(intent) }.isSuccess
        if (!launched) {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }
}
