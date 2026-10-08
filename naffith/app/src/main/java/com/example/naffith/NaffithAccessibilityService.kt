package com.example.naffith

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.ComponentName
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

/** خطة قصيرة ومحددة بطلب المستخدم، مع انتظار الشاشة وتوقف عند غياب العناصر. */
class NaffithAccessibilityService : AccessibilityService() {
    private enum class Kind { SEARCH, WRITE, CALCULATE, PLAY, DOWNLOAD_IMAGE, CLICK, NAVIGATE, OPEN_FILES, OPEN_FOLDER }
    private data class Job(
        val packageName: String,
        val kind: Kind,
        val text: String,
        val playFirst: Boolean = false,
        val submit: Boolean = false,
        val downloadCount: Int = 1,
        var downloaded: Int = 0,
        var route: List<UiTransition> = emptyList(),
        var routeIndex: Int = 0
    )
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var learningStore: AppLearningStore
    private var job: Job? = null
    private var stage = 0
    private var keyIndex = 0
    private var attempts = 0
    private var nextAt = 0L
    private var deadline = 0L
    private var beforeSubmit = ""
    private var beforePlay = ""
    private var imageRect: Rect? = null
    private val downloadedImageKeys = linkedSetOf<String>()
    private val tick = object : Runnable {
        override fun run() {
            if (job == null) return
            runStep()
            if (job != null) handler.postDelayed(this, 100L)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        learningStore = AppLearningStore(this)
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or
                AccessibilityEvent.TYPE_VIEW_CLICKED or AccessibilityEvent.TYPE_VIEW_SCROLLED or
                AccessibilityEvent.TYPE_VIEW_SELECTED or AccessibilityEvent.TYPE_WINDOWS_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // لقطة نصية محلية فقط لآخر تطبيق غير نفّذ؛ لا تحفظ الشاشة أو ترسلها لخادم.
        if (event != null) {
            val root = rootInActiveWindow ?: return
            if (root.packageName?.toString() != packageName) {
                val nodes = flatten(root)
                val screenPackage = root.packageName?.toString().orEmpty()
                val via = if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
                    event.source?.let { source ->
                        val selector = UiSemantics.selector(uiElement(source, -1))
                        source.recycle()
                        selector
                    }
                } else null
                latestScreenText = nodes.map(::nodeText).filter { it.isNotBlank() }.distinct().joinToString("\n").take(6000)
                val appLabel = try {
                    packageManager.getApplicationInfo(screenPackage, 0).loadLabel(packageManager).toString()
                } catch (_: Exception) { screenPackage }
                // تعلّم محليًا أسماء العناصر وأدوارها حتى تعمل الأوامر العامة
                // داخل التطبيقات التي لم تكن معروفة وقت بناء APK.
                learningStore.observe(screenPackage, appLabel, nodes, via)
                latestScreenPackage = root.packageName?.toString().orEmpty()
                recycle(nodes)
            } else root.recycle()
        }
    }

    override fun onInterrupt() { finish("توقف التنفيذ بسبب مقاطعة إمكانية الوصول.") }
    override fun onDestroy() {
        handler.removeCallbacks(tick)
        job = null
        instance = null
        super.onDestroy()
    }

    private fun begin(request: Job) {
        handler.removeCallbacks(tick)
        job = request
        stage = 0
        keyIndex = 0
        attempts = 0
        beforeSubmit = ""
        beforePlay = ""
        imageRect = null
        downloadedImageKeys.clear()
        currentRouteReset(request)
        val now = SystemClock.uptimeMillis()
        // طلب «ابحث وشغّل» يرسل ACTION_SEARCH من MainActivity أولًا؛ إعطاء
        // يوتيوب مهلة لعرض صفحة النتائج يمنع خدمة التشغيل من النقر على أول
        // فيديو في الصفحة الرئيسية قبل وصول نتيجة البحث.
        nextAt = now + if (request.kind == Kind.PLAY) 1400L else 220L
        deadline = now + 30000L
        handler.postDelayed(tick, 100L)
    }

    private fun currentRouteReset(request: Job) {
        request.route = emptyList()
        request.routeIndex = 0
    }

