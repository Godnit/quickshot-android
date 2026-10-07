package com.example.naffith

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

data class InstalledApp(val packageName: String, val label: String)

/** يقرأ التطبيقات القابلة للتشغيل، مع أسماء بديلة عربية للتطبيقات الشائعة. */
class AppCatalog(private val context: Context) {
    private val packageManager: PackageManager = context.packageManager

    fun all(): List<InstalledApp> {
        val result = LinkedHashMap<String, InstalledApp>()
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        packageManager.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL).forEach { info ->
            val packageName = info.activityInfo?.packageName ?: return@forEach
            val label = info.loadLabel(packageManager)?.toString()?.trim().orEmpty()
            if (label.isNotBlank()) result[packageName] = InstalledApp(packageName, label)
        }

        // بعض الأجهزة لا تعرض مدير الملفات أو الكاميرا في قائمة الاختصارات،
        // لذلك نضيف كل حزمة تملك Intent تشغيلًا أيضًا.
        packageManager.getInstalledApplications(PackageManager.GET_META_DATA).forEach { info ->
            val packageName = info.packageName ?: return@forEach
            if (result.containsKey(packageName)) return@forEach
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName) ?: return@forEach
            if (launchIntent.component?.packageName != packageName) return@forEach
            val label = info.loadLabel(packageManager)?.toString()?.trim().orEmpty()
            if (label.isNotBlank()) result[packageName] = InstalledApp(packageName, label)
        }
        return result.values.sortedBy { normalize(it.label) }
    }

    fun find(query: String): InstalledApp? {
        val normalizedQuery = normalize(query)
        if (normalizedQuery.isBlank()) return null
        val aliases = mapOf(
            "يوتيوب" to listOf("youtube", "يوتيوب"),
            "كروم" to listOf("chrome", "كروم", "google chrome"),
            "واتساب" to listOf("whatsapp", "واتساب", "whatsapp business"),
            "ام اكس" to listOf("mx player", "mxplayer", "ام اكس", "mx"),
            "الصور" to listOf("photos", "gallery", "الصور", "المعرض", "صور"),
            "لقطه شاشه" to listOf("screenshot", "quickshot", "لقطة شاشة", "لقطه شاشه"),
            "اداره الملفات" to listOf("file manager", "files", "my files", "file", "ادارة الملفات", "مدير الملفات", "الملفات", "ملفاتي"),
            "الكاميرا" to listOf("camera", "كاميرا", "الكاميرا"),
            "الحاسبه" to listOf("calculator", "calc", "الحاسبة", "الآلة الحاسبة", "اله حاسبه"),
            "موسيقي play" to listOf("play music", "youtube music", "music", "موسيقى play", "موسيقي play")
        )
        val candidates = aliases[normalizedQuery] ?: listOf(normalizedQuery)
        val normalizedCandidates = candidates.map(::normalize)
        val apps = all()

        return apps.firstOrNull { app -> normalizedCandidates.any { normalize(app.label) == it } }
            ?: apps.firstOrNull { app -> normalizedCandidates.any { normalize(app.label).contains(it) } }
            ?: apps.firstOrNull { app -> normalize(app.label).contains(normalizedQuery) || normalizedQuery.contains(normalize(app.label)) }
    }

    private fun normalize(value: String): String = value.lowercase()
        .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
        .replace('ى', 'ي').replace('ة', 'ه')
        .replace(Regex("[ًٌٍَُِّْـ]"), "")
        .replace(Regex("\\s+"), " ").trim()
}
