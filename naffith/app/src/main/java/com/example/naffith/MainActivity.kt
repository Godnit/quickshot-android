package com.example.naffith

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val brain: LocalBrain = RuleBasedArabicBrain()
    private lateinit var catalog: AppCatalog
    private lateinit var commandInput: EditText
    private lateinit var logView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        catalog = AppCatalog(this)
        commandInput = findViewById(R.id.commandInput)
        logView = findViewById(R.id.logView)
        val executeButton: Button = findViewById(R.id.executeButton)

        executeButton.setOnClickListener { executeCommand(commandInput.text.toString()) }
        commandInput.setOnEditorActionListener { _, _, _ ->
            executeCommand(commandInput.text.toString())
            true
        }
    }

    private fun executeCommand(text: String) {
        if (text.trim().isEmpty()) {
            Toast.makeText(this, "اكتب أمرًا أولًا", Toast.LENGTH_SHORT).show()
            return
        }
        appendLog("أنت: $text")
        when (val action = brain.understand(text)) {
            is LocalAction.OpenApp -> openInstalledApp(action.query)
            is LocalAction.OpenAppAndSearch -> openInstalledAppAndSearch(action.appQuery, action.searchQuery)
            is LocalAction.OpenAppAndCalculate -> openInstalledAppAndCalculate(action.appQuery, action.expression)
            is LocalAction.SearchYoutube -> searchYoutube(action.query, action.playFirst)
            is LocalAction.SearchChrome -> searchChrome(action.query, action.images)
            is LocalAction.SearchWeb -> searchWeb(action.query)
            LocalAction.OpenFiles -> openFiles()
            LocalAction.OpenSettings -> openSettings()
            LocalAction.ListApps -> listInstalledApps()
            LocalAction.Help -> showHelp()
            is LocalAction.Unknown -> appendLog("نفّذ: لم أفهم الأمر بعد. اكتب «مساعدة» لرؤية الأوامر المتاحة.")
        }
        commandInput.text.clear()
    }

    private fun openInstalledApp(query: String) {
        val app = catalog.find(query)
        if (app == null) {
            appendLog("نفّذ: لم أجد تطبيقًا مثبتًا باسم «$query». جرّب «ما هي التطبيقات المثبتة».")
            return
        }
        val intent = packageManager.getLaunchIntentForPackage(app.packageName)
        if (intent == null) {
            appendLog("نفّذ: وجدت ${app.label} لكن لا يوجد له اختصار تشغيل.")
            return
        }
        startActivity(intent)
        appendLog("نفّذ: تم فتح ${app.label}")
    }

    private fun openInstalledAppAndSearch(appQuery: String, searchQuery: String) {
        val app = catalog.find(appQuery)
        if (app == null) {
            appendLog("نفّذ: لم أجد تطبيق «$appQuery». اكتب «ما هي التطبيقات المثبتة» للتأكد من الاسم.")
            return
        }
        val intent = packageManager.getLaunchIntentForPackage(app.packageName)
        if (intent == null) {
            appendLog("نفّذ: لا أستطيع تشغيل ${app.label}.")
            return
        }
        NaffithAccessibilityService.requestSearch(app.packageName, searchQuery)
        startActivity(intent)
        appendLog("نفّذ: فتحت ${app.label} وسأبحث عن «$searchQuery» وأضغط Enter عبر إمكانية الوصول.")
    }

    private fun openInstalledAppAndCalculate(appQuery: String, expression: String) {
        val app = catalog.find(appQuery)
        if (app == null) {
            appendLog("نفّذ: لم أجد تطبيق الحاسبة. جرّب فتحه يدويًا مرة أو اكتب «ما هي التطبيقات المثبتة».")
            return
        }
        val intent = packageManager.getLaunchIntentForPackage(app.packageName)
        if (intent == null) {
            appendLog("نفّذ: وجدت ${app.label} لكن لا يوجد له اختصار تشغيل.")
            return
        }
        NaffithAccessibilityService.requestCalculator(app.packageName, expression)
        startActivity(intent)
        appendLog("نفّذ: فتحت ${app.label} وسأدخل $expression ثم أضغط يساوي عبر إمكانية الوصول.")
    }

    private fun searchYoutube(query: String, playFirst: Boolean) {
        val url = if (query.isBlank()) "https://www.youtube.com" else
            "https://www.youtube.com/results?search_query=${Uri.encode(query)}"
        val youtube = catalog.find("يوتيوب")
        if (playFirst && youtube != null) NaffithAccessibilityService.requestPlayFirst(youtube.packageName)
        val youtubeIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            if (youtube != null) setPackage(youtube.packageName)
        }
        try {
            startActivity(youtubeIntent)
        } catch (_: ActivityNotFoundException) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
        appendLog(
            if (query.isBlank()) "نفّذ: تم فتح يوتيوب"
            else if (playFirst) "نفّذ: بحثت في يوتيوب عن «$query» وسأحاول تشغيل أول نتيجة."
            else "نفّذ: بحثت في يوتيوب عن «$query»."
        )
    }

    private fun searchChrome(query: String, images: Boolean) {
        val url = if (images) {
            "https://www.google.com/search?tbm=isch&q=${Uri.encode(query)}"
        } else {
            "https://www.google.com/search?q=${Uri.encode(query)}"
        }
        val chrome = catalog.find("كروم")
        val chromeIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            if (chrome != null) setPackage(chrome.packageName)
        }
        try {
            startActivity(chromeIntent)
        } catch (_: ActivityNotFoundException) {
            searchWeb(query, images)
            return
        }
        appendLog(if (images) "نفّذ: فتحت صور Google عن «$query». اختر الصورة واحفظها من Chrome." else "نفّذ: بحثت في Chrome عن «$query».")
    }

    private fun searchWeb(query: String, images: Boolean = false) {
        val url = if (images) "https://www.google.com/search?tbm=isch&q=${Uri.encode(query)}"
        else "https://www.google.com/search?q=${Uri.encode(query)}"
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        appendLog(if (images) "نفّذ: فتحت نتائج الصور عن «$query»." else "نفّذ: بحثت على الويب عن «$query».")
    }

    private fun openFiles() {
        val fileApp = catalog.find("اداره الملفات")
        val launchIntent = fileApp?.let { packageManager.getLaunchIntentForPackage(it.packageName) }
        if (launchIntent != null) {
            startActivity(launchIntent)
            appendLog("نفّذ: تم فتح ${fileApp.label}")
            return
        }
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        try {
            startActivityForResult(intent, REQUEST_OPEN_FILE)
            appendLog("نفّذ: فتحت منتقي الملفات")
        } catch (_: ActivityNotFoundException) {
            appendLog("نفّذ: لم أجد مدير ملفات متاحًا")
        }
    }

    private fun openSettings() {
        startActivity(Intent(Settings.ACTION_SETTINGS))
        appendLog("نفّذ: تم فتح الإعدادات")
    }

    private fun listInstalledApps() {
        val apps = catalog.all()
        if (apps.isEmpty()) {
            appendLog("نفّذ: لم أستطع قراءة قائمة التطبيقات. تأكد من تفعيل إمكانية رؤية التطبيقات.")
            return
        }
        val visible = apps.take(100).joinToString("\n") { "• ${it.label}" }
        val suffix = if (apps.size > 100) "\n… و${apps.size - 100} تطبيقات أخرى" else ""
        appendLog("التطبيقات القابلة للتشغيل (${apps.size}):\n$visible$suffix")
    }

    private fun showHelp() {
        appendLog(
            "الأوامر المتاحة حاليًا:\n" +
                "• افتح تطبيق لقطة شاشة / الكاميرا / إدارة الملفات\n" +
                "• ما هي التطبيقات المثبتة\n" +
                "• افتح الحاسبة واحسب 500+645\n" +
                "• ابحث في يوتيوب عن أغنية يا ليلي وشغلها\n" +
                "• ابحث في كروم عن صور قطط\n" +
                "• افتح مشغل ام اكس وابحث عن موسيقى\n" +
                "• افتح الإعدادات"
        )
    }

    private fun appendLog(message: String) {
        val current = logView.text.toString()
        val separator = if (current.isBlank()) "" else "\n\n"
        logView.text = message + separator + current
    }

    companion object {
        private const val REQUEST_OPEN_FILE = 1001
    }
}
