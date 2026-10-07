package com.example.naffith

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/** تنفيذ بحث وضغطات بسيطة داخل التطبيقات بعد تفعيل إمكانية الوصول. */
class NaffithAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                AccessibilityEvent.TYPE_VIEW_CLICKED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val root = rootInActiveWindow ?: return
        latestScreenText = readVisibleText(root)
        val target = targetPackage ?: return
        val packageName = event?.packageName?.toString()
        if (packageName != null && packageName != target) return
        if (System.currentTimeMillis() - lastActionAt < 350) return

        when (mode) {
            MODE_SEARCH -> handleSearch(root)
            MODE_CALCULATOR -> handleCalculator(root)
            MODE_PLAY_FIRST -> handlePlayFirst(root)
        }
    }

    override fun onInterrupt() = Unit

    private fun handleSearch(root: AccessibilityNodeInfo) {
        when (searchStage) {
            0 -> {
                val searchButton = findFirst(root) { isSearchNode(it) }
                if (clickNode(searchButton)) {
                    searchStage = 1
                    lastActionAt = System.currentTimeMillis()
                }
            }
            1 -> {
                val input = findFirst(root) { it.isEditable || it.className?.toString()?.contains("EditText") == true }
                if (input != null) {
                    val args = Bundle().apply {
                        putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, targetQuery)
                    }
                    input.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                    input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                    // زر البحث الظاهر هو بديل Enter المتوافق مع Android 8.1.
                    clickNode(findFirst(root) { isSearchNode(it) })
                    if (searchPlayFirst) {
                        searchPlayFirst = false
                        mode = MODE_PLAY_FIRST
                        requestStartedAt = System.currentTimeMillis()
                    } else {
                        clearRequest()
                    }
                    lastActionAt = System.currentTimeMillis()
                }
            }
        }
    }

    private fun handleCalculator(root: AccessibilityNodeInfo) {
        if (calculatorIndex >= calculatorKeys.length) {
            clearRequest()
            return
        }
        val expected = calculatorKeys[calculatorIndex].toString()
        val key = findFirst(root) { node -> isCalculatorKey(node, expected) }
        if (clickNode(key)) {
            calculatorIndex++
            calculatorMisses = 0
            lastActionAt = System.currentTimeMillis()
            if (calculatorIndex >= calculatorKeys.length) clearRequest()
        } else {
            calculatorMisses++
            if (calculatorMisses > 8) clearRequest()
        }
    }

    private fun handlePlayFirst(root: AccessibilityNodeInfo) {
        if (System.currentTimeMillis() - requestStartedAt < 1200L) return
        val firstResult = findFirst(root) { node ->
            if (!node.isClickable && node.actionList.none { it.id == AccessibilityNodeInfo.ACTION_CLICK }) return@findFirst false
            val value = nodeText(node)
            value.length >= 3 && excludedFromFirstResult.none { value.contains(it) }
        }
        if (clickNode(firstResult)) {
            lastActionAt = System.currentTimeMillis()
            clearRequest()
        } else if (System.currentTimeMillis() - requestStartedAt > 10000L) {
            clearRequest()
        }
    }

    private fun isSearchNode(node: AccessibilityNodeInfo): Boolean {
        val value = nodeText(node)
        return value.contains("بحث") || value.contains("search") || value.contains("ابحث") ||
            node.viewIdResourceName?.lowercase()?.contains("search") == true
    }

    private fun isCalculatorKey(node: AccessibilityNodeInfo, expected: String): Boolean {
        val value = nodeText(node)
        if (value.isBlank()) return false
        if (expected[0].isDigit()) return value == expected || value.endsWith(" $expected") || value.contains("رقم $expected")
        return when (expected) {
            "+" -> value == "+" || value.contains("plus") || value.contains("جمع")
            "-" -> value == "-" || value.contains("minus") || value.contains("طرح")
            "*" -> value == "*" || value.contains("×") || value.contains("ضرب")
            "/" -> value == "/" || value.contains("÷") || value.contains("قسمة")
            "." -> value == "." || value.contains("نقطة") || value.contains("decimal")
            "=" -> value == "=" || value.contains("يساوي") || value.contains("equal") || value == "enter"
            else -> false
        }
    }

    private fun clickNode(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        var parent = node.parent
        repeat(3) {
            if (parent?.isClickable == true && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            parent = parent?.parent
        }
        return false
    }

    private fun readVisibleText(root: AccessibilityNodeInfo): String {
        val result = StringBuilder()
        appendNodeText(root, result)
        return result.toString().trim()
    }

    private fun appendNodeText(node: AccessibilityNodeInfo, result: StringBuilder) {
        nodeText(node).takeIf { it.isNotBlank() }?.let {
            if (result.isNotEmpty()) result.append('\n')
            result.append(it)
        }
        for (index in 0 until node.childCount) node.getChild(index)?.let { appendNodeText(it, result) }
    }

    private fun nodeText(node: AccessibilityNodeInfo): String {
        val raw = node.text?.toString() ?: node.contentDescription?.toString() ?: return ""
        return raw.lowercase()
            .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا').trim()
    }

    private fun findFirst(root: AccessibilityNodeInfo?, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (root == null) return null
        if (predicate(root)) return root
        for (index in 0 until root.childCount) findFirst(root.getChild(index), predicate)?.let { return it }
        return null
    }

    private fun clearRequest() {
        targetPackage = null
        targetQuery = ""
        mode = MODE_NONE
        searchStage = 0
        searchPlayFirst = false
        calculatorKeys = ""
        calculatorIndex = 0
        calculatorMisses = 0
    }

    companion object {
        private const val MODE_NONE = 0
        private const val MODE_SEARCH = 1
        private const val MODE_CALCULATOR = 2
        private const val MODE_PLAY_FIRST = 3

        @Volatile var latestScreenText: String = ""
            private set
        @Volatile private var targetPackage: String? = null
        @Volatile private var targetQuery = ""
        @Volatile private var mode = MODE_NONE
        @Volatile private var searchStage = 0
        @Volatile private var searchPlayFirst = false
        @Volatile private var calculatorKeys = ""
        @Volatile private var calculatorIndex = 0
        @Volatile private var calculatorMisses = 0
        @Volatile private var lastActionAt = 0L
        @Volatile private var requestStartedAt = 0L

        private val excludedFromFirstResult = listOf("بحث", "search", "الصفحة الرئيسية", "home", "اشتراك", "subscriptions", "shorts")

        fun requestSearch(packageName: String, query: String, playFirst: Boolean = false) {
            targetPackage = packageName
            targetQuery = query
            searchPlayFirst = playFirst
            mode = MODE_SEARCH
            searchStage = 0
            lastActionAt = 0L
            requestStartedAt = System.currentTimeMillis()
        }

        fun requestCalculator(packageName: String, expression: String) {
            targetPackage = packageName
            calculatorKeys = expression
            calculatorIndex = 0
            calculatorMisses = 0
            mode = MODE_CALCULATOR
            lastActionAt = 0L
            requestStartedAt = System.currentTimeMillis()
        }

        fun requestPlayFirst(packageName: String) {
            targetPackage = packageName
            mode = MODE_PLAY_FIRST
            lastActionAt = 0L
            requestStartedAt = System.currentTimeMillis()
        }
    }
}
