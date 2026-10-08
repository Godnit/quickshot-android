package com.example.naffith

interface LocalBrain { fun understand(text: String): LocalAction }

sealed class LocalAction {
    data class OpenApp(val query: String) : LocalAction()
    data class OpenAppAndSearch(val appQuery: String, val searchQuery: String, val playFirst: Boolean = false) : LocalAction()
    data class OpenAppAndCalculate(val appQuery: String, val expression: String) : LocalAction()
    data class SearchYoutube(val query: String, val playFirst: Boolean) : LocalAction()
    data class SearchChrome(
        val query: String,
        val images: Boolean,
        val downloadImage: Boolean = false,
        val downloadCount: Int = 0
    ) : LocalAction()
    data class SearchWeb(val query: String) : LocalAction()
    /** بحث في التطبيق الظاهر حاليًا، ويُستخدم خصوصًا لمدير الملفات. */
    data class SearchCurrent(val query: String, val playFirst: Boolean = false) : LocalAction()
    data class Write(val appQuery: String?, val text: String, val submit: Boolean) : LocalAction()
    data class Click(val label: String) : LocalAction()
    data class OpenFolder(val folder: String) : LocalAction()
    object OpenFiles : LocalAction()
    object OpenSettings : LocalAction()
    object ListApps : LocalAction()
    object ReadScreen : LocalAction()
    object Back : LocalAction()
    object Home : LocalAction()
    object Stop : LocalAction()
    object Help : LocalAction()
    data class Unknown(val originalText: String) : LocalAction()
}

/** محلل محلي صغير للأوامر؛ لا يستخدم نموذجًا لغويًا أو API. */
class RuleBasedArabicBrain : LocalBrain {
    private val appWords = listOf(
        "يوتيوب", "يوتيوب ميوزك", "youtube", "كروم", "chrome", "المتصفح",
        "ام اكس", "ام اكس بلاير", "ام اكس بليير", "مشغل ام اكس", "ام اكس", "mx player", "mxplayer",
        "الحاسبه", "الاله الحاسبه", "اله حاسبه", "الحاسبه", "calculator", "calc",
        "اداره الملفات", "مدير الملفات", "الملفات", "ملفاتي", "files", "file manager",
        "الكاميرا", "كاميرا", "camera", "المعرض", "الصور", "gallery", "photos",
        "جوجل", "قوقل", "google", "متجر بلاي", "متجر play", "جوجل بلاي", "google play", "play store",
        "موسيقى بلاي", "موسيقى play", "play music", "youtube music", "يوتيوب ميوزك",
        "تليجرام", "تلجرام", "تيليجرام", "telegram", "ماسنجر", "messenger", "فيسبوك", "facebook",
        "انستقرام", "انستغرام", "instagram", "تيك توك", "تيكتوك", "tiktok", "الرسائل", "messages",
        "جهات الاتصال", "الأسماء", "اسماء", "contacts", "الهاتف", "phone", "البريد الالكتروني", "جيميل", "gmail",
        "الساعة", "clock", "مسجل الصوت", "مسجل صوتي", "voice recorder", "الراديو", "راديو fm", "fm radio",
        "السيارة", "سياره", "لعبة السيارة", "لعبه السياره", "لعبة سيارات", "لعبه سيارات",
        "سباق السيارات", "hill climb", "hill climb racing", "car game", "racing game"
    )
    private val searchVerbs = setOf("ابحث", "بحث", "دور", "فتش", "ابحت", "ابحتث")
    private val writeVerbs = setOf("اكتب", "يكتب", "كتابه", "اكتبي")

