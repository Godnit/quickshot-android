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
            is LocalAction.SearchChrome -> searchChrome(action.query, action.images, action.downloadImage, action.downloadCount)
            is LocalAction.SearchWeb -> searchCurrentOrWeb(action.query)
            is LocalAction.SearchCurrent -> searchCurrentApp(action.query)
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
        if (NaffithAccessibilityService.isBusy()) {
            planHandler.postDelayed({ runNextPlannedCommand() }, 180L)
            return
        }
        if (pendingCommands.isEmpty()) {
            planRunning = false
            appendLog("نفّذ: اكتملت الخطة.")
            return
        }
        val command = pendingCommands.removeFirst()
        executeSingleCommand(command)
        if (pendingCommands.isNotEmpty()) {
            // ننتظر انتهاء خطوة إمكانية الوصول بدل تأخير ثابت طويل بين كل الأوامر.
            val delay = if (NaffithAccessibilityService.isBusy()) 180L else 350L
            planHandler.postDelayed({ runNextPlannedCommand() }, delay)
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
                    bare in starts && normalized.startsWith("و")
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
            if (!launchExternal(app.intent)) {
                appendLog("نفّذ: وجدت ${app.label} لكن لا يمكن تشغيله على هذا الهاتف.")
                return
            }
            appendLog("نفّذ: تم فتح ${app.label}")
        } catch (_: ActivityNotFoundException) {
            appendLog("نفّذ: وجدت ${app.label} لكن لا يمكن تشغيله على هذا الهاتف.")
        }
    }

    private fun openInstalledAppAndSearch(appQuery: String, searchQuery: String, playFirst: Boolean) {
        val app = catalog.find(appQuery)
        if (app == null) { appendLog("نفّذ: لم أجد تطبيق «$appQuery»."); return }
        val queued = NaffithAccessibilityService.requestSearch(app.packageName, searchQuery, playFirst)
        if (!launchExternal(app.intent)) { appendLog("نفّذ: لا أستطيع تشغيل ${app.label}."); return }
        appendLog(if (queued) {
            if (playFirst) "نفّذ: فتحت ${app.label}، وسأبحث عن «$searchQuery» ثم أحاول تشغيل أول نتيجة."
            else "نفّذ: فتحت ${app.label} وسأكتب «$searchQuery» ثم أرسل البحث."
        } else "نفّذ: فتحت ${app.label}. فعّل إمكانية الوصول لكي أنفذ البحث داخل التطبيق.")
    }

    private fun openInstalledAppAndCalculate(appQuery: String, expression: String) {
        val app = catalog.find(appQuery)
        if (app == null) { appendLog("نفّذ: لم أجد تطبيق الحاسبة. اكتب «ما هي التطبيقات المثبتة»."); return }
        val queued = NaffithAccessibilityService.requestCalculator(app.packageName, expression)
        if (!launchExternal(app.intent)) { appendLog("نفّذ: لا أستطيع تشغيل ${app.label}."); return }
        appendLog(if (queued) "نفّذ: فتحت ${app.label} وسأدخل $expression ثم أضغط يساوي."
        else "نفّذ: فتحت ${app.label}. فعّل إمكانية الوصول لكي أضغط أزرار الحاسبة.")
    }

    private fun searchYoutube(query: String, playFirst: Boolean) {
        val youtube = catalog.find("يوتيوب")
        val url = if (query.isBlank()) "https://www.youtube.com" else "https://www.youtube.com/results?search_query=${Uri.encode(query)}"
        val queued = if (playFirst && youtube != null) NaffithAccessibilityService.requestPlayFirst(youtube.packageName, query) else false
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply { youtube?.let { setPackage(it.packageName) } }
        if (!launchExternal(intent)) launchExternal(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        appendLog(if (query.isBlank()) "نفّذ: فتحت يوتيوب."
        else if (playFirst && queued) "نفّذ: بحثت في يوتيوب عن «$query» وسأحاول تشغيل أول نتيجة."
        else "نفّذ: بحثت في يوتيوب عن «$query».")
    }

    private fun searchChrome(query: String, images: Boolean, downloadImage: Boolean, downloadCount: Int) {
        val chrome = catalog.find("كروم")
        val url = if (images) "https://www.google.com/search?tbm=isch&q=${Uri.encode(query)}" else "https://www.google.com/search?q=${Uri.encode(query)}"
        val queued = if (downloadImage && images && chrome != null) {
            NaffithAccessibilityService.requestImageDownload(chrome.packageName, query, downloadCount.coerceIn(1, 9))
        } else false
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply { chrome?.let { setPackage(it.packageName) } }
        if (!launchExternal(intent)) { searchWeb(query, images); return }
        appendLog(when {
            downloadImage && images && queued -> "نفّذ: فتحت صور Google عن «$query» وسأحاول تنزيل ${downloadCount.coerceIn(1, 9)} صور."
            images -> "نفّذ: فتحت صور Google عن «$query»."
            else -> "نفّذ: بحثت في Chrome عن «$query»."
        })
    }

    private fun searchWeb(query: String, images: Boolean = false) {
        val url = if (images) "https://www.google.com/search?tbm=isch&q=${Uri.encode(query)}" else "https://www.google.com/search?q=${Uri.encode(query)}"
        launchExternal(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        appendLog(if (images) "نفّذ: فتحت نتائج الصور عن «$query»." else "نفّذ: بحثت على الويب عن «$query».")
    }

    private fun searchCurrentOrWeb(query: String) {
        val current = NaffithAccessibilityService.latestScreenPackage
        if (current.isNotBlank() && current != packageName) searchCurrentApp(query) else searchWeb(query)
    }

    private fun searchCurrentApp(query: String) {
        var targetPackage = NaffithAccessibilityService.latestScreenPackage
        val fileApp = catalog.findFiles()
        if (targetPackage.isBlank() || targetPackage == packageName) targetPackage = fileApp?.packageName.orEmpty()
        if (targetPackage.isBlank()) { searchWeb(query); return }
        val queued = NaffithAccessibilityService.requestSearch(targetPackage, query)
        if (targetPackage == fileApp?.packageName && NaffithAccessibilityService.latestScreenPackage != targetPackage && fileApp != null) {
            launchExternal(fileApp.intent)
        }
        appendLog(if (queued) "نفّذ: سأبحث عن «$query» داخل التطبيق المفتوح." else "نفّذ: فعّل إمكانية الوصول للبحث داخل التطبيق المفتوح.")
    }

    private fun writeText(action: LocalAction.Write) {
        val packageName = action.appQuery?.let { catalog.find(it)?.packageName } ?: NaffithAccessibilityService.latestScreenPackage
        if (packageName.isBlank()) { appendLog("نفّذ: افتح التطبيق أولًا أو اكتب «اكتب في كروم ...»."); return }
        val queued = NaffithAccessibilityService.requestWrite(packageName, action.text, action.submit)
        val app = action.appQuery?.let { catalog.find(it) }
        if (app != null) launchExternal(app.intent)
        appendLog(if (queued) "نفّذ: سأكتب «${action.text}»${if (action.submit) " ثم أرسلها." else "."}" else "نفّذ: فعّل إمكانية الوصول لكي أكتب داخل التطبيق.")
    }

    private fun clickLabel(label: String) {
        val packageName = NaffithAccessibilityService.latestScreenPackage
        val queued = if (packageName.isBlank()) false else NaffithAccessibilityService.requestClick(packageName, label)
        appendLog(if (queued) "نفّذ: سأضغط «$label»." else "نفّذ: لا توجد شاشة مستهدفة. افتح التطبيق أولًا.")
    }

    private fun openFolder(folder: String) {
        val fileApp = catalog.findFiles() ?: catalog.find("الملفات")
        if (fileApp == null) {
            appendLog("نفّذ: لم أجد تطبيق إدارة الملفات لفتح مجلد «$folder».")
            return
        }
        val alreadyOpen = NaffithAccessibilityService.latestScreenPackage == fileApp.packageName
        val queued = NaffithAccessibilityService.requestClick(fileApp.packageName, folder)
        if (!alreadyOpen && !launchExternal(fileApp.intent)) {
            appendLog("نفّذ: لا أستطيع تشغيل ${fileApp.label}.")
            return
        }
        appendLog(if (queued) "نفّذ: فتحت ${fileApp.label} وسأدخل مجلد «$folder»."
        else "نفّذ: فتحت ${fileApp.label}. فعّل إمكانية الوصول لدخول المجلد تلقائيًا.")
    }

    private fun openFiles() {
        val fileApp = catalog.findFiles() ?: catalog.find("الملفات")
        if (fileApp != null) {
            if (launchExternal(fileApp.intent)) { appendLog("نفّذ: تم فتح ${fileApp.label}"); return }
        }
        // هذا مسار احتياطي فقط للأجهزة التي لا تثبت أي تطبيق ملفات مستقل.
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
        if (launchExternal(intent)) appendLog("نفّذ: لم أجد تطبيق ملفات مستقلًا، ففتحت منتقي الملفات.")
        else appendLog("نفّذ: لم أجد مدير ملفات متاحًا.")
    }

    private fun openSettings() { launchExternal(Intent(Settings.ACTION_SETTINGS)); appendLog("نفّذ: تم فتح الإعدادات.") }

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

    /** يفرض تشغيل التطبيق الهدف في مهمة خارجية بدل أي مسار داخل واجهة نفّذ. */
    private fun launchExternal(intent: Intent): Boolean {
        val external = Intent(intent).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        )
        return try {
            startActivity(external)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    companion object { private const val REQUEST_OPEN_FILE = 1001 }
}
