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
        Group(listOf("إدارة الملفات", "مدير الملفات", "الملفات", "ملفاتي", "files", "file manager", "my files", "files by google"), listOf("com.huawei.hidisk", "com.android.filemanager", "com.sec.android.app.myfiles", "com.google.android.apps.nbu.files", "com.android.documentsui", "com.google.android.documentsui")),
        Group(listOf("الكاميرا", "كاميرا", "camera"), listOf("com.huawei.camera", "com.android.camera", "com.android.camera2", "com.google.android.GoogleCamera", "com.sec.android.app.camera")),
        Group(listOf("المعرض", "الصور", "صور", "gallery", "photos", "google photos", "الاستوديو"), listOf("com.android.gallery3d", "com.huawei.photos", "com.google.android.apps.photos")),
        Group(listOf("لقطة شاشة", "لقطة سريعة", "لقطة سريعه", "screenshot", "quickshot"), emptyList()),
        Group(listOf("واتساب", "واتس اب", "whatsapp"), listOf("com.whatsapp", "com.whatsapp.w4b")),
        Group(listOf("موسيقى play", "play music", "موسيقى", "مشغل الموسيقى", "music"), listOf("com.google.android.music"))
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
