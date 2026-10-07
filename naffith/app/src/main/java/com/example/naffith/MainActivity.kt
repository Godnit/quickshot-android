package com.example.naffith

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import android.os.Handler
import android.os.Looper
import java.util.ArrayDeque

class MainActivity : Activity() {
    private val brain: LocalBrain = RuleBasedArabicBrain()
    private lateinit var catalog: AppCatalog
    private lateinit var commandInput: EditText
    private lateinit var logView: TextView
    private val planHandler = Handler(Looper.getMainLooper())
    private val pendingCommands = ArrayDeque<String>()
    private var planRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        catalog = AppCatalog(this)
        commandInput = findViewById(R.id.commandInput)
        logView = findViewById(R.id.logView)
        findViewById<Button>(R.id.executeButton).setOnClickListener { executeCommand(commandInput.text.toString()) }
        commandInput.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_GO ||
                event?.keyCode == KeyEvent.KEYCODE_ENTER) executeCommand(commandInput.text.toString())
            true
        }
    }

    override fun onResume() {
        super.onResume()
        val prefs = getSharedPreferences("naffith", MODE_PRIVATE)
        prefs.getString("automation_result", null)?.let { result ->
            appendLog("نفّذ: $result")
            prefs.edit().remove("automation_result").apply()
        }
    }

    private fun executeCommand(text: String) {
        if (text.trim().isEmpty()) {
            Toast.makeText(this, "اكتب أمرًا أولًا", Toast.LENGTH_SHORT).show()
            return
        }
        val parts = splitCommands(text)
        if (parts.size > 1) {
            pendingCommands.clear()
            pendingCommands.addAll(parts)
            planRunning = true
            appendLog("أنت: خطة من ${parts.size} أوامر. سأنفذها بالتتابع.")
            runNextPlannedCommand()
            commandInput.text.clear()
            return
        }
        executeSingleCommand(text)
    }

    private fun executeSingleCommand(text: String) {
        appendLog("أنت: $text")
        when (val action = brain.understand(text)) {
            is LocalAction.OpenApp -> openInstalledApp(action.query)
            is LocalAction.OpenAppAndSearch -> openInstalledAppAndSearch(action.appQuery, action.searchQuery, action.playFirst)
            is LocalAction.OpenAppAndCalculate -> openInstalledAppAndCalculate(action.appQuery, action.expression)
            is LocalAction.SearchYoutube -> searchYoutube(action.query, action.playFirst)
            is LocalAction.SearchChrome -> searchChrome(action.query, action.images, action.downloadImage)
            is LocalAction.SearchWeb -> searchWeb(action.query)
            is LocalAction.Write -> writeText(action)
            is LocalAction.Click -> clickLabel(action.label)
            is LocalAction.OpenFolder -> openFolder(action.folder)
            LocalAction.OpenFiles -> openFiles()
            LocalAction.OpenSettings -> openSettings()
            LocalAction.ListApps -> listInstalledApps()
            LocalAction.ReadScreen -> readScreen()
            LocalAction.Back -> appendLog(if (NaffithAccessibilityService.back()) "نفّذ: رجعت للخلف." else "نفّذ: فعّل إمكانية الوصول أولًا.")
            LocalAction.Home -> appendLog(if (NaffithAccessibilityService.home()) "نفّذ: رجعت إلى الشاشة الرئيسية." else "نفّذ: فعّل إمكانية الوصول أولًا.")
            LocalAction.Stop -> { NaffithAccessibilityService.stop(); appendLog("نفّذ: أوقفت الخطة الحالية.") }
            LocalAction.Help -> showHelp()
            is LocalAction.Unknown -> appendLog("نفّذ: لم أفهم الأمر. اكتب «مساعدة» لرؤية أمثلة الأوامر.")
        }
        commandInput.text.clear()
    }

    private fun runNextPlannedCommand() {
        if (pendingCommands.isEmpty()) {
            planRunning = false
            appendLog("نفّذ: اكتملت الخطة.")
            return
        }
        val command = pendingCommands.removeFirst()
        executeSingleCommand(command)
        if (pendingCommands.isNotEmpty()) {
            // نمنح التطبيق وإمكانية الوصول وقتًا لفتح الشاشة قبل الأمر التالي.
            planHandler.postDelayed({ runNextPlannedCommand() }, 6500L)
        } else {
            planHandler.postDelayed({ runNextPlannedCommand() }, 1200L)
        }
    }

    private fun splitCommands(text: String): List<String> {
        val tokens = text.replace('؛', '|').split(Regex("\\s+|\\|" )).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return emptyList()
        val starts = setOf("افتح", "شغل", "ابحث", "اكتب", "اضغط", "احسب", "ادخل", "دخل", "اذهب", "اقرا", "اقرأ", "ارجع", "توقف")
        val parts = mutableListOf<String>()
        val current = mutableListOf<String>()
        for (token in tokens) {
            val normalized = ArabicText.normalize(token.trim('،', ',', '.', '؟', '?', '!'))
            val bare = normalized.removePrefix("و")
            val previous = current.lastOrNull()?.let { ArabicText.normalize(it) }
            val startsNew = current.isNotEmpty() && (
                normalized in starts ||
                    bare in setOf("افتح", "ادخل", "دخل", "اذهب") && normalized.startsWith("و")
                )
            if (startsNew) {
                parts += current.joinToString(" ")
                current.clear()
            }
            current += token
        }
        if (current.isNotEmpty()) parts += current.joinToString(" ")
        return parts.map { it.trim() }.filter { it.isNotBlank() }
    }

    private fun openInstalledApp(query: String) {
        val app = catalog.find(query)
        if (app == null) {
            appendLog("نفّذ: لم أجد تطبيقًا مثبتًا باسم «$query». اكتب «ما هي التطبيقات المثبتة».")
            return
        }
        try {
            startActivity(app.intent)
            appendLog("نفّذ: تم فتح ${app.label}")
        } catch (_: ActivityNotFoundException) {
            appendLog("نفّذ: وجدت ${app.label} لكن لا يمكن تشغيله على هذا الهاتف.")
        }
    }

    private fun openInstalledAppAndSearch(appQuery: String, searchQuery: String, playFirst: Boolean) {
        val app = catalog.find(appQuery)
        if (app == null) { appendLog("نفّذ: لم أجد تطبيق «$appQuery»."); return }
        val queued = NaffithAccessibilityService.requestSearch(app.packageName, searchQuery, playFirst)
        try { startActivity(app.intent) } catch (_: ActivityNotFoundException) { appendLog("نفّذ: لا أستطيع تشغيل ${app.label}."); return }
        appendLog(if (queued) {
            if (playFirst) "نفّذ: فتحت ${app.label}، وسأبحث عن «$searchQuery» ثم أحاول تشغيل أول نتيجة."
            else "نفّذ: فتحت ${app.label} وسأكتب «$searchQuery» ثم أرسل البحث."
        } else "نفّذ: فتحت ${app.label}. فعّل إمكانية الوصول لكي أنفذ البحث داخل التطبيق.")
    }

    private fun openInstalledAppAndCalculate(appQuery: String, expression: String) {
        val app = catalog.find(appQuery)
        if (app == null) { appendLog("نفّذ: لم أجد تطبيق الحاسبة. اكتب «ما هي التطبيقات المثبتة»."); return }
        val queued = NaffithAccessibilityService.requestCalculator(app.packageName, expression)
        try { startActivity(app.intent) } catch (_: ActivityNotFoundException) { appendLog("نفّذ: لا أستطيع تشغيل ${app.label}."); return }
        appendLog(if (queued) "نفّذ: فتحت ${app.label} وسأدخل $expression ثم أضغط يساوي."
        else "نفّذ: فتحت ${app.label}. فعّل إمكانية الوصول لكي أضغط أزرار الحاسبة.")
    }

    private fun searchYoutube(query: String, playFirst: Boolean) {
        val youtube = catalog.find("يوتيوب")
        val url = if (query.isBlank()) "https://www.youtube.com" else "https://www.youtube.com/results?search_query=${Uri.encode(query)}"
        val queued = if (playFirst && youtube != null) NaffithAccessibilityService.requestPlayFirst(youtube.packageName, query) else false
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply { youtube?.let { setPackage(it.packageName) } }
        try { startActivity(intent) } catch (_: ActivityNotFoundException) { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        appendLog(if (query.isBlank()) "نفّذ: فتحت يوتيوب."
        else if (playFirst && queued) "نفّذ: بحثت في يوتيوب عن «$query» وسأحاول تشغيل أول نتيجة."
        else "نفّذ: بحثت في يوتيوب عن «$query».")
    }

    private fun searchChrome(query: String, images: Boolean, downloadImage: Boolean) {
        val chrome = catalog.find("كروم")
        val url = if (images) "https://www.google.com/search?tbm=isch&q=${Uri.encode(query)}" else "https://www.google.com/search?q=${Uri.encode(query)}"
        val queued = if (downloadImage && images && chrome != null) NaffithAccessibilityService.requestImageDownload(chrome.packageName, query) else false
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply { chrome?.let { setPackage(it.packageName) } }
        try { startActivity(intent) } catch (_: ActivityNotFoundException) { searchWeb(query, images); return }
        appendLog(when {
            downloadImage && images && queued -> "نفّذ: فتحت صور Google عن «$query» وسأحاول تنزيل صورة عشوائية."
            images -> "نفّذ: فتحت صور Google عن «$query»."
            else -> "نفّذ: بحثت في Chrome عن «$query»."
        })
    }

    private fun searchWeb(query: String, images: Boolean = false) {
        val url = if (images) "https://www.google.com/search?tbm=isch&q=${Uri.encode(query)}" else "https://www.google.com/search?q=${Uri.encode(query)}"
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        appendLog(if (images) "نفّذ: فتحت نتائج الصور عن «$query»." else "نفّذ: بحثت على الويب عن «$query».")
    }

    private fun writeText(action: LocalAction.Write) {
        val packageName = action.appQuery?.let { catalog.find(it)?.packageName } ?: NaffithAccessibilityService.latestScreenPackage
        if (packageName.isBlank()) { appendLog("نفّذ: افتح التطبيق أولًا أو اكتب «اكتب في كروم ...»."); return }
        val queued = NaffithAccessibilityService.requestWrite(packageName, action.text, action.submit)
        val app = action.appQuery?.let { catalog.find(it) }
        if (app != null) try { startActivity(app.intent) } catch (_: ActivityNotFoundException) { }
        appendLog(if (queued) "نفّذ: سأكتب «${action.text}»${if (action.submit) " ثم أرسلها." else "."}" else "نفّذ: فعّل إمكانية الوصول لكي أكتب داخل التطبيق.")
    }

    private fun clickLabel(label: String) {
        val packageName = NaffithAccessibilityService.latestScreenPackage
        val queued = if (packageName.isBlank()) false else NaffithAccessibilityService.requestClick(packageName, label)
        appendLog(if (queued) "نفّذ: سأضغط «$label»." else "نفّذ: لا توجد شاشة مستهدفة. افتح التطبيق أولًا.")
    }

    private fun openFolder(folder: String) {
        val fileApp = catalog.find("الملفات")
        if (fileApp == null) {
            appendLog("نفّذ: لم أجد تطبيق إدارة الملفات لفتح مجلد «$folder».")
            return
        }
        val queued = NaffithAccessibilityService.requestClick(fileApp.packageName, folder)
        try { startActivity(fileApp.intent) } catch (_: ActivityNotFoundException) {
            appendLog("نفّذ: لا أستطيع تشغيل ${fileApp.label}.")
            return
        }
        appendLog(if (queued) "نفّذ: فتحت ${fileApp.label} وسأدخل مجلد «$folder»."
        else "نفّذ: فتحت ${fileApp.label}. فعّل إمكانية الوصول لدخول المجلد تلقائيًا.")
    }

    private fun openFiles() {
        val fileApp = catalog.find("الملفات")
        if (fileApp != null) {
            try { startActivity(fileApp.intent); appendLog("نفّذ: تم فتح ${fileApp.label}"); return } catch (_: ActivityNotFoundException) { }
        }
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
        try { startActivityForResult(intent, REQUEST_OPEN_FILE); appendLog("نفّذ: فتحت منتقي الملفات.") }
        catch (_: ActivityNotFoundException) { appendLog("نفّذ: لم أجد مدير ملفات متاحًا.") }
    }

    private fun openSettings() { startActivity(Intent(Settings.ACTION_SETTINGS)); appendLog("نفّذ: تم فتح الإعدادات.") }

    private fun listInstalledApps() {
        val apps = catalog.all()
        if (apps.isEmpty()) { appendLog("نفّذ: لم أستطع قراءة قائمة التطبيقات."); return }
        val visible = apps.take(100).joinToString("\n") { "• ${it.label}" }
        appendLog("التطبيقات التي أستطيع تشغيلها (${apps.size}):\n$visible${if (apps.size > 100) "\n… و${apps.size - 100} تطبيقات أخرى" else ""}")
    }

    private fun readScreen() {
        val text = NaffithAccessibilityService.latestScreenText
        appendLog(if (text.isBlank()) "نفّذ: لا توجد قراءة شاشة بعد. افتح التطبيق ثم جرّب مرة أخرى." else "قراءة الشاشة الحالية:\n$text")
    }

    private fun showHelp() {
        appendLog("أمثلة:\n• افتح يوتيوب وابحث عن أغنية يا ليلي وشغلها\n• افتح مشغل MX وابحث عن موسيقى وشغلها\n• افتح الحاسبة واحسب 500+645\n• افتح كروم وابحث عن صور قطط وحمل صورة\n• اكتب في كروم قطط واضغط بحث\n• اضغط تنزيل\n• ما هي التطبيقات المثبتة\n• اقرأ الشاشة / رجوع / الرئيسية / توقف")
    }

    private fun appendLog(message: String) {
        val current = logView.text.toString()
        logView.text = message + if (current.isBlank()) "" else "\n\n$current"
    }

    companion object { private const val REQUEST_OPEN_FILE = 1001 }
}
