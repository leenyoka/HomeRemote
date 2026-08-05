package com.homeremote

import android.content.Context
import android.content.Intent

data class AppInfo(val packageName: String, val label: String)

class AppDiscovery(private val context: Context) {

    fun getInstalledApps(): List<AppInfo> {
        val pm = context.packageManager
        val tvIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory("android.intent.category.LEANBACK_LAUNCHER")
        }
        val results = pm.queryIntentActivities(tvIntent, 0).toMutableList()

        // Also include standard launcher apps as fallback
        if (results.isEmpty()) {
            val mainIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            results.addAll(pm.queryIntentActivities(mainIntent, 0))
        }

        return results
            .filter { it.activityInfo.packageName != context.packageName }
            .map { AppInfo(packageName = it.activityInfo.packageName, label = it.loadLabel(pm).toString()) }
            .distinctBy { it.packageName }
            .sortedBy { it.label }
    }
}
