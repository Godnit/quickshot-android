package com.example.naffith

/** واجهة فهم الأوامر. تعمل النسخة الحالية محليًا بلا API. */
interface LocalBrain {
    fun understand(text: String): LocalAction
}

sealed class LocalAction {
    data class OpenApp(val query: String) : LocalAction()
    data class OpenAppAndSearch(val appQuery: String, val searchQuery: String) : LocalAction()
    data class SearchYoutube(val query: String) : LocalAction()
    data class SearchChrome(val query: String) : LocalAction()
    data class SearchWeb(val query: String) : LocalAction()
    object OpenFiles : LocalAction()
    object OpenSettings : LocalAction()
    object ListApps : LocalAction()
    object Help : LocalAction()
    data class Unknown(val originalText: String) : LocalAction()
}

/**
 * عقل محلي صغير لفهم صيغ عربية شائعة. لا يحتاج إلى إنترنت.
 * إذا لم يجد تطبيقًا معروفًا، يعيد اسم التطبيق إلى AppCatalog للبحث في التطبيقات المثبتة.
 */
class RuleBasedArabicBrain : LocalBrain {

    override fun understand(text: String): LocalAction {
        val original = text.trim()
        if (original.isEmpty()) return LocalAction.Unknown(original)
        val normalized = normalize(original)

        if (normalized == "مساعده" || normalized.contains("ماذا تستطيع") ||
            normalized == "الاوامر" || normalized.contains("كيف استخدمك")) {
            return LocalAction.Help
        }
        if (normalized.contains("التطبيقات المثبته") || normalized.contains("التطبيقات الموجوده") ||
            normalized.contains("قائمه التطبيقات") || normalized.contains("ما هي التطبيقات")) {
            return LocalAction.ListApps
        }

        val isSearch = containsAny(normalized, "ابحث", "بحث", "دور", "فتش")
        val youtube = containsAny(normalized, "يوتيوب", "youtube")
        val chrome = containsAny(normalized, "كروم", "chrome", "المتصفح")
        val mxPlayer = containsAny(normalized, "مشغل ام اكس", "ام اكس", "mx player", "mxplayer")

        if (isSearch && youtube) {
            val query = extractQuery(normalized, setOf("افتح", "يوتيوب", "youtube", "ابحث", "لي", "عن", "بحث", "دور", "في"))
            if (query.isNotBlank()) return LocalAction.SearchYoutube(query)
        }
        if (isSearch && chrome) {
            val query = extractQuery(normalized, setOf("افتح", "كروم", "chrome", "المتصفح", "ابحث", "لي", "عن", "بحث", "دور", "في"))
            if (query.isNotBlank()) return LocalAction.SearchChrome(query)
        }
        if (isSearch && mxPlayer) {
            val query = extractQuery(normalized, setOf("افتح", "مشغل", "ام", "اكس", "mx", "player", "mxplayer", "ابحث", "لي", "عن", "بحث", "دور", "في"))
            if (query.isNotBlank()) return LocalAction.OpenAppAndSearch("ام اكس", query)
        }
        if (isSearch && containsAny(normalized, "جوجل", "google", "الويب", "الانترنت")) {
            val query = extractQuery(normalized, setOf("افتح", "جوجل", "google", "الويب", "الانترنت", "ابحث", "لي", "عن", "بحث", "دور", "في"))
            if (query.isNotBlank()) return LocalAction.SearchWeb(query)
        }

        if (containsAny(normalized, "الملفات", "ملفاتي", "file manager", "التنزيلات")) return LocalAction.OpenFiles
        if (containsAny(normalized, "الاعدادات", "الضبط", "settings")) return LocalAction.OpenSettings

        if (containsAny(normalized, "يوتيوب", "youtube")) return LocalAction.OpenApp("يوتيوب")
        if (containsAny(normalized, "كروم", "chrome", "المتصفح")) return LocalAction.OpenApp("كروم")
        if (containsAny(normalized, "واتساب", "whatsapp")) return LocalAction.OpenApp("واتساب")
        if (containsAny(normalized, "الاستوديو", "الصور", "المعرض", "gallery")) return LocalAction.OpenApp("الصور")
        if (mxPlayer) return LocalAction.OpenApp("ام اكس")

        // مثل: «افتح تطبيق لقطة شاشة» أو «شغل مشغل الموسيقى».
        if (containsAny(normalized, "افتح", "شغل", "شغل لي", "ابدأ")) {
            val query = extractQuery(normalized, setOf("افتح", "فتح", "تطبيق", "التطبيق", "شغل", "شغل لي", "ابدأ", "لي"))
            if (query.isNotBlank()) return LocalAction.OpenApp(query)
        }

        return LocalAction.Unknown(original)
    }

    private fun extractQuery(normalized: String, ignoredWords: Set<String>): String = normalized
        .split(" ")
        .filter { it.isNotBlank() && it !in ignoredWords }
        .joinToString(" ")

    private fun containsAny(text: String, vararg values: String): Boolean = values.any { text.contains(it) }

    fun normalize(value: String): String = value
        .lowercase()
        .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
        .replace('ى', 'ي').replace('ة', 'ه')
        .replace(Regex("[ًٌٍَُِّْـ]"), "")
        .replace(Regex("\\s+"), " ").trim()
}
