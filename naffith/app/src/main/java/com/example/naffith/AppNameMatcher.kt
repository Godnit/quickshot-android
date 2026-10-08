package com.example.naffith

data class AppName(val packageName: String, val label: String)

/** المطابقة الحرفية أولًا، ثم الأسماء البديلة، ثم خطأ إملائي صغير غير ملتبس. */
object AppNameMatcher {
    private data class Group(val names: List<String>, val packages: List<String>)
    private val groups = listOf(
        Group(listOf("يوتيوب", "يو تيوب", "يوتوب", "youtube"), listOf("com.google.android.youtube")),
        Group(listOf("كروم", "chrome", "google chrome", "جوجل كروم", "قوقل كروم", "المتصفح"), listOf("com.android.chrome")),
        Group(listOf("ام اكس", "ام اكس بلاير", "ام اكس بليير", "مشغل ام اكس", "mx player", "mxplayer", "مشغل mx"), listOf("com.mxtech.videoplayer.ad", "com.mxtech.videoplayer.pro")),
        Group(listOf("الحاسبة", "حاسبة", "الآلة الحاسبة", "آلة حاسبة", "calculator", "calc"), listOf("com.android.calculator2", "com.google.android.calculator", "com.huawei.calculator", "com.sec.android.app.popupcalculator")),
        Group(listOf("إدارة الملفات", "مدير الملفات", "الملفات", "ملفاتي", "files", "file manager", "my files", "files by google"), listOf("com.huawei.hidisk", "com.android.filemanager", "com.sec.android.app.myfiles", "com.google.android.apps.nbu.files")),
        Group(listOf("الكاميرا", "كاميرا", "camera"), listOf("com.huawei.camera", "com.android.camera", "com.android.camera2", "com.google.android.GoogleCamera", "com.sec.android.app.camera")),
        Group(listOf("المعرض", "الصور", "صور", "gallery", "photos", "google photos", "الاستوديو"), listOf("com.android.gallery3d", "com.huawei.photos", "com.google.android.apps.photos")),
        Group(listOf("جوجل", "قوقل", "google", "google app", "تطبيق جوجل"), listOf("com.google.android.googlequicksearchbox")),
        Group(listOf("متجر بلاي", "متجر play", "جوجل بلاي", "google play", "play store", "المتجر"), listOf("com.android.vending")),
        Group(listOf("موسيقى بلاي", "موسيقى play", "play music", "youtube music", "يوتيوب ميوزك"), listOf("com.google.android.apps.youtube.music", "com.google.android.music")),
        Group(listOf("تليجرام", "تلجرام", "تيليجرام", "telegram"), listOf("org.telegram.messenger")),
        Group(listOf("ماسنجر", "فيسبوك ماسنجر", "messenger"), listOf("com.facebook.orca")),
        Group(listOf("فيسبوك", "facebook"), listOf("com.facebook.katana")),
        Group(listOf("انستقرام", "انستغرام", "instagram"), listOf("com.instagram.android")),
        Group(listOf("تيك توك", "تيكتوك", "tiktok"), listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill")),
        Group(listOf("الرسائل", "رسائل", "الرسائل النصية", "messages", "messaging"), listOf("com.google.android.apps.messaging", "com.android.mms", "com.android.messaging")),
        Group(listOf("جهات الاتصال", "الأسماء", "اسماء", "contacts", "phonebook"), listOf("com.google.android.contacts", "com.android.contacts")),
        Group(listOf("الهاتف", "الاتصال", "phone", "dialer"), listOf("com.google.android.dialer", "com.android.dialer")),
        Group(listOf("البريد الإلكتروني", "البريد الالكتروني", "جيميل", "gmail", "email", "البريد"), listOf("com.google.android.gm")),
        Group(listOf("الساعة", "clock", "المنبه", "alarm"), listOf("com.google.android.deskclock", "com.android.deskclock")),
        Group(listOf("مسجل الصوت", "المسجل الصوتي", "مسجل صوتي", "voice recorder", "sound recorder"), listOf("com.android.soundrecorder", "com.huawei.android.soundrecorder")),
        Group(listOf("الراديو", "راديو fm", "fm radio", "radio"), listOf("com.huawei.android.FMRadio", "com.sec.android.app.fm")),
        Group(listOf("التنزيلات", "downloads"), listOf("com.android.providers.downloads.ui")),
        Group(listOf("لقطة شاشة", "لقطة سريعة", "لقطة سريعه", "screenshot", "quickshot"), emptyList()),
        Group(listOf("واتساب", "واتس اب", "whatsapp"), listOf("com.whatsapp", "com.whatsapp.w4b")),
        Group(listOf("موسيقى play", "play music", "موسيقى", "مشغل الموسيقى", "music"), listOf("com.google.android.music")),
        // أسماء مختصرة شائعة للعبة Hill Climb Racing الظاهرة في صورة المستخدم.
        Group(
            listOf(
                "السيارة", "سياره", "لعبة السيارة", "لعبه السياره", "لعبة سيارات", "لعبه سيارات",
                "سباق السيارات", "hill climb", "hill climb racing", "car game", "racing game"
            ),
            listOf("com.fingersoft.hillclimb", "com.fingersoft.hillclimb2", "com.fingersoft.hcr2")
        )
    )

    fun find(query: String, apps: List<AppName>): AppName? {
        val q = ArabicText.normalize(query).removePrefix("تطبيق ").trim()
        if (q.isEmpty()) return null
        apps.firstOrNull { ArabicText.normalize(it.label) == q || it.packageName == query }?.let { return it }
        val group = groups.firstOrNull { g -> g.names.any { ArabicText.normalize(it) == q } }
            ?: groups.firstOrNull { g -> g.names.any { name -> near(q, ArabicText.normalize(name)) } }
        if (group != null) {
            group.packages.forEach { pkg -> apps.firstOrNull { it.packageName == pkg }?.let { return it } }
            group.names.forEach { name -> apps.firstOrNull { ArabicText.normalize(it.label) == ArabicText.normalize(name) }?.let { return it } }
        }
        val candidates = group?.names?.map(ArabicText::normalize) ?: listOf(q)
        val contained = apps.filter { app -> candidates.any { name -> name.length >= 3 && ArabicText.normalize(app.label).contains(name) } }
        if (contained.size == 1) return contained.first()
        val nearMatches = apps.filter { near(q, ArabicText.normalize(it.label)) }
        return nearMatches.singleOrNull()
    }

    private fun near(a: String, b: String): Boolean {
        if (a.length < 4 || b.length < 4 || kotlin.math.abs(a.length - b.length) > 1) return false
        var previous = IntArray(b.length + 1) { it }
        for (i in a.indices) {
            val current = IntArray(b.length + 1)
            current[0] = i + 1
            for (j in b.indices) current[j + 1] = minOf(current[j] + 1, previous[j + 1] + 1, previous[j] + if (a[i] == b[j]) 0 else 1)
            previous = current
        }
        return previous.last() <= 1
    }
}