    private fun runStep() {
        val current = job ?: return
        val now = SystemClock.uptimeMillis()
        if (now >= deadline) {
            finish("لم يكتمل الأمر: لم أجد عنصرًا مناسبًا على الشاشة. قد يكون التطبيق لا يعرضه لإمكانية الوصول.")
            return
        }
        if (now < nextAt) return
        val root = targetRoot(current.packageName) ?: return
        val nodes = flatten(root)
        try {
            when (current.kind) {
                Kind.SEARCH, Kind.WRITE -> searchStep(current, nodes)
                Kind.CALCULATE -> calculatorStep(current, nodes)
                Kind.PLAY -> playStep(current, nodes)
                Kind.DOWNLOAD_IMAGE -> imageStep(current, nodes)
                Kind.NAVIGATE -> navigateStep(current, nodes)
                Kind.OPEN_FILES, Kind.OPEN_FOLDER -> fileManagerStep(current, nodes)
                Kind.CLICK -> {
                    val label = current.text
                    val semantic = UiSemantics.choose(uiElements(nodes), label)
                    val node = semantic?.let { nodes.getOrNull(it.actionIndex) }
                        ?: nodes.firstOrNull { it.isVisibleToUser && !it.isEditable &&
                        (AutomationRules.clickCandidate(nodeText(it), id(it), label) ||
                            (ArabicText.normalize(label) in setOf("انتر", "enter", "بحث", "تم") && AutomationRules.submitButton(nodeText(it), id(it)))) && clickable(it) }
                    if (node != null && click(node)) finish("ضغطت «${current.text}».")
                }
            }
        } finally { recycle(nodes) }
    }

    private fun targetRoot(target: String): AccessibilityNodeInfo? {
        fun matches(pkg: String?): Boolean {
            val actual = pkg.orEmpty()
            if (actual == target) return true
            // MX Player قد يبدّل بين نسخة الإعلانات والنسخة الاحترافية أو
            // يفتح Activity من حزمة شقيقة؛ كلاهما نفس التطبيق للمستخدم.
            if (target.startsWith("com.mxtech") && actual.startsWith("com.mxtech")) return true
            return false
        }
        val active = rootInActiveWindow
        if (matches(active?.packageName?.toString())) return active
        active?.recycle()
        for (window in windows) {
            val root = window.root ?: continue
            if (matches(root.packageName?.toString())) return root
            root.recycle()
        }
        return null
    }

