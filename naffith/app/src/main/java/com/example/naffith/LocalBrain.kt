package com.example.naffith

/**
 * واجهة العقل المحلي. نستبدل RuleBasedArabicBrain بنموذج محلي لاحقًا
 * دون تغيير واجهة التطبيق أو طبقة تنفيذ الأوامر.
 */
interface LocalBrain {
    fun understand(text: String): LocalAction
}

sealed class LocalAction {
    data class OpenApp(val packageName: String, val label: String) : LocalAction()
    data class SearchYoutube(val query: String) : LocalAction()
    object OpenFiles : LocalAction()
    object OpenSettings : LocalAction()
    object Help : LocalAction()
    data class Unknown(val originalText: String) : LocalAction()
}

/**
 * فهم محلي خفيف للأوامر العربية. لا يحتاج إلى إنترنت أو API.
 */
class RuleBasedArabicBrain : LocalBrain {

    override fun understand(text: String): LocalAction {
        val original = text.trim()
        if (original.isEmpty()) return LocalAction.Unknown(original)

        val normalized = normalize(original)

        if (normalized == "مساعده" || normalized.contains("ماذا تستطيع") || normalized == "الاوامر") {
            return LocalAction.Help
        }

        val youtubeWords = listOf("يوتيوب", "youtube")
        val searchWords = listOf("ابحث", "بحث", "دور", "ابحث لي", "ابحث عن")
        if (youtubeWords.any { normalized.contains(it) } &&
            searchWords.any { normalized.contains(it) }) {
            val query = extractYoutubeQuery(normalized)
            if (query.isNotBlank()) return LocalAction.SearchYoutube(query)
        }

        if (containsAny(normalized, "يوتيوب", "youtube")) {
            return LocalAction.OpenApp("com.google.android.youtube", "يوتيوب")
        }
        if (containsAny(normalized, "كروم", "chrome", "المتصفح")) {
            return LocalAction.OpenApp("com.android.chrome", "Chrome")
        }
        if (containsAny(normalized, "واتساب", "whatsapp")) {
            return LocalAction.OpenApp("com.whatsapp", "واتساب")
        }
        if (containsAny(normalized, "الاستوديو", "الصور", "المعرض", "gallery")) {
            return LocalAction.OpenApp("com.google.android.apps.photos", "الصور")
        }
        if (containsAny(normalized, "الملفات", "ملفاتي", "file manager", "التنزيلات")) {
            return LocalAction.OpenFiles
        }
        if (containsAny(normalized, "الاعدادات", "الضبط", "settings")) {
            return LocalAction.OpenSettings
        }

        return LocalAction.Unknown(original)
    }

    private fun extractYoutubeQuery(normalized: String): String {
        val ignoredWords = setOf("افتح", "يوتيوب", "youtube", "ابحث", "لي", "عن", "بحث", "دور", "في")
        return normalized
            .split(" ")
            .filter { it.isNotBlank() && it !in ignoredWords }
            .joinToString(" ")
    }

    private fun containsAny(text: String, vararg values: String): Boolean =
        values.any { text.contains(it) }

    private fun normalize(value: String): String {
        return value
            .lowercase()
            .replace('أ', 'ا')
            .replace('إ', 'ا')
            .replace('آ', 'ا')
            .replace('ى', 'ي')
            .replace('ة', 'ه')
            .replace(Regex("[ًٌٍَُِّْـ]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
