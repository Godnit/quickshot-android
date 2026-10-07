package com.example.naffith

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val brain: LocalBrain = RuleBasedArabicBrain()
    private lateinit var commandInput: EditText
    private lateinit var logView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        commandInput = findViewById(R.id.commandInput)
        logView = findViewById(R.id.logView)
        val executeButton: Button = findViewById(R.id.executeButton)

        executeButton.setOnClickListener {
            val text = commandInput.text.toString()
            executeCommand(text)
        }

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
            is LocalAction.OpenApp -> openApp(action.packageName, action.label)
            is LocalAction.SearchYoutube -> searchYoutube(action.query)
            LocalAction.OpenFiles -> openFiles()
            LocalAction.OpenSettings -> openSettings()
            LocalAction.Help -> showHelp()
            is LocalAction.Unknown -> appendLog("نفّذ: لم أفهم الأمر بعد. اكتب «مساعدة» لرؤية الأوامر المتاحة.")
        }
        commandInput.text.clear()
    }

    private fun openApp(packageName: String, label: String) {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent == null) {
            appendLog("نفّذ: تطبيق $label غير مثبت، سأحاول فتحه من المتصفح.")
            if (label == "يوتيوب") searchYoutube("")
            return
        }
        startActivity(launchIntent)
        appendLog("نفّذ: تم فتح $label")
    }

    private fun searchYoutube(query: String) {
        val url = if (query.isBlank()) {
            "https://www.youtube.com"
        } else {
            "https://www.youtube.com/results?search_query=${Uri.encode(query)}"
        }
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

    private fun showHelp() {
        appendLog(
            "الأوامر المتاحة حاليًا:\n" +
                "• افتح يوتيوب\n" +
                "• ابحث في يوتيوب عن تعلم البرمجة\n" +
                "• افتح Chrome\n" +
                "• افتح واتساب\n" +
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
