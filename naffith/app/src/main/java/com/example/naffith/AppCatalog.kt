package com.example.naffith

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.DocumentsContract
import android.provider.MediaStore

data class InstalledApp(val packageName: String, val label: String, val intent: Intent)

class AppCatalog(private val context: Context) {
    private val pm = context.packageManager

    private val fileManagerPackages = listOf(
        "com.google.android.apps.nbu.files",
        "com.google.android.documentsui",
        "com.android.documentsui",
        "com.sec.android.app.myfiles",
        "com.mi.android.globalFileexplorer",
        "com.android.filemanager",
        "com.oneplus.filemanager",
        "com.coloros.filemanager",
        "com.vivo.filemanager",
        "com.huawei.hidisk"
    )

    @Suppress("DEPRECATION")
    fun all(): List<InstalledApp> {
        val apps = LinkedHashMap<String, InstalledApp>()
        fun addHandlers(intent: Intent) {
            pm.queryIntentActivities(intent, 0).forEach { info ->
                val activity = info.activityInfo ?: return@forEach
                if (!activity.enabled || !activity.exported) return@forEach
                val launch = Intent(intent).setComponent(ComponentName(activity.packageName, activity.name))
                val label = info.loadLabel(pm).toString().trim()
                apps.putIfAbsent(activity.packageName, InstalledApp(activity.packageName, label, launch))
            }
        }
        addHandlers(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER))
        pm.getInstalledApplications(0).forEach { info ->
            if (info.enabled && !apps.containsKey(info.packageName)) {
                val launch = pm.getLaunchIntentForPackage(info.packageName) ?: return@forEach
                apps[info.packageName] = InstalledApp(info.packageName, info.loadLabel(pm).toString().trim(), launch)
            }
        }
        // تضاف تطبيقات النظام حتى إذا كانت بلا اختصار Launcher عادي.
        addHandlers(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
        addHandlers(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALCULATOR))
        addHandlers(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_GALLERY))
        addHandlers(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_FILES))
        return apps.values.sortedBy { ArabicText.normalize(it.label) }
    }

    fun find(query: String): InstalledApp? {
        val apps = all()
        val match = AppNameMatcher.find(query, apps.map { AppName(it.packageName, it.label) }) ?: return null
        return apps.firstOrNull { it.packageName == match.packageName }
    }

    fun findPackage(packageName: String): InstalledApp? =
        all().firstOrNull { it.packageName == packageName }

    private fun fileRootIntent(intent: Intent): Intent = Intent(intent).apply {
        // تتجاهله تطبيقات الملفات التي لا تدعم DocumentsContract، بينما تمنع
        // DocumentsUI من استعادة آخر موقع (التنزيلات) عند توفر الدعم.
        putExtra(
            DocumentsContract.EXTRA_INITIAL_URI,
            DocumentsContract.buildRootUri("com.android.externalstorage.documents", "primary")
        )
    }

    /**
     * يختار تطبيق إدارة الملفات الحقيقي.  ACTION_OPEN_DOCUMENT هو منتقي ملفات
     * يبدأ غالبًا من Downloads، لذلك لا نستخدمه كاختيار أول للأمر «افتح الملفات».
     */
    fun findFiles(): InstalledApp? {
        val filesIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_FILES)
        val handlers = pm.queryIntentActivities(filesIntent, 0)
            .filter { info ->
                val pkg = info.activityInfo?.packageName.orEmpty()
                pkg.isNotBlank() && pkg != context.packageName && !pkg.contains("downloads", ignoreCase = true)
            }

        fun fromHandler(info: android.content.pm.ResolveInfo): InstalledApp? {
            val activity = info.activityInfo ?: return null
            if (!activity.enabled || !activity.exported) return null
            val launch = Intent(filesIntent).setComponent(ComponentName(activity.packageName, activity.name))
            return InstalledApp(activity.packageName, info.loadLabel(pm).toString().trim(), launch)
        }

        handlers.sortedWith(
            compareBy<android.content.pm.ResolveInfo> {
                val pkg = it.activityInfo?.packageName.orEmpty()
                val preferred = fileManagerPackages.indexOfFirst { candidate -> candidate.equals(pkg, ignoreCase = true) }
                if (preferred < 0) Int.MAX_VALUE else preferred
            }.thenBy { it.loadLabel(pm).toString() }
        ).firstNotNullOfOrNull(::fromHandler)?.let { handler ->
            // إذا كان للتطبيق اختصار تشغيل عادي فله أولوية؛ بعض نسخ DocumentsUI
            // تستقبل CATEGORY_APP_FILES لكنها تعيد فتح آخر مجلد (غالبًا التنزيلات).
            val launcher = pm.getLaunchIntentForPackage(handler.packageName)
            return if (launcher != null) handler.copy(intent = fileRootIntent(launcher))
            else handler.copy(intent = fileRootIntent(handler.intent))
        }

        // بعض واجهات الشركات لا تعلن CATEGORY_APP_FILES لكنها تملك اختصار تشغيل.
        for (pkg in fileManagerPackages) {
            val launch = pm.getLaunchIntentForPackage(pkg) ?: continue
            val info = try { pm.getApplicationInfo(pkg, 0) } catch (_: PackageManager.NameNotFoundException) { continue }
            if (info.enabled) return InstalledApp(pkg, info.loadLabel(pm).toString().trim(), fileRootIntent(launch))
        }

        return all().firstOrNull { app ->
            val label = ArabicText.normalize(app.label)
            val pkg = app.packageName.lowercase()
            !pkg.contains("download") && (label.contains("file") || label.contains("ملف") || label.contains("files"))
        }
    }
}
