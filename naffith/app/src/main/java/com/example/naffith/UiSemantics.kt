package com.example.naffith

import java.security.MessageDigest

/** Plain data: Android nodes are read afresh for every action, never retained. */
data class UiElement(
    val index: Int,
    val actionIndex: Int = index,
    val label: String = "",
    val hint: String = "",
    val viewId: String = "",
    val editable: Boolean = false,
    val password: Boolean = false,
    val clickable: Boolean = false,
    val scrollable: Boolean = false,
    val focused: Boolean = false,
    val selected: Boolean = false,
    val enabled: Boolean = true,
    val left: Int = 0, val top: Int = 0, val right: Int = 0, val bottom: Int = 0
)

data class UiSelector(val viewId: String, val semantic: String, val labelHash: String, val input: Boolean)
data class UiTransition(val from: String, val to: String, val via: UiSelector)
data class UiScreen(val key: String, val controls: List<UiSelector>)

/** Language-independent roles shared by all apps, resolved against the live UI. */
object UiSemantics {
    private val aliases = linkedMapOf(
        "search" to listOf("بحث", "ابحث", "البحث", "search", "find", "search button"),
        "message" to listOf("رسالة", "اكتب رسالة", "كتابة رسالة", "اسأل أي شيء", "رسالتك", "message", "type a message", "message chatgpt", "ask anything", "prompt", "chat input", "compose"),
        "send" to listOf("إرسال", "ارسل", "send", "send message", "send prompt", "submit", "إرسال الرسالة"),
        "menu" to listOf("القائمة", "قائمة", "قائمة التنقل", "القائمة الجانبية", "فتح قائمة التنقل", "menu", "navigation menu", "open navigation drawer", "drawer"),
        "more" to listOf("المزيد", "خيارات إضافية", "مزيد من الخيارات", "more", "more options", "overflow"),
        "home" to listOf("الرئيسية", "الصفحة الرئيسية", "home"),
        "subscriptions" to listOf("الاشتراكات", "اشتراكات", "subscriptions"),
        "shorts" to listOf("الشورت", "الشورتس", "شورتس", "shorts"),
        "account" to listOf("الحساب", "حسابي", "أنت", "الملف الشخصي", "account", "profile", "you"),
        "chats" to listOf("المحادثات", "محادثات", "الدردشات", "دردشات", "chats", "conversations"),
        "new_chat" to listOf("محادثة جديدة", "دردشة جديدة", "إنشاء محادثة", "new chat", "new conversation", "start new chat"),
        "channels" to listOf("القنوات", "قنوات", "channels"),
        "updates" to listOf("المستجدات", "التحديثات", "updates"),
        "calls" to listOf("المكالمات", "مكالمات", "calls"),
        "communities" to listOf("المجتمعات", "مجتمعات", "communities"),
        "music" to listOf("الموسيقى", "موسيقى", "الصوت", "أغاني", "music", "audio", "songs"),
        "videos" to listOf("الفيديوهات", "الفيديو", "فيديو", "videos", "video", "movies"),
        "photos" to listOf("الصور", "صور", "photos", "pictures", "gallery"),
        "albums" to listOf("الألبومات", "ألبومات", "albums"),
        "files" to listOf("الملفات", "ملفاتي", "files", "my files"),
        "storage" to listOf("الذاكرة الداخلية", "التخزين الداخلي", "وحدة التخزين الداخلية", "internal storage", "phone storage"),
        "sd_card" to listOf("بطاقة الذاكرة", "قرص الذاكرة", "الذاكرة الخارجية", "بطاقة sd", "sd card", "memory card"),
        "downloads" to listOf("التنزيلات", "تنزيلات", "downloads"),
        "settings" to listOf("الإعدادات", "إعدادات", "settings", "preferences"),
        "photo_mode" to listOf("صورة", "photo", "picture"),
        "video_mode" to listOf("تصوير فيديو", "تسجيل فيديو", "video mode"),
        "capture" to listOf("التقاط صورة", "زر الغالق", "تصوير", "take photo", "shutter", "capture"),
        "record" to listOf("بدء التسجيل", "تسجيل", "start recording", "record"),
        "like" to listOf("إعجاب", "أعجبني", "لايك", "like", "like video", "thumbs up"),
        "share" to listOf("مشاركة", "share"),
        "copy" to listOf("نسخ", "copy"),
        "paste" to listOf("لصق", "paste"),
        "rename" to listOf("إعادة تسمية", "rename"),
        "save" to listOf("حفظ", "save"),
        "play" to listOf("تشغيل", "play", "play video"),
        "pause" to listOf("إيقاف مؤقت", "pause", "pause video", "pause playback"),
        "start_game" to listOf("ابدأ اللعبة", "بدء اللعبة", "start game"),
        "done" to listOf("تم", "done", "enter", "go")
    ).mapValues { (_, names) -> names.map(ArabicText::normalize) }
    private val navigation = setOf("search", "menu", "more", "home", "subscriptions", "shorts", "account", "chats", "new_chat", "channels", "updates", "calls", "communities", "music", "videos", "photos", "albums", "files", "storage", "sd_card", "downloads", "settings", "photo_mode", "video_mode")

    fun target(value: String): String = ArabicText.normalize(value)
        .replace(Regex("^(?:زر|قسم|تبويب|القسم|التبويب)\\s+"), "").trim()

