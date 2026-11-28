package com.infinity.core.advanced.data.capabilities

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import com.infinity.core.advanced.domain.capabilities.InfinityApplicationLauncherCapability
import com.infinity.core.advanced.domain.capabilities.LaunchCapabilityResult

/**
 * Reuses Android's installed launcher entry points. It does not enumerate,
 * install, or maintain a second application catalog.
 */
internal class InfinityAndroidApplicationLauncherAdapter(
    context: Context
) : InfinityApplicationLauncherCapability {
    private val appContext = context.applicationContext

    override suspend fun launch(packageName: String): LaunchCapabilityResult {
        if (!infinityIsValidLauncherPackageName(packageName)) {
            return LaunchCapabilityResult.Failed("Invalid package name")
        }

        return try {
            val launchIntent = appContext.packageManager
                .getLaunchIntentForPackage(packageName)
                ?: return LaunchCapabilityResult.AppNotFound

            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(launchIntent)
            LaunchCapabilityResult.Launched
        } catch (_: ActivityNotFoundException) {
            LaunchCapabilityResult.AppNotFound
        } catch (_: SecurityException) {
            LaunchCapabilityResult.Failed("Android denied application launch")
        } catch (_: IllegalStateException) {
            LaunchCapabilityResult.Failed("Application launch is unavailable")
        }
    }
}

internal fun infinityIsValidLauncherPackageName(packageName: String): Boolean {
    if (packageName.length !in 3..255) return false
    val segments = packageName.split('.')
    return segments.size >= 2 && segments.all { segment ->
        segment.isNotEmpty() &&
            segment.first().isLetter() &&
            segment.all { it.isLetterOrDigit() || it == '_' }
    }
}