    override fun understand(text: String): LocalAction {
        val original = text.trim()
        if (original.isEmpty()) return LocalAction.Unknown(original)
        val n = ArabicText.normalize(original)
        val tokens = original.split(Regex("\\s+"))
        val keys = tokens.map { ArabicText.normalize(it.trim('،', ',', '؟', '?', '!', '.')) }
        if (n in setOf("مساعده", "الاوامر") || n.contains("ماذا تستطيع")) return LocalAction.Help
        if (n in setOf("توقف", "وقف", "الغاء", "الغي", "اوقف التنفيذ")) return LocalAction.Stop
        if (n in setOf("ارجع", "رجوع", "عوده", "ارجع للخلف")) return LocalAction.Back
        if (n in setOf("الشاشه الرئيسيه", "ارجع للرئيسيه", "افتح الرئيسيه")) return LocalAction.Home
        if (n in setOf("اقرا الشاشه", "ما في الشاشه", "ايش في الشاشه", "ماذا علي الشاشه")) return LocalAction.ReadScreen
        if (listOf("التطبيقات المثبته", "التطبيقات الموجوده", "التطبيقات عندي", "ايش التطبيقات", "قائمه التطبيقات", "ما هي التطبيقات").any { n.contains(it) }) return LocalAction.ListApps

        // «ادخل تطبيق X» داخل مدير الملفات يعني فتح العنصر الظاهر، وليس تشغيل X
        // من قائمة التطبيقات المثبتة.
        val enterItem = Regex("^(?:ادخل|دخل|اذهب الى|اذهب ل)\\s+(?:تطبيق|التطبيق)\\s+(.+)$").find(n)
            ?.groupValues?.getOrNull(1)?.trim(' ', '،', ',', '.', '؟', '?')
        if (!enterItem.isNullOrBlank()) return LocalAction.Click(enterItem)

        val storage = Regex("^(?:و)?(?:ادخل|دخل|اذهب الى|اذهب ل|افتح)\\s+(قرص الذاكره|قرص الذاكرة|بطاقه الذاكره|بطاقة الذاكرة|الذاكره الداخليه|الذاكرة الداخلية|internal storage|sd card|storage)$")
            .find(n)?.groupValues?.getOrNull(1)
        if (!storage.isNullOrBlank()) return LocalAction.OpenFolder(storage)

        val folder = Regex("و?(?:ادخل|دخل|اذهب الى|اذهب ل|افتح)\\s+(?:مجلد|المجلد)\\s+(.+)$").find(n)
            ?.groupValues?.getOrNull(1)?.trim(' ', '،', ',', '.', '؟', '?')
        if (folder != null && folder.isNotBlank()) return LocalAction.OpenFolder(folder)

        val calculator = listOf("الحاسبه", "حاسبه", "calculator", "calc").any { n.contains(it) }
        if (calculator || keys.any { it == "احسب" || it == "واحسب" }) {
            val expression = extractExpression(n)
            if (expression != null) return LocalAction.OpenAppAndCalculate("الحاسبة", expression)
            if (n.any { it.isDigit() }) return LocalAction.Unknown(original)
        }
        val app = knownApp(n)
        val searchIndex = keys.indexOfFirst { verb(it) in searchVerbs }
        val writeIndex = keys.indexOfFirst { verb(it) in writeVerbs }
        val play = keys.any { verb(it) in setOf("شغلها", "شغله", "شغل", "تشغيل") }
        if (searchIndex >= 0 && (writeIndex < 0 || searchIndex < writeIndex)) {
            val query = extractPayload(tokens, keys, searchIndex, true)
            if (query.isBlank()) return LocalAction.Unknown(original)
            // صيغة «دور تطبيق تلجرام» لا تحدد تطبيقًا خارجيًا؛ ابحث في الشاشة الحالية.
            if (keys.getOrNull(searchIndex + 1) in setOf("تطبيق", "التطبيق", "ملف", "الملف", "مجلد", "المجلد")) {
                return LocalAction.SearchCurrent(query, play)
            }
            when (app) {
                "يوتيوب" -> return LocalAction.SearchYoutube(removeSongPrefix(query), play)
                "كروم" -> {
                    val images = Regex("(?:^| )صور(?:ه| )").containsMatchIn(ArabicText.normalize(query)) || keys.any { it == "صور" || it == "صوره" }
                    val download = keys.any { verb(it) in setOf("حمل", "نزل", "تحميل", "تنزيل") }
                    val cleaned = query.replace(Regex("^(?:صور|صورة|صوره)\\s+"), "")
                    val count = if (download && images) extractDownloadCount(keys, searchIndex) else 0
                    return LocalAction.SearchChrome(cleaned, images, download && images, count)
                }
                "ام اكس" -> return LocalAction.OpenAppAndSearch(app, removeSongPrefix(query), play)
                null -> {
                    val images = keys.any { it == "صور" || it == "صوره" || it == "صور" }
                    val download = keys.any { verb(it) in setOf("حمل", "نزل", "تحميل", "تنزيل") }
                    if (images) {
                        val cleaned = query.replace(Regex("^(?:صور|صورة|صوره)\\s+"), "")
                        val count = if (download) extractDownloadCount(keys, searchIndex) else 0
                        return LocalAction.SearchChrome(cleaned, true, download, count)
                    }
                    val target = searchTarget(tokens, keys, searchIndex)
                    if (target != null && ArabicText.normalize(target) !in setOf("جوجل", "google", "الويب", "الانترنت")) {
                        return LocalAction.OpenAppAndSearch(target, query, play)
                    }
                    // عندما لا يذكر المستخدم اسم التطبيق، احتفظ بالأمر كسياقي.
                    // MainActivity تختار التطبيق المفتوح (إن وجد) أو الويب كحل احتياطي.
                    return LocalAction.SearchCurrent(query, play)
                }
                else -> return LocalAction.OpenAppAndSearch(app, query, play)
            }
        }
        if (writeIndex >= 0) {
            val query = extractPayload(tokens, keys, writeIndex, false)
            if (query.isBlank()) return LocalAction.Unknown(original)
            val submit = keys.any { verb(it) in setOf("ابحث", "بحث", "تم", "انتر", "enter") }
            return LocalAction.Write(app, query, submit)
        }
        if (keys.firstOrNull()?.let { verb(it) } in setOf("اضغط", "انقر")) {
            val label = tokens.drop(1).joinToString(" ").trim()
            if (label.isNotEmpty()) return LocalAction.Click(label)
        }
        if (listOf("لايك", "اعجاب", "إعجاب", "اعجبني", "ضع اعجاب", "ضع إعجاب", "اعمل لايك", "سو لايك").any { n.contains(ArabicText.normalize(it)) }) {
            return LocalAction.Click("إعجاب")
        }
        if (app == "الملفات") return LocalAction.OpenFiles
        if (listOf("الاعدادات", "الضبط", "settings").any { n.contains(it) }) return LocalAction.OpenSettings
        if (app != null) return LocalAction.OpenApp(app)
        if (keys.firstOrNull()?.let { verb(it) } in setOf("افتح", "فتح", "شغل", "ابدأ", "ابدا")) {
            val query = tokens.drop(1).dropWhile { ArabicText.normalize(it) in setOf("لي", "تطبيق", "التطبيق") }.joinToString(" ")
            if (query.isNotBlank()) return LocalAction.OpenApp(query)
        }
        return LocalAction.Unknown(original)
    }

