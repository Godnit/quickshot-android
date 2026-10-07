package com.example.naffith

interface LocalBrain {
    fun understand(text: String): LocalAction
}

sealed class LocalAction {
    data class OpenApp(val query: String) : LocalAction()
    data class OpenAppAndSearch(val appQuery: String, val searchQuery: String) : LocalAction()
    data class OpenAppAndCalculate(val appQuery: String, val expression: String) : LocalAction()
    data class SearchYoutube(val query: String, val playFirst: Boolean) : LocalAction()
    data class SearchChrome(val query: String, val images: Boolean) : LocalAction()
    data class SearchWeb(val query: String) : LocalAction()
    object OpenFiles : LocalAction()
    object OpenSettings : LocalAction()
    object ListApps : LocalAction()
    object Help : LocalAction()
    data class Unknown(val originalText: String) : LocalAction()
}

/** فهم محلي للأوامر العربية، بلا API أو اتصال بخادم. */
class RuleBasedArabicBrain : LocalBrain {

    override fun understand(text: String): LocalAction {
        val original = text.trim()
        if (original.isEmpty()) return LocalAction.Unknown(original)
        val normalized = normalize(original)

        if (normalized == "مساعده" || normalized.contains("ماذا تستطيع") ||
            normalized == "الاوامر" || normalized.contains("كيف استخدمك")) return LocalAction.Help

        if (normalized.contains("التطبيقات المثبته") || normalized.contains("التطبيقات الموجوده") ||
            normalized.contains("التطبيقات عندي") || normalized.contains("ايش التطبيقات") ||
            normalized.contains("قائمه التطبيقات") || normalized.contains("ما هي التطبيقات") ||
            normalized.contains("ما التطبيقات")) return LocalAction.ListApps

        val isSearch = containsAny(normalized, "ابحث", "بحث", "دور", "فتش")
        val youtube = containsAny(normalized, "يوتيوب", "youtube")
        val chrome = containsAny(normalized, "كروم", "chrome", "المتصفح")
        val mxPlayer = containsAny(normalized, "مشغل ام اكس", "ام اكس", "mx player", "mxplayer")
        val calculator = containsAny(normalized, "الحاسبه", "اله حاسبه", "calculator", "calc")

        if (calculator) {
            val expression = extractExpression(normalized)
            if (expression != null) return LocalAction.OpenAppAndCalculate("الحاسبه", expression)
        }

        if (isSearch && youtube) {
            val query = extractQuery(normalized, commonSearchWords + setOf("اغنيه", "اغنية", "شغل", "شغله", "شغلها", "تشغيل", "و"))
            if (query.isNotBlank()) {
                val playFirst = containsAny(normalized, "شغل", "شغله", "شغلها", "تشغيل")
                return LocalAction.SearchYoutube(query, playFirst)
            }
        }
        if (isSearch && chrome) {
            val images = containsAny(normalized, "صور", "صوره", "صورة", "صورًا")
            val query = extractQuery(normalized, commonSearchWords + setOf(
                "صور", "صوره", "صورة", "في", "و", "حمل", "تحميل", "نزل", "نزّل", "عشوائيه", "عشوائي", "الصوره"
            ))
            if (query.isNotBlank()) return LocalAction.SearchChrome(query, images)
        }
        if (isSearch && mxPlayer) {
            val query = extractQuery(normalized, commonSearchWords + setOf("مشغل", "ام", "اكس", "mx", "player", "mxplayer", "شغل", "شغله", "شغلها", "و"))
            if (query.isNotBlank()) return LocalAction.OpenAppAndSearch("ام اكس", query)
        }
        if (isSearch && containsAny(normalized, "جوجل", "google", "الويب", "الانترنت")) {
            val query = extractQuery(normalized, commonSearchWords + setOf("جوجل", "google", "الويب", "الانترنت", "و"))
            if (query.isNotBlank()) return LocalAction.SearchWeb(query)
        }

        if (containsAny(normalized, "اداره الملفات", "مدير الملفات", "الملفات", "ملفاتي", "file manager", "files", "التنزيلات")) return LocalAction.OpenFiles
        if (containsAny(normalized, "الاعدادات", "الضبط", "settings")) return LocalAction.OpenSettings

        if (containsAny(normalized, "يوتيوب", "youtube")) return LocalAction.OpenApp("يوتيوب")
        if (containsAny(normalized, "كروم", "chrome", "المتصفح")) return LocalAction.OpenApp("كروم")
        if (containsAny(normalized, "واتساب", "whatsapp")) return LocalAction.OpenApp("واتساب")
        if (containsAny(normalized, "الاستوديو", "الصور", "المعرض", "gallery")) return LocalAction.OpenApp("الصور")
        if (containsAny(normalized, "الكاميرا", "camera")) return LocalAction.OpenApp("الكاميرا")
        if (mxPlayer) return LocalAction.OpenApp("ام اكس")
        if (calculator) return LocalAction.OpenApp("الحاسبه")

        if (containsAny(normalized, "افتح", "شغل", "شغل لي", "ابدأ")) {
            val query = extractQuery(normalized, setOf("افتح", "فتح", "تطبيق", "التطبيق", "شغل", "شغل لي", "ابدأ", "لي"))
            if (query.isNotBlank()) return LocalAction.OpenApp(query)
        }

        return LocalAction.Unknown(original)
    }

    private val commonSearchWords = setOf("افتح", "ابحث", "لي", "عن", "بحث", "دور", "فتش", "في")

    private fun extractQuery(normalized: String, ignoredWords: Set<String>): String = normalized
        .split(" ")
        .map { it.trim('،', ',', '.', '؟', '?', '!') }
        .filter { it.isNotBlank() && !isIgnoredToken(it, ignoredWords) }
        .joinToString(" ")

    private fun isIgnoredToken(token: String, ignoredWords: Set<String>): Boolean {
        if (token in ignoredWords) return true
        // الكتابة العربية تلصق الواو أو الباء بالفعل: «وأبحث»، «وحمل».
        return (token.startsWith("و") || token.startsWith("ب")) && token.drop(1) in ignoredWords
    }

    private fun extractExpression(normalized: String): String? {
        return Regex("[0-9]+(?:\\s*[+\\-*/]\\s*[0-9]+)+")
            .find(normalized)?.value?.replace(" ", "")
    }

    private fun containsAny(text: String, vararg values: String): Boolean = values.any { text.contains(it) }

    private fun normalize(value: String): String = value
        .lowercase()
        .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
        .replace('ى', 'ي').replace('ة', 'ه')
        .replace('٠', '0').replace('١', '1').replace('٢', '2').replace('٣', '3').replace('٤', '4')
        .replace('٥', '5').replace('٦', '6').replace('٧', '7').replace('٨', '8').replace('٩', '9')
        .replace(Regex("[ًٌٍَُِّْـ]"), "")
        .replace(Regex("\\s+"), " ").trim()
}
