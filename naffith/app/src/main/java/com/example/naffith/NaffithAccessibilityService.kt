package com.example.naffith

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * طبقة التحكم في الشاشة للمرحلة التالية.
 * في هذه النسخة تجمع النص الظاهر فقط ولا تنفذ أي نقر تلقائي غير مطلوب.
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
    }

    override fun onInterrupt() = Unit

    private fun readVisibleText(root: AccessibilityNodeInfo?): String {
        if (root == null) return ""
        val result = StringBuilder()
        appendNodeText(root, result)
        return result.toString().trim()
    }

    private fun appendNodeText(node: AccessibilityNodeInfo, result: StringBuilder) {
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let {
            if (result.isNotEmpty()) result.append('\n')
            result.append(it)
        }
        for (index in 0 until node.childCount) {
            node.getChild(index)?.let { child ->
                appendNodeText(child, result)
                child.recycle()
            }
        }
    }

    companion object {
        @Volatile
        var latestScreenText: String = ""
            private set
    }
}
