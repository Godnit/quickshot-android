package com.example.naffith

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.MediaStore

data class InstalledApp(val packageName: String, val label: String, val intent: Intent)

class AppCatalog(private val context: Context) {
    private val pm = context.packageManager

    @Suppress("DEPRECATION")
    fun all(): List<InstalledApp> {
        val apps = LinkedHashMap<String, InstalledApp>()
        fun addHandlers(intent: Intent) {
            pm.queryIntentActivities(intent, 0).forEach { info ->
                val activity = info.activityInfo ?: return@forEach
                if (!activity.enabled || !activity.exported) return@forEach
                val launch = Intent(intent).setComponent(ComponentName(activity.packageName, activity.name))
                val label = info.loadLabel(pm).toString().trim()
                apps.putIfAbsent(activity.packageName, InstalledApp(activity.packageName, label, launch))
            }
        }
        addHandlers(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER))
        pm.getInstalledApplications(0).forEach { info ->
            if (info.enabled && !apps.containsKey(info.packageName)) {
                val launch = pm.getLaunchIntentForPackage(info.packageName) ?: return@forEach
                apps[info.packageName] = InstalledApp(info.packageName, info.loadLabel(pm).toString().trim(), launch)
            }
        }
        // تضاف تطبيقات النظام حتى إذا كانت بلا اختصار Launcher عادي.
        addHandlers(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
        addHandlers(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALCULATOR))
        addHandlers(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_GALLERY))
        addHandlers(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"))
        return apps.values.sortedBy { ArabicText.normalize(it.label) }
    }

    fun find(query: String): InstalledApp? {
        val apps = all()
        val match = AppNameMatcher.find(query, apps.map { AppName(it.packageName, it.label) }) ?: return null
        return apps.firstOrNull { it.packageName == match.packageName }
    }
}
