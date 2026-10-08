package com.example.naffith

import android.content.Context
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONArray
import org.json.JSONObject

/**
 * ذاكرة محلية صغيرة لعناصر الواجهات التي يراها المستخدم داخل التطبيقات.
 *
 * لا تحفظ قيمة حقول الإدخال أو محتوى الرسائل؛ تحفظ اسم الزر/الوصف، معرّف
 * العنصر ودوره وعدد مرات ظهوره فقط. بهذا يستطيع المنفّذ تفضيل الأسماء التي
 * استُخدمت فعليًا في نسخة التطبيق الموجودة على الهاتف بدل الاعتماد على قائمة
 * ثابتة من التطبيقات.
 */
data class LearnedControl(
    val label: String,
    val viewId: String,
    val role: String,
    val seen: Int
)

class AppLearningStore(context: Context) {
    private val ownPackageName = context.packageName
    private val prefs = context.getSharedPreferences("naffith_app_knowledge", Context.MODE_PRIVATE)
    private var lastPackage = ""
    private var lastSignature = ""
    private var lastAt = 0L
    private val lastScreenByPackage = mutableMapOf<String, String>()
    // حدث الضغط يصل أحيانًا قبل أن تتغير شجرة الشاشة؛ نحتفظ به حتى تصل
    // لقطة الشاشة التالية، وبذلك يمكن إعادة تنفيذ المسار الذي علّمه المستخدم.
    private val pendingViaByPackage = mutableMapOf<String, UiSelector>()

    fun observe(packageName: String, appLabel: String, nodes: List<AccessibilityNodeInfo>, via: UiSelector? = null) {
        if (packageName.isBlank() || packageName == ownPackageName) return
        val elements = nodes.mapIndexed { index, node ->
            val label = listOfNotNull(node.text?.toString(), node.contentDescription?.toString())
                .firstOrNull { it.isNotBlank() }.orEmpty()
            val hint = node.hintText?.toString().orEmpty()
            val rect = android.graphics.Rect().also { node.getBoundsInScreen(it) }
            UiElement(index, index, label, hint, node.viewIdResourceName.orEmpty().lowercase(),
                node.isEditable, node.isPassword, node.isClickable, node.isScrollable,
                node.isFocused, node.isSelected, node.isEnabled, rect.left, rect.top, rect.right, rect.bottom)
        }
        observeElements(packageName, appLabel, elements, via)
    }

