package com.example.naffith

/** قواعد اختيار الأزرار لا تعتمد على Android وتخضع لاختبارات الانحدار. */
object AutomationRules {
    fun calculatorKey(text: String, viewId: String, key: Char): Boolean {
        val value = ArabicText.normalize(text)
        val id = viewId.substringAfterLast('/').lowercase()
        if (key.isDigit()) {
            val words = listOf("صفر", "واحد", "اثنان", "ثلاثه", "اربعه", "خمسه", "سته", "سبعه", "ثمانيه", "تسعه")
            return value == key.toString() || value == words[key - '0'] || value == "رقم $key" ||
                id in setOf("digit_$key", "digit$key", "num_$key", "btn_$key", "key_$key")
        }
        return when (key) {
            '+' -> value in setOf("+", "جمع", "زائد", "plus", "add") || id in setOf("op_add", "plus", "add")
            '-' -> value in setOf("-", "طرح", "ناقص", "minus", "subtract") || id in setOf("op_sub", "minus", "subtract")
            '*' -> value in setOf("*", "ضرب", "multiply", "times") || id in setOf("op_mul", "multiply")
            '/' -> value in setOf("/", "قسمه", "تقسيم", "divide") || id in setOf("op_div", "divide")
            '.' -> value in setOf(".", "نقطه", "فاصله عشريه", "decimal point") || id in setOf("dec_point", "decimal", "dot")
            '=' -> value in setOf("=", "يساوي", "equal", "equals", "نتيجه") || id in setOf("eq", "equals", "equal", "op_equal")
            else -> false
        }
    }

    fun isClear(text: String, id: String): Boolean = ArabicText.normalize(text) in
        setOf("ac", "c", "clear", "clear all", "مسح", "مسح الكل", "مسح جميع", "حذف الكل") ||
        id.substringAfterLast('/').lowercase() in setOf("clr", "clear", "clear_all", "btn_clear")

    fun submitButton(text: String, id: String): Boolean = ArabicText.normalize(text) in
        setOf("بحث", "ابحث", "search", "search button", "go", "تم", "done", "enter", "ادخال", "تنفيذ", "انتقال") ||
        id.substringAfterLast('/').lowercase() in setOf("search_go_btn", "search_submit", "submit", "search_button", "ime_enter", "key_enter", "enter")

    fun playCandidate(text: String, id: String, query: String, mx: Boolean): Boolean {
        val value = ArabicText.normalize(text)
        val name = id.lowercase()
        if (value in setOf("بحث", "search", "home", "الرئيسيه", "الصفحه الرئيسيه", "shorts", "اشتراك", "الاشتراكات", "subscriptions", "الفيديوهات", "videos", "audio", "الموسيقي")) return false
        if (listOf("mini_player", "miniplayer", "menu", "toolbar", "navigation", "search", "avatar", "channel", "advert").any { name.contains(it) }) return false
        if (value.contains("sponsored") || value.contains("اعلان") || value.contains("ممول")) return false
        if (mx) {
            val relevant = query.split(' ').filter { it.length >= 3 }.any { value.contains(ArabicText.normalize(it)) }
            return relevant && (name.contains("title") || name.contains("file_name") || name.contains("filename") || Regex("\\.(?:mp3|mp4|m4a|wav|mkv|ogg|webm)\\b").containsMatchIn(value))
        }
        return name.contains("video_title") || name.contains("video_card") || name.contains("video_renderer") ||
            ((value.contains("مشاهد") || value.contains("views") || value.contains("قبل ") || value.contains(" ago")) &&
                (Regex("\\d+[:٫.]\\d{2}").containsMatchIn(value) || value.contains("دقيق") || value.contains("minute") || value.length > 45))
    }

    fun downloadButton(text: String): Boolean {
        val value = ArabicText.normalize(text).trimEnd('.', '…').trim()
        if (value in setOf(
                "download image", "save image", "download picture", "save picture",
                "تنزيل الصوره", "تحميل الصوره", "حفظ الصوره", "تنزيل", "تحميل", "حفظ"
            )) return true
        return (value.contains("download") || value.contains("save") ||
            value.contains("تنزيل") || value.contains("تحميل") || value.contains("حفظ")) &&
            (value.contains("image") || value.contains("picture") || value.contains("صوره") ||
                value == "download" || value == "save")
    }

    fun clickCandidate(text: String, id: String, target: String): Boolean {
        val value = ArabicText.normalize(text)
        val wanted = ArabicText.normalize(target)
        if (value == wanted || value.contains(wanted)) return true
        val name = id.lowercase()
        if (wanted in setOf("قرص الذاكره", "بطاقه الذاكره", "الذاكره الداخليه", "internal storage", "sd card", "storage")) {
            val storageLabels = setOf(
                "قرص الذاكره", "بطاقه الذاكره", "الذاكره الداخليه", "التخزين الداخلي", "وحده التخزين",
                "وحده التخزين الداخليه", "internal storage", "phone", "storage", "sd card", "memory card", "بطاقه sd"
            )
            if (storageLabels.any { value.contains(it) } || name.contains("storage") || name.contains("sdcard") || name.contains("memory")) return true
        }
        return when {
            wanted in setOf("اعجاب", "لايك", "اعجبني", "like") ->
                value.contains("اعجب") || value.contains("like") || name.contains("like")
            wanted in setOf("اشتراك", "اشترك", "subscribe") ->
                value.contains("اشتراك") || value.contains("subscribe") || name.contains("subscribe")
            wanted in setOf("تشغيل", "شغل", "play") ->
                value == "play" || value.contains("تشغيل") || name.contains("play")
            wanted in setOf("تنزيل", "تحميل", "download") -> downloadButton(value) || name.contains("download")
            else -> false
        }
    }
}
