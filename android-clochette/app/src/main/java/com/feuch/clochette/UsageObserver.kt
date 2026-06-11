package com.feuch.clochette

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process

class UsageObserver(private val context: Context) {
    private val usageStatsManager =
        context.applicationContext.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private val packageManager = context.applicationContext.packageManager

    fun hasPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun snapshot(windowMs: Long = 10 * 60 * 1000L): ActivitySnapshot {
        if (!hasPermission()) return ActivitySnapshot()

        val now = System.currentTimeMillis()
        val events = usageStatsManager.queryEvents(now - windowMs, now)
        val event = UsageEvents.Event()
        var foregroundPackage: String? = null
        var foregroundSince = now
        var switches = 0
        var lastForeground: String? = null

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                if (lastForeground != null && lastForeground != event.packageName) switches += 1
                lastForeground = event.packageName
                foregroundPackage = event.packageName
                foregroundSince = event.timeStamp
            }
        }

        return ActivitySnapshot(
            foregroundPackage = foregroundPackage,
            foregroundDisplayName = foregroundPackage?.toDisplayName(),
            recentSwitchCount = switches,
            approximateDurationMs = (now - foregroundSince).coerceAtLeast(0),
        )
    }

    private fun String.toDisplayName(): String = runCatching {
        val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getApplicationInfo(this, PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getApplicationInfo(this, 0)
        }
        packageManager.getApplicationLabel(appInfo).toString()
    }.getOrElse { this }
}