    private fun observeElements(packageName: String, appLabel: String, elements: List<UiElement>, via: UiSelector?) {
        val candidates = elements.mapNotNull { element ->
            val rawLabel = when {
                element.editable -> element.hint
                else -> element.label
            }
            val label = ArabicText.normalize(rawLabel).take(100)
            val id = element.viewId.lowercase().take(120)
            if (label.isBlank() && id.isBlank()) return@mapNotNull null
            val role = when {
                element.editable -> "input"
                element.scrollable -> "scroll"
                element.clickable -> "button"
                else -> "text"
            }
            LearnedControl(label, id, role, 1)
        }.distinctBy { "${it.role}|${it.label}|${it.viewId}" }
        if (candidates.isEmpty()) return
        val signature = candidates.joinToString(";") { "${it.role}|${it.label}|${it.viewId}" }
        val now = System.currentTimeMillis()
        if (packageName == lastPackage && signature == lastSignature && now - lastAt < 900L) {
            if (via != null) pendingViaByPackage[packageName] = via
            return
        }
        lastPackage = packageName
        lastSignature = signature
        lastAt = now

        val key = "app:$packageName"
        val record = JSONObject(prefs.getString(key, "{}"))
        record.put("label", appLabel)
        val existing = linkedMapOf<String, JSONObject>()
        val array = record.optJSONArray("controls") ?: JSONArray()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            existing[controlKey(item.optString("role"), item.optString("label"), item.optString("id"))] = item
        }
        candidates.forEach { control ->
            val controlKey = controlKey(control.role, control.label, control.viewId)
            val item = existing[controlKey] ?: JSONObject().apply {
                put("role", control.role)
                put("label", control.label)
                put("id", control.viewId)
                put("seen", 0)
            }
            item.put("seen", item.optInt("seen", 0) + 1)
            existing[controlKey] = item
        }
        val merged = JSONArray()
        existing.values.sortedByDescending { it.optInt("seen", 0) }.take(MAX_CONTROLS).forEach(merged::put)
        record.put("controls", merged)
        val screen = UiSemantics.screen(elements)
        val previousScreen = lastScreenByPackage[packageName]
        val screens = record.optJSONArray("screens") ?: JSONArray()
        if ((0 until screens.length()).none { screens.optJSONObject(it)?.optString("key") == screen.key }) {
            screens.put(JSONObject().apply {
                put("key", screen.key)
                put("controls", JSONArray().also { out -> screen.controls.forEach { out.put(selectorJson(it)) } })
            })
        }
        while (screens.length() > MAX_SCREENS) screens.remove(0)
        record.put("screens", screens)
        if (via != null) pendingViaByPackage[packageName] = via
        val transitionVia = via ?: pendingViaByPackage[packageName]
        if (previousScreen != null && previousScreen != screen.key && transitionVia != null) {
            val edges = record.optJSONArray("edges") ?: JSONArray()
            val edgeKey = "$previousScreen|${screen.key}|${transitionVia.viewId}|${transitionVia.semantic}|${transitionVia.labelHash}"
            if ((0 until edges.length()).none { edges.optJSONObject(it)?.optString("key") == edgeKey }) {
                edges.put(JSONObject().apply {
                    put("key", edgeKey)
                    put("from", previousScreen)
                    put("to", screen.key)
                    put("via", selectorJson(transitionVia))
                })
            }
            while (edges.length() > MAX_EDGES) edges.remove(0)
            record.put("edges", edges)
            pendingViaByPackage.remove(packageName)
        }
        lastScreenByPackage[packageName] = screen.key
        prefs.edit().putString(key, record.toString()).apply()
    }

    fun controls(packageName: String): List<LearnedControl> {
        val array = JSONObject(prefs.getString("app:$packageName", "{}"))
            .optJSONArray("controls") ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            LearnedControl(
                item.optString("label"),
                item.optString("id"),
                item.optString("role", "text"),
                item.optInt("seen", 0)
            )
        }
    }

    fun describe(packageName: String): String {
        val record = JSONObject(prefs.getString("app:$packageName", "{}"))
        val label = record.optString("label", packageName)
        val controls = controls(packageName).take(80)
        if (controls.isEmpty()) return "لم أتعلم عناصر واجهة $label بعد. افتحه مرة واحدة ثم أعد قراءة الشاشة."
        val grouped = controls.groupBy { it.role }
        val buttons = grouped["button"].orEmpty().mapNotNull { it.label.takeIf(String::isNotBlank) }.distinct()
        val inputs = grouped["input"].orEmpty().mapNotNull { it.label.takeIf(String::isNotBlank) }.distinct()
        val navigation = controls.filter { it.viewId.contains("nav") || it.viewId.contains("menu") || it.viewId.contains("tab") }
            .mapNotNull { it.label.takeIf(String::isNotBlank) }.distinct()
        return buildString {
            append("معرفة محلية عن $label:\n")
            if (buttons.isNotEmpty()) append("الأزرار: ${buttons.take(25).joinToString("، ")}\n")
            if (inputs.isNotEmpty()) append("حقول الإدخال: ${inputs.take(12).joinToString("، ")}\n")
            if (navigation.isNotEmpty()) append("التنقل: ${navigation.take(20).joinToString("، ")}\n")
            append("العناصر المرصودة: ${controls.size}")
            append("، الشاشات المتعلمة: ${record.optJSONArray("screens")?.length() ?: 0}")
            append("، المسارات المؤكدة: ${record.optJSONArray("edges")?.length() ?: 0}")
        }
    }

    fun route(packageName: String, from: String, target: String, input: Boolean = false): List<UiTransition> {
        val record = JSONObject(prefs.getString("app:$packageName", "{}"))
        val screens = record.optJSONArray("screens") ?: return emptyList()
        val screenModels = (0 until screens.length()).mapNotNull { index ->
            val item = screens.optJSONObject(index) ?: return@mapNotNull null
            val controls = item.optJSONArray("controls") ?: JSONArray()
            UiScreen(item.optString("key"), (0 until controls.length()).mapNotNull { controlIndex ->
                selectorFromJson(controls.optJSONObject(controlIndex) ?: return@mapNotNull null)
            })
        }
        val edgeArray = record.optJSONArray("edges") ?: JSONArray()
        val edgeModels = (0 until edgeArray.length()).mapNotNull { index ->
            val item = edgeArray.optJSONObject(index) ?: return@mapNotNull null
            val via = selectorFromJson(item.optJSONObject("via") ?: return@mapNotNull null)
            UiTransition(item.optString("from"), item.optString("to"), via)
        }
        return UiSemantics.route(screenModels, edgeModels, from, target, input)
    }

    private fun selectorJson(selector: UiSelector) = JSONObject().apply {
        put("id", selector.viewId)
        put("semantic", selector.semantic)
        put("hash", selector.labelHash)
        put("input", selector.input)
    }

    private fun selectorFromJson(value: JSONObject): UiSelector = UiSelector(
        value.optString("id"), value.optString("semantic"), value.optString("hash"), value.optBoolean("input")
    )

    private fun controlKey(role: String, label: String, id: String) = "$role|$label|$id"

    companion object {
        private const val MAX_CONTROLS = 220
        private const val MAX_SCREENS = 80
        private const val MAX_EDGES = 240
    }
}