    private fun knownApp(n: String): String? {
        val padded = " $n "
        val found = appWords.firstOrNull { padded.contains(" $it ") || padded.contains(" و$it ") } ?: return null
        return when {
            found.contains("يوتيوب") || found == "youtube" -> "يوتيوب"
            found in setOf("كروم", "chrome", "المتصفح") -> "كروم"
            found.contains("اكس") || found.startsWith("mx") -> "ام اكس"
            found.contains("حاسبه") || found in setOf("calculator", "calc") -> "الحاسبة"
            found.contains("ملف") || found in setOf("files", "file manager") -> "الملفات"
            found.contains("كاميرا") || found == "camera" -> "الكاميرا"
            found in setOf("جوجل", "قوقل", "google") -> "جوجل"
            found.contains("متجر") || found.contains("play store") || found.contains("بلاي") && found.contains("google") -> "متجر بلاي"
            found.contains("موسيقى") || found.contains("music") -> "موسيقى بلاي"
            found.contains("تليجرام") || found.contains("تلجرام") || found.contains("telegram") -> "تليجرام"
            found.contains("ماسنجر") || found == "messenger" -> "ماسنجر"
            found.contains("فيسبوك") || found == "facebook" -> "فيسبوك"
            found.contains("انست") || found == "instagram" -> "انستقرام"
            found.contains("تيك") || found == "tiktok" -> "تيك توك"
            found.contains("رسائل") || found == "messages" -> "الرسائل"
            found.contains("اتصال") || found.contains("اسماء") || found == "contacts" -> "جهات الاتصال"
            found.contains("هاتف") || found == "phone" -> "الهاتف"
            found.contains("بريد") || found.contains("جيميل") || found == "gmail" -> "البريد الإلكتروني"
            found.contains("ساعه") || found == "clock" -> "الساعة"
            found.contains("مسجل") || found.contains("recorder") -> "مسجل الصوت"
            found.contains("راديو") || found.contains("radio") -> "الراديو"
            found.contains("سيار") || found.contains("سباق") || found.contains("hill") || found.contains("racing") || found.contains("car") -> "السيارة"
            else -> "الصور"
        }
    }

    private fun verb(key: String): String {
        val verbs = searchVerbs + writeVerbs + setOf("شغل", "شغلها", "شغله", "تشغيل", "حمل", "نزل", "تحميل", "تنزيل", "اضغط", "انقر", "افتح", "تم", "انتر", "enter")
        return if (key.startsWith("و") && key.drop(1) in verbs) key.drop(1) else key
    }

