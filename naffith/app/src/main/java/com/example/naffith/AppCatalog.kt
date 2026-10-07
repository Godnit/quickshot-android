package com.example.naffith

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

data class InstalledApp(val packageName: String, val label: String)

/** يبحث في التطبيقات التي يملك الهاتف اختصار تشغيل لها. */
class AppCatalog(private val context: Context) {
    private val packageManager: PackageManager = context.packageManager

    fun all(): List<InstalledApp> {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
            .mapNotNull { info ->
                val packageName = info.activityInfo?.packageName ?: return@mapNotNull null
                val label = info.loadLabel(packageManager)?.toString()?.trim().orEmpty()
                if (label.isBlank()) null else InstalledApp(packageName, label)
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    fun find(query: String): InstalledApp? {
        val normalizedQuery = normalize(query)
        if (normalizedQuery.isBlank()) return null
        val apps = all()

        val aliases = mapOf(
            "يوتيوب" to listOf("youtube", "يوتيوب"),
            "كروم" to listOf("chrome", "كروم", "google chrome"),
            "واتساب" to listOf("whatsapp", "واتساب"),
            "ام اكس" to listOf("mx player", "mxplayer", "ام اكس"),
            "الصور" to listOf("photos", "gallery", "الصور", "المعرض"),
            "لقطه شاشه" to listOf("screenshot", "quickshot", "لقطة شاشة", "لقطه شاشه")
        )
        val candidates = aliases[normalizedQuery] ?: listOf(normalizedQuery)

        return apps.firstOrNull { app -> candidates.any { normalize(app.label) == normalize(it) } }
            ?: apps.firstOrNull { app -> candidates.any { normalize(app.label).contains(normalize(it)) } }
            ?: apps.firstOrNull { normalize(app.label).contains(normalizedQuery) || normalizedQuery.contains(normalize(app.label)) }
    }

    private fun normalize(value: String): String = value.lowercase()
        .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
        .replace('ى', 'ي').replace('ة', 'ه')
        .replace(Regex("[ًٌٍَُِّْـ]"), "")
        .replace(Regex("\\s+"), " ").trim()
}