    private fun searchStep(current: Job, nodes: List<AccessibilityNodeInfo>) {
        val editable = nodes.filter { it.isVisibleToUser && it.isEditable && !it.isPassword }
        val semanticInput = UiSemantics.input(uiElements(nodes), current.kind == Kind.SEARCH)
        val input = semanticInput?.let { nodes.getOrNull(it.index) }
            ?: nodes.firstOrNull { it.isVisibleToUser && it.isEditable && !it.isPassword && it.isFocused }
            ?: nodes.firstOrNull { it.isVisibleToUser && it.isEditable && !it.isPassword && id(it).contains("search") }
            ?: if (current.kind == Kind.WRITE) {
                editable.firstOrNull {
                    val name = id(it)
                    name.contains("message") || name.contains("prompt") || name.contains("compose") ||
                        name.contains("chat") || name.contains("input") || name.contains("edittext")
                } ?: editable.maxByOrNull { bounds(it).bottom }
            } else editable.singleOrNull()
        when (stage) {
            0 -> {
                if (input != null) { stage = 1; return }
                // افتح حقل البحث إذا كان الأمر بحثًا أو كتابة في تطبيق ذي بحث.
                val button = nodes.firstOrNull { !it.isEditable && it.isVisibleToUser && clickable(it) &&
                    (AutomationRules.submitButton(nodeText(it), id(it)) || id(it).substringAfterLast('/') in setOf("search", "menu_search", "action_search", "search_btn")) }
                if (button != null && click(button)) waitFor(1, 280L)
                else {
                    // بعض إصدارات MX/يوتيوب تعرض أيقونة البحث كرسم بلا نص أو
                    // رقم مورد؛ انقر موضع شريط البحث كحل احتياطي محدود.
                    attempts++
                    if (attempts == 3 && tapTopBar(right = true)) waitFor(0, 520L)
                    else if (attempts == 10 && tapTopBar(right = false)) waitFor(0, 520L)
                }
            }
            1 -> {
                if (input == null) return
                input.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, current.text) }
                if (input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
                    if (current.kind == Kind.WRITE && !current.submit) finish("كتبت النص المطلوب داخل التطبيق.")
                    else waitFor(2, 180L)
                }
            }
            2 -> {
                // بعض المشغلات ترشح الملفات مباشرة بمجرد الكتابة.
                val mx = current.packageName.startsWith("com.mxtech")
                val filtered = mx && resultNodes(current, nodes).isNotEmpty()
                beforeSubmit = screenSignature(nodes)
                val submitted = filtered || submitInput(input, nodes)
                if (submitted) {
                    if (current.playFirst) { beforePlay = ""; waitFor(3, 650L) }
                    else if (filtered) finish("كتبت «${current.text}» وظهرت ملفات مطابقة.")
                    else waitFor(4, 260L)
                } else if (++attempts > 10) {
                    finish("كتبت «${current.text}»، لكن لم أجد زر بحث/Enter متاحًا. اضغطه يدويًا في هذا التطبيق.")
                }
            }
            3 -> playStep(current, nodes)
            4 -> {
                if (screenSignature(nodes) != beforeSubmit || nodes.none { it.isEditable && it.isFocused }) {
                    finish("أرسلت البحث عن «${current.text}».")
                }
            }
            5 -> verifyPlayback(current, nodes)
        }
    }

    private fun submitInput(input: AccessibilityNodeInfo?, nodes: List<AccessibilityNodeInfo>): Boolean {
        if (Build.VERSION.SDK_INT >= 30 && input != null && input.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)) return true
        val button = nodes.firstOrNull { it.isVisibleToUser && !it.isEditable && clickable(it) && AutomationRules.submitButton(nodeText(it), id(it)) }
        if (button != null && click(button)) return true
        // Android 8.1: اضغط زر بحث/تم في الكيبورد من شجرة إمكانية الوصول.
        for (window in windows) {
            if (window.type != AccessibilityWindowInfo.TYPE_INPUT_METHOD) continue
            val root = window.root ?: continue
            val keys = flatten(root)
            try {
                val enter = keys.firstOrNull { it.isVisibleToUser && AutomationRules.submitButton(nodeText(it), id(it)) }
                if (enter != null && click(enter)) return true
            } finally { recycle(keys) }
        }
        return false
    }

    private fun calculatorStep(current: Job, nodes: List<AccessibilityNodeInfo>) {
        if (stage == 0) {
            val clear = nodes.firstOrNull { it.isVisibleToUser && clickable(it) && AutomationRules.isClear(nodeText(it), id(it)) }
            if (clear != null && click(clear)) { waitFor(1, 100L); return }
            val input = nodes.firstOrNull { it.isEditable && !it.isPassword && it.isVisibleToUser }
            if (input != null) {
                val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, current.text) }
                if (input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) { keyIndex = current.text.length; waitFor(1, 90L) }
            } else {
                // الآلات الحاسبة ذات لوحة الأزرار لا تملك EditText؛ انتقل مباشرة إلى الأزرار.
                waitFor(1, 90L)
            }
            return
        }
        val keys = current.text + "="
        if (keyIndex >= keys.length) { finish("أدخلت ${current.text} وضغطت يساوي في الحاسبة."); return }
        val expected = keys[keyIndex]
        val key = nodes.firstOrNull { it.isVisibleToUser && !it.isEditable && clickable(it) && AutomationRules.calculatorKey(nodeText(it), id(it), expected) }
        if (key != null && click(key)) {
            keyIndex++
            nextAt = SystemClock.uptimeMillis() + 85L
            if (keyIndex == keys.length) finish("أدخلت ${current.text} وضغطت يساوي في الحاسبة.")
        }
    }

    private fun resultNodes(current: Job, nodes: List<AccessibilityNodeInfo>): List<AccessibilityNodeInfo> {
        val mx = current.packageName.startsWith("com.mxtech")
        return nodes.filter { it.isVisibleToUser && !it.isEditable && clickable(it) &&
            AutomationRules.playCandidate(nodeText(it), id(it), current.text, mx) }
            .sortedBy { bounds(it).top }
    }

    private fun playStep(current: Job, nodes: List<AccessibilityNodeInfo>) {
        if (stage == 5) { verifyPlayback(current, nodes); return }
        val candidate = resultNodes(current, nodes).firstOrNull() ?: return
        beforePlay = screenSignature(nodes)
        if (click(candidate)) {
            // نجاح الضغط هو بداية التشغيل. لا نُبقي الخطة معلقة بانتظار
            // إشارات تختلف من نسخة تطبيق إلى أخرى.
            attempts = 0
            waitFor(5, 650L)
        }
    }

    private fun verifyPlayback(current: Job, nodes: List<AccessibilityNodeInfo>) {
        val pausedControl = nodes.any { ArabicText.normalize(nodeText(it)) in setOf("pause", "pause video", "pause playback", "ايقاف مؤقت", "ايقاف التشغيل مؤقتا") }
        val fullscreenPlayer = current.packageName.startsWith("com.mxtech") && nodes.any { id(it).contains("video_player") || id(it).contains("player_layout") }
        if (pausedControl || fullscreenPlayer) finish("فتحت نتيجة «${current.text}» وظهر مشغل الوسائط.")
        else if (screenSignature(nodes) != beforePlay) finish("ضغطت نتيجة «${current.text}». إذا توقفت عند إعلان أو شاشة اختيار، أكملها يدويًا.")
        else if (++attempts >= 8) finish("بدأت تشغيل «${current.text}» ويمكن متابعة الأمر التالي.")
    }

    private fun navigateStep(current: Job, nodes: List<AccessibilityNodeInfo>) {
        val elements = uiElements(nodes)
        val semanticCandidate = UiSemantics.choose(elements, current.text)
            ?.takeIf { UiSemantics.isNavigation(it) }
            ?.let { nodes.getOrNull(it.actionIndex) }
        val candidate = semanticCandidate ?: nodes.asSequence()
            .filter { it.isVisibleToUser && !it.isEditable && clickable(it) }
            .filter { AutomationRules.navigationCandidate(nodeText(it), id(it), current.text) }
            .sortedBy { bounds(it).top }
            .firstOrNull()
        if (candidate != null && click(candidate)) {
            finish("فتحت قسم «${current.text}».")
        } else {
            attempts++
            if (current.route.isEmpty() && current.routeIndex == 0) {
                current.route = learningStore.route(current.packageName, UiSemantics.screenKey(elements), current.text)
            }
            val edge = current.route.getOrNull(current.routeIndex)
            if (edge != null) {
                val routeNode = elements.mapIndexedNotNull { index, element ->
                    if (UiSemantics.matches(UiSemantics.selector(element), edge.via.semantic, false) ||
                        (edge.via.viewId.isNotBlank() && element.viewId == edge.via.viewId)) nodes.getOrNull(index) else null
                }.firstOrNull { it.isVisibleToUser && clickable(it) }
                if (routeNode != null && click(routeNode)) {
                    current.routeIndex++
                    waitFor(0, 650L)
                    return
                }
            }
            val hasPause = nodes.any { ArabicText.normalize(nodeText(it)) in setOf("pause", "pause video", "ايقاف مؤقت", "ايقاف التشغيل مؤقتا") }
            val looksLikeResults = nodes.any {
                val value = ArabicText.normalize(nodeText(it))
                value.contains("نتائج") || value.contains("results") || id(it).contains("search_result")
            }
            if (current.text == "like" && attempts >= 3 && looksLikeResults && !hasPause) {
                finish("لم أجد زر الإعجاب في صفحة النتائج؛ يجب تشغيل الفيديو أولًا.")
                return
            }
            if (current.text == "like" && current.packageName == "com.google.android.youtube" && attempts <= 8) {
                // إذا طُلب الإعجاب من صفحة النتائج، افتح أول فيديو ثم أعد
                // البحث عن زر الإعجاب بعد ظهور صفحة المشاهدة.
                val firstVideo = nodes.firstOrNull { it.isVisibleToUser && !it.isEditable && clickable(it) &&
                    AutomationRules.playCandidate(nodeText(it), id(it), "", mx = false) }
                if (firstVideo != null && click(firstVideo)) waitFor(0, 900L)
            }
            // زر الإعجاب أسفل عنوان الفيديو في تطبيق يوتيوب وقد لا يظهر قبل
            // تمرير الشاشة قليلًا. أعد الفحص بعد تمريرين بدل إنهاء الخطة فورًا.
            if (current.text == "like" && attempts in setOf(8, 16) && hasPause) swipeUp()
            if (attempts > 22) finish("لم أجد زر قسم «${current.text}» في التطبيق الحالي.")
        }
    }

    private fun imageStep(current: Job, nodes: List<AccessibilityNodeInfo>) {
        when (stage) {
            0 -> {
                val images = imageNodes(nodes).filter { imageKey(it) !in downloadedImageKeys }
                if (images.isEmpty()) return
                val selected = images[kotlin.random.Random.nextInt(images.size)]
                imageRect = bounds(selected)
                beforePlay = imageKey(selected)
                if (click(selected)) waitFor(1, 700L)
            }
            1 -> {
                val preview = imageNodes(nodes).maxByOrNull { bounds(it).width() * bounds(it).height() } ?: return
                val accepted = preview.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK) || touch(preview, 850L)
                if (accepted) waitFor(2, 850L)
            }
            2 -> {
                val download = nodes.firstOrNull { it.isVisibleToUser &&
                    (AutomationRules.downloadButton(nodeText(it)) || id(it).contains("download") || id(it).contains("save")) && clickable(it) }
                if (download != null && click(download)) {
                    downloadedImageKeys += beforePlay
                    current.downloaded++
                    if (current.downloaded >= current.downloadCount) {
                        finish("نزّلت ${current.downloaded} صور من «${current.text}». تحقق من مجلد التنزيلات.")
                    } else {
                        // ارجع إلى شبكة النتائج ثم اختر صورة مختلفة للعدد المطلوب.
                        performGlobalAction(GLOBAL_ACTION_BACK)
                        waitFor(0, 650L)
                    }
                }
                else if (++attempts % 8 == 0) {
                    // Chrome قد يغلق قائمة الضغط المطول أثناء انتقال الصورة؛ أعد فتحها قبل الفشل.
                    val preview = imageNodes(nodes).maxByOrNull { bounds(it).width() * bounds(it).height() }
                    if (preview != null) {
                        preview.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
                        touch(preview, 850L)
                    }
                } else if (attempts > 45) finish("فتحت نتائج الصور، لكن المتصفح لم يعرض خيار تنزيل الصورة. احفظ الصورة يدويًا.")
            }
        }
    }

    /** يخرج من مجلد التنزيلات إلى واجهة مدير الملفات قبل تنفيذ أي أمر داخله. */
    private fun fileManagerStep(current: Job, nodes: List<AccessibilityNodeInfo>) {
        val texts = nodes.map(::nodeText).map(ArabicText::normalize).filter { it.isNotBlank() }
        val downloadsScreen = texts.any { it == "تنزيلات" || it == "التنزيلات" || it == "downloads" }
        val targetFolder = current.kind == Kind.OPEN_FOLDER

        if (!downloadsScreen && stage == 0) {
            if (!targetFolder) {
                finish("تم فتح إدارة الملفات من الجذر.")
                return
            }
            val folder = nodes.firstOrNull { it.isVisibleToUser && clickable(it) &&
                AutomationRules.clickCandidate(nodeText(it), id(it), current.text) }
            if (folder != null && click(folder)) {
                finish("تم فتح مجلد «${current.text}» داخل إدارة الملفات.")
            } else if (++attempts > 35) {
                finish("فتحت إدارة الملفات، لكن لم أجد مجلد «${current.text}».")
            }
            return
        }

        when (stage) {
            0 -> {
                val menu = nodes.firstOrNull { it.isVisibleToUser && clickable(it) &&
                    (id(it).contains("drawer") || id(it).contains("navigation") ||
                        id(it).contains("roots") || ArabicText.normalize(nodeText(it)) in
                        setOf("القائمه", "القائمة", "menu", "مواقع", "locations")) }
                if (menu != null && click(menu)) waitFor(1, 450L)
                else if (attempts == 3 && tapFileManagerMenu(right = true)) waitFor(1, 650L)
                else if (attempts == 10 && tapFileManagerMenu(right = false)) waitFor(1, 650L)
                else if (++attempts > 30) finish("فتحت إدارة الملفات، لكن لم أستطع مغادرة مجلد التنزيلات.")
            }
            1 -> {
                val storage = nodes.firstOrNull { it.isVisibleToUser && clickable(it) &&
                    (AutomationRules.clickCandidate(nodeText(it), id(it), "التخزين الداخلي") ||
                        ArabicText.normalize(nodeText(it)) in setOf("الرئيسيه", "الرئيسية", "home", "جهاز", "device")) }
                if (storage != null && click(storage)) waitFor(2, 500L)
                else if (attempts == 10 && tapFileManagerMenu(right = false)) {
                    attempts = 0
                    waitFor(1, 650L)
                }
                else if (++attempts > 30) finish("فتحت مدير الملفات، لكن لم أجد الذاكرة الداخلية.")
            }
            2 -> {
                if (!targetFolder) {
                    if (!downloadsScreen) finish("تم فتح إدارة الملفات من الجذر.")
                } else {
                    val folder = nodes.firstOrNull { it.isVisibleToUser && clickable(it) &&
                        AutomationRules.clickCandidate(nodeText(it), id(it), current.text) }
                    if (folder != null && click(folder)) finish("تم فتح مجلد «${current.text}» داخل إدارة الملفات.")
                    else if (++attempts > 30) finish("فتحت إدارة الملفات، لكن لم أجد مجلد «${current.text}».")
                }
            }
        }
    }

    private fun imageNodes(nodes: List<AccessibilityNodeInfo>): List<AccessibilityNodeInfo> {
        val density = resources.displayMetrics.density
        return nodes.filter {
            val r = bounds(it)
            val label = ArabicText.normalize(nodeText(it))
            it.isVisibleToUser && it.className?.toString()?.contains("Image") == true &&
                r.width() >= 70 * density && r.height() >= 70 * density && r.top >= 80 * density &&
                listOf("google", "logo", "شعار", "avatar", "حساب").none { word -> label.contains(word) }
        }
    }

    private fun imageKey(node: AccessibilityNodeInfo): String {
        val r = bounds(node)
        return "${r.left}:${r.top}:${r.right}:${r.bottom}:${nodeText(node)}"
    }

    private fun waitFor(newStage: Int, delay: Long) { stage = newStage; nextAt = SystemClock.uptimeMillis() + delay }
    private fun screenSignature(nodes: List<AccessibilityNodeInfo>): String = nodes.map(::nodeText).filter { it.isNotBlank() }.distinct().joinToString("|").take(7000)
    private fun id(node: AccessibilityNodeInfo) = node.viewIdResourceName.orEmpty().lowercase()
    private fun nodeText(node: AccessibilityNodeInfo): String = listOfNotNull(node.text?.toString(), node.contentDescription?.toString()).filter { it.isNotBlank() }.distinct().joinToString(" ")
    private fun bounds(node: AccessibilityNodeInfo) = Rect().also { node.getBoundsInScreen(it) }
    private fun uiElement(node: AccessibilityNodeInfo, index: Int): UiElement {
        val rect = bounds(node)
        return UiElement(
            index = index,
            actionIndex = index,
            label = nodeText(node),
            hint = node.hintText?.toString().orEmpty(),
            viewId = id(node),
            editable = node.isEditable,
            password = node.isPassword,
            clickable = clickable(node),
            scrollable = node.isScrollable,
            focused = node.isFocused,
            selected = node.isSelected,
            enabled = node.isEnabled,
            left = rect.left,
            top = rect.top,
            right = rect.right,
            bottom = rect.bottom
        )
    }
    private fun uiElements(nodes: List<AccessibilityNodeInfo>): List<UiElement> =
        nodes.mapIndexed { index, node -> uiElement(node, index) }
    private fun clickable(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable) return true
        var parent = node.parent
        repeat(3) {
            val p = parent ?: return false
            val click = p.isClickable
            parent = p.parent
            p.recycle()
            if (click) { parent?.recycle(); return true }
        }
        parent?.recycle()
        return false
    }

    private fun click(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        var parent = node.parent
        repeat(3) {
            val p = parent ?: return@repeat
            if (p.isClickable && p.performAction(AccessibilityNodeInfo.ACTION_CLICK)) { p.recycle(); return true }
            parent = p.parent
            p.recycle()
        }
        parent?.recycle()
        return false
    }

    private fun touch(node: AccessibilityNodeInfo, duration: Long): Boolean {
        val r = bounds(node)
        if (r.isEmpty) return false
        val path = Path().apply { moveTo(r.exactCenterX(), r.exactCenterY()) }
        val gesture = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0L, duration)).build()
        return dispatchGesture(gesture, null, handler)
    }

    private fun tapFileManagerMenu(right: Boolean): Boolean {
        val density = resources.displayMetrics.density
        val width = resources.displayMetrics.widthPixels.toFloat()
        val x = if (right) width - 28f * density else 28f * density
        val y = 54f * density
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 60L))
            .build()
        return dispatchGesture(gesture, null, handler)
    }

    private fun tapTopBar(right: Boolean): Boolean {
        val density = resources.displayMetrics.density
        val width = resources.displayMetrics.widthPixels.toFloat()
        val x = if (right) width - 34f * density else 34f * density
        val y = 58f * density
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 60L))
            .build()
        return dispatchGesture(gesture, null, handler)
    }

    private fun swipeUp(): Boolean {
        val density = resources.displayMetrics.density
        val width = resources.displayMetrics.widthPixels.toFloat()
        val height = resources.displayMetrics.heightPixels.toFloat()
        val path = Path().apply {
            moveTo(width * 0.52f, height * 0.78f)
            lineTo(width * 0.52f, height * 0.38f)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, (260L * density).toLong().coerceAtMost(500L)))
            .build()
        return dispatchGesture(gesture, null, handler)
    }

    private fun flatten(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val result = ArrayList<AccessibilityNodeInfo>()
        fun walk(node: AccessibilityNodeInfo, depth: Int) {
            result.add(node)
            if (depth >= 30 || result.size >= 1800) return
            for (i in 0 until node.childCount) { if (result.size >= 1800) break; node.getChild(i)?.let { walk(it, depth + 1) } }
        }
        walk(root, 0)
        return result
    }
    @Suppress("DEPRECATION") private fun recycle(nodes: List<AccessibilityNodeInfo>) { nodes.forEach { it.recycle() } }

    private fun finish(message: String) {
        job = null
        handler.removeCallbacks(tick)
        getSharedPreferences("naffith", MODE_PRIVATE).edit().putString("automation_result", message).apply()
    }

    companion object {
        private var instance: NaffithAccessibilityService? = null
        var latestScreenText = ""
            private set
        var latestScreenPackage = ""
            private set
        fun isEnabled(context: Context): Boolean {
            val expected = ComponentName(context, NaffithAccessibilityService::class.java)
            return Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?.split(':')?.mapNotNull(ComponentName::unflattenFromString)?.any { it == expected } == true
        }
        private fun request(job: Job): Boolean { val service = instance ?: return false; service.begin(job); return true }
        fun isBusy(): Boolean = instance?.job != null
        fun learnedAppDescription(packageName: String): String? = instance?.learningStore?.describe(packageName)
        fun learnedControls(packageName: String): List<LearnedControl> = instance?.learningStore?.controls(packageName).orEmpty()
        fun requestSearch(packageName: String, query: String, playFirst: Boolean = false) = request(Job(packageName, Kind.SEARCH, query, playFirst))
        fun requestWrite(packageName: String, text: String, submit: Boolean) = request(Job(packageName, Kind.WRITE, text, submit = submit))
        fun requestCalculator(packageName: String, expression: String) = request(Job(packageName, Kind.CALCULATE, expression))
        fun requestPlayFirst(packageName: String, query: String) = request(Job(packageName, Kind.PLAY, query))
        fun requestNavigate(packageName: String, section: String) = request(Job(packageName, Kind.NAVIGATE, section))
        fun requestImageDownload(packageName: String, query: String, count: Int = 1) =
            request(Job(packageName, Kind.DOWNLOAD_IMAGE, query, downloadCount = count.coerceIn(1, 9)))
        fun requestClick(packageName: String, label: String) = request(Job(packageName, Kind.CLICK, label))
        fun requestOpenFiles(packageName: String) = request(Job(packageName, Kind.OPEN_FILES, ""))
        fun requestOpenFolder(packageName: String, folder: String) = request(Job(packageName, Kind.OPEN_FOLDER, folder))
        fun stop() { instance?.finish("أوقفت التنفيذ.") }
        fun back() = instance?.performGlobalAction(GLOBAL_ACTION_BACK) == true
        fun home() = instance?.performGlobalAction(GLOBAL_ACTION_HOME) == true
    }
}
