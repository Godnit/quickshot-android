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
            is LocalAction.SearchYoutube -> searchYoutube(action.query)
            is LocalAction.SearchChrome -> searchChrome(action.query)
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
            appendLog("نفّذ: لم أجد تطبيقًا مثبتًا باسم «$query». جرّب «ما هي التطبيقات المثبتة». ")
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
            appendLog("نفّذ: لم أجد تطبيق «$appQuery». ثبّت التطبيق أو اكتب قائمة التطبيقات.")
            return
        }
        val intent = packageManager.getLaunchIntentForPackage(app.packageName)
        if (intent == null) {
            appendLog("نفّذ: لا أستطيع تشغيل ${app.label}.")
            return
        }
        NaffithAccessibilityService.requestSearch(app.packageName, searchQuery)
        startActivity(intent)
        appendLog("نفّذ: فتحت ${app.label} وسأحاول البحث عن «$searchQuery» عبر إمكانية الوصول.")
    }

    private fun searchYoutube(query: String) {
        val url = if (query.isBlank()) "https://www.youtube.com" else
            "https://www.youtube.com/results?search_query=${Uri.encode(query)}"
        val youtubeIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            setPackage("com.google.android.youtube")
        }
        try {
            startActivity(youtubeIntent)
        } catch (_: ActivityNotFoundException) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
        appendLog(if (query.isBlank()) "نفّذ: تم فتح يوتيوب" else "نفّذ: بحثت في يوتيوب عن «$query»")
    }

    private fun searchChrome(query: String) {
        val url = "https://www.google.com/search?q=${Uri.encode(query)}"
        val chromeIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            setPackage("com.android.chrome")
        }
        try {
            startActivity(chromeIntent)
        } catch (_: ActivityNotFoundException) {
            searchWeb(query)
        }
        appendLog("نفّذ: بحثت في Chrome عن «$query»")
    }

    private fun searchWeb(query: String) {
        val url = "https://www.google.com/search?q=${Uri.encode(query)}"
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        appendLog("نفّذ: بحثت على الويب عن «$query»")
    }

    private fun openFiles() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        try {
            startActivityForResult(intent, REQUEST_OPEN_FILE)
            appendLog("نفّذ: افتح منتقي الملفات")
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
            appendLog("نفّذ: لم أستطع قراءة قائمة التطبيقات.")
            return
        }
        val labels = apps.take(40).joinToString("\n") { "• ${it.label}" }
        appendLog("التطبيقات المثبتة التي لها اختصار تشغيل:\n$labels")
    }

    private fun showHelp() {
        appendLog(
            "الأوامر المتاحة حاليًا:\n" +
                "• افتح تطبيق لقطة شاشة\n" +
                "• ما هي التطبيقات المثبتة\n" +
                "• ابحث في يوتيوب عن موسيقى\n" +
                "• ابحث في كروم عن أخبار اليوم\n" +
                "• افتح مشغل ام اكس وابحث عن موسيقى\n" +
                "• افتح الملفات\n" +
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