    fun canonical(value: String): String? {
        val wanted = target(value)
        return aliases.entries.firstOrNull { wanted == it.key || wanted in it.value }?.key
    }

    fun role(element: UiElement): String? {
        if (element.password) return null
        val label = target(if (element.editable) element.hint else element.label)
        // Dislike/unlike must never be classified as Like.
        if (label.contains("dislike") || label.contains("unlike") || label.contains("لم يعجب") || label.contains("عدم الاعجاب") || label.contains("ازاله الاعجاب")) return null
        canonical(label)?.let { return it }
        val idTokens = element.viewId.substringAfterLast('/').lowercase().split(Regex("[^a-z0-9]+"))
        if ("dislike" in idTokens || "unlike" in idTokens) return null
        return when {
            "search" in idTokens -> "search"
            element.editable && idTokens.any { it in setOf("message", "prompt", "compose", "composer", "chat") } -> "message"
            !element.editable && "send" in idTokens -> "send"
            "drawer" in idTokens -> "menu"
            "overflow" in idTokens -> "more"
            "new" in idTokens && "chat" in idTokens -> "new_chat"
            else -> aliases.keys.firstOrNull { it in idTokens }
        }
    }

    fun hash(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(target(value).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    fun selector(element: UiElement): UiSelector = UiSelector(element.viewId, role(element).orEmpty(),
        hash(if (element.editable) element.hint else element.label), element.editable)

    fun matches(selector: UiSelector, target: String, input: Boolean = false): Boolean =
        selector.input == input && (selector.labelHash == hash(target) ||
            canonical(target)?.let { selector.semantic == it } == true)

    fun score(element: UiElement, requested: String): Int {
        if (!element.enabled || element.password || element.editable || !element.clickable) return 0
        val wanted = target(requested)
        val label = target(element.label)
        val semantic = canonical(wanted)
        if (semantic == "like" && (label.contains("dislike") || label.contains("unlike") || label.contains("لم يعجب") || label.contains("عدم الاعجاب") || element.viewId.contains("dislike"))) return 0
        if (wanted.isNotBlank() && label == wanted) return 100
        if (semantic != null && role(element) == semantic) return 90
        if (wanted.length >= 3 && Regex("(?:^|[\\s،,:])${Regex.escape(wanted)}(?:$|[\\s،,:])").containsMatchIn(label)) return 70
        return 0
    }

    /** null means absent OR ambiguous: never silently choose the first of two buttons. */
    fun choose(elements: List<UiElement>, requested: String): UiElement? {
        val ranked = elements.map { it to score(it, requested) }.filter { it.second > 0 }
            .groupBy { it.first.actionIndex }.map { (_, group) -> group.maxBy { it.second } }
            .sortedByDescending { it.second }
        val best = ranked.firstOrNull() ?: return null
        if (ranked.getOrNull(1)?.second == best.second) return null
        return best.first
    }

    fun input(elements: List<UiElement>, search: Boolean): UiElement? {
        val usable = elements.filter { it.editable && it.enabled && !it.password }
        val wantedRole = if (search) "search" else "message"
        val matching = usable.filter { role(it) == wantedRole }
        matching.singleOrNull()?.let { return it }
        matching.singleOrNull { it.focused }?.let { return it }
        if (matching.isNotEmpty()) return null
        // Search never writes into a composer; Write never writes into Search or a login form.
        return usable.singleOrNull()?.takeIf { candidate ->
            role(candidate) == null && listOf("password", "email", "username", "phone", "login", "كلمه المرور", "البريد", "الهاتف")
                .none { target("${candidate.hint} ${candidate.viewId}").contains(it) }
        }
    }

    fun isNavigation(element: UiElement) = !element.editable && element.clickable && role(element) in navigation
    fun isSafeRoute(selector: UiSelector) = !selector.input && selector.semantic in navigation

    fun screenKey(elements: List<UiElement>): String {
        val structure = elements.filter { !it.password && (it.clickable || it.editable || it.scrollable) }
            .map { "${it.viewId}|${role(it)}|${it.editable}|${it.selected}" }.distinct().sorted().joinToString(";")
        return hash(structure)
    }

    /** Only structural data and hashes are persisted; live text stays in memory. */
    fun screen(elements: List<UiElement>) = UiScreen(screenKey(elements), elements
        .filter { !it.password && (it.clickable || it.editable) }.map(::selector).distinct().take(140))

    fun route(screens: List<UiScreen>, edges: List<UiTransition>, from: String, target: String, input: Boolean = false): List<UiTransition> {
        val goals = screens.filter { screen -> screen.controls.any { matches(it, target, input) } }.map { it.key }.toSet()
        if (from in goals) return emptyList()
        val queue = java.util.ArrayDeque<Pair<String, List<UiTransition>>>()
        queue.add(from to emptyList())
        val visited = mutableSetOf(from)
        while (queue.isNotEmpty()) {
            val (key, path) = queue.removeFirst()
            if (path.size >= 4) continue
            val outgoing = edges.filter { it.from == key && isSafeRoute(it.via) }
                // A selector that led to two different screens is not a reliable route.
                .groupBy { it.via }.values.filter { group -> group.map { it.to }.distinct().size == 1 }.map { it.first() }
            for (edge in outgoing) {
                if (!visited.add(edge.to)) continue
                val next = path + edge
                if (edge.to in goals) return next
                queue.add(edge.to to next)
            }
        }
        return emptyList()
    }
}
