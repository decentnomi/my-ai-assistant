package com.decentnomi.assistant

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

class LockService : AccessibilityService() {
    override fun onServiceConnected() { inst = this }
    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onUnbind(i: Intent?): Boolean { inst = null; return super.onUnbind(i) }
    companion object {
        var inst: LockService? = null
        fun lock(): Boolean = inst?.performGlobalAction(8) ?: false
    }
}
