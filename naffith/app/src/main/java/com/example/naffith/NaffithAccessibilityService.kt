package com.example.naffith

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * يقرأ عناصر الشاشة وينفذ بحثًا بسيطًا داخل التطبيق المفتوح عند طلب المستخدم.
 * يجب تفعيل الخدمة يدويًا من إعدادات إمكانية الوصول.
 */
class NaffithAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        latestScreenText = readVisibleText(rootInActiveWindow)
        val target = targetPackage ?: return
        if (event?.packageName?.toString() != target) return
        if (System.currentTimeMillis() - lastActionAt < 350) return

        when (searchStage) {
            0 -> {
                val searchButton = findFirst(rootInActiveWindow) { node ->
                    val value = nodeText(node)
                    value.contains("بحث") || value.contains("search") || value.contains("ابحث")
                }
                if (searchButton?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) {
                    searchStage = 1
                    lastActionAt = System.currentTimeMillis()
                }
            }
            1 -> {
                val input = findFirst(rootInActiveWindow) { node ->
                    node.className?.toString()?.contains("EditText") == true || node.isEditable
                }
                if (input != null) {
                    val args = Bundle().apply {
                        putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, targetQuery)
                    }
                    input.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                    input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                    val searchButton = findFirst(rootInActiveWindow) { node ->
                        val value = nodeText(node)
                        value.contains("بحث") || value.contains("search") || value.contains("ابحث")
                    }
                    searchButton?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    clearSearchRequest()
                    lastActionAt = System.currentTimeMillis()
                }
            }
        }
    }

    override fun onInterrupt() = Unit

    private fun readVisibleText(root: AccessibilityNodeInfo?): String {
        if (root == null) return ""
        val result = StringBuilder()
        appendNodeText(root, result)
        return result.toString().trim()
    }

    private fun appendNodeText(node: AccessibilityNodeInfo, result: StringBuilder) {
        nodeText(node).takeIf { it.isNotBlank() }?.let {
            if (result.isNotEmpty()) result.append('\n')
            result.append(it)
        }
        for (index in 0 until node.childCount) {
            node.getChild(index)?.let { appendNodeText(it, result) }
        }
    }

    private fun nodeText(node: AccessibilityNodeInfo): String =
        listOf(node.text?.toString(), node.contentDescription?.toString())
            .filterNotNull()
            .firstOrNull { it.isNotBlank() }
            ?.lowercase()
            ?.replace('أ', 'ا')
            ?.replace('إ', 'ا')
            ?.replace('آ', 'ا')
            ?.trim()
            .orEmpty()

    private fun findFirst(root: AccessibilityNodeInfo?, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (root == null) return null
        if (predicate(root)) return root
        for (index in 0 until root.childCount) {
            val found = findFirst(root.getChild(index), predicate)
            if (found != null) return found
        }
        return null
    }

    private fun clearSearchRequest() {
        targetPackage = null
        targetQuery = ""
        searchStage = 0
    }

    companion object {
        @Volatile
        var latestScreenText: String = ""
            private set

        @Volatile
        private var targetPackage: String? = null
        @Volatile
        private var targetQuery: String = ""
        @Volatile
        private var searchStage: Int = 0
        @Volatile
        private var lastActionAt: Long = 0L

        fun requestSearch(packageName: String, query: String) {
            targetPackage = packageName
            targetQuery = query
            searchStage = 0
            lastActionAt = 0L
        }
    }
}