    private fun extractPayload(tokens: List<String>, keys: List<String>, index: Int, search: Boolean): String {
        var start = index + 1
        if (search) {
            val about = (start until keys.size).firstOrNull { keys[it] == "عن" }
            val explicitRoute = keys.getOrNull(start) in setOf("في", "داخل", "بداخل", "ضمن")
            when {
                explicitRoute -> {
                    start++
                    while (start < keys.size && keys[start] !in setOf("عن", "حول")) start++
                    if (keys.getOrNull(start) in setOf("عن", "حول")) start++
                }
                keys.getOrNull(start) == "عن" -> start++
                about != null && about > start -> {
                    // «ابحث يوتيوب عن ...»؛ احذف اسم التطبيق الواقع قبل «عن».
                    val prefix = keys.subList(start, about)
                    if (prefix.any { token -> appWords.any { app -> ArabicText.normalize(app) == token } }) start = about + 1
                }
                keys.getOrNull(start) in setOf("تطبيق", "التطبيق") -> start++
            }
        } else {
            // احذف مقدمة الأمر فقط؛ لا تحذف الكلمات المماثلة من عنوان البحث.
            val routeWords = appWords.flatMap { it.split(' ') }.toSet() + setOf("في", "لي", "تطبيق", "التطبيق", "جوجل", "google", "الويب", "الانترنت")
            while (start < keys.size && keys[start] in routeWords) start++
            if (keys.getOrNull(start) == "عن") start++
        }
        var end = tokens.size
        val trailingVerbs = setOf("شغلها", "شغله", "حمل", "نزل", "تحميل", "تنزيل", "اضغط", "انقر")
        for (i in start until keys.size) {
            if (verb(keys[i]) in trailingVerbs && (keys[i].startsWith("و") || keys.getOrNull(i - 1) in setOf("ثم", "و", "بعدين"))) {
                end = if (keys.getOrNull(i - 1) in setOf("ثم", "و", "بعدين")) i - 1 else i
                break
            }
        }
        return tokens.subList(start.coerceAtMost(end), end).joinToString(" ")
            .trim(' ', '،', ',', '"', '«', '»', 'و')
    }

    private fun searchTarget(tokens: List<String>, keys: List<String>, index: Int): String? {
        if (keys.getOrNull(index + 1) != "في") return null
        val about = (index + 2 until keys.size).firstOrNull { keys[it] == "عن" } ?: return null
        return tokens.subList(index + 2, about).joinToString(" ").takeIf { it.isNotBlank() }
    }

    private fun removeSongPrefix(query: String): String = query.replace(Regex("^(?:أغنية|اغنية|اغنيه|أغنيه)\\s+"), "")

    private fun extractDownloadCount(keys: List<String>, searchIndex: Int): Int {
        val downloadIndex = (searchIndex until keys.size).firstOrNull { verb(keys[it]) in setOf("حمل", "نزل", "تحميل", "تنزيل") }
            ?: return 1
        val next = keys.getOrNull(downloadIndex + 1) ?: return 1
        return when (next) {
            "واحد", "واحده", "صوره", "صوره واحده", "1" -> 1
            "اثنان", "اثنين", "ثنتين", "2" -> 2
            "ثلاث", "ثلاثه", "ثلاثة", "3" -> 3
            "اربع", "اربعه", "أربع", "أربعة", "4" -> 4
            "خمس", "خمسه", "خمسة", "5" -> 5
            "ست", "سته", "ستة", "6" -> 6
            "سبع", "سبعه", "سبعة", "7" -> 7
            "ثمان", "ثمانيه", "ثمانية", "8" -> 8
            "تسع", "تسعه", "تسعة", "9" -> 9
            else -> next.toIntOrNull()?.coerceIn(1, 9) ?: 1
        }
    }

    private fun extractExpression(n: String): String? {
        val numbers = n.replace("زائد", "+").replace("ناقص", "-").replace("ضرب", "*").replace("تقسيم", "/")
        val candidate = Regex("[-+]?\\d[\\d.\\s+*/-]*").find(numbers)?.value?.replace(" ", "")?.trimEnd('=') ?: return null
        return candidate.takeIf { Regex("[-+]?\\d+(?:\\.\\d+)?(?:[+*/-]\\d+(?:\\.\\d+)?)+").matches(it) }
    }
}
