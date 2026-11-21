package com.decentnomi.assistant

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class AssistantAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile var pendingUntil: Long = 0L
        @Volatile var instance: AssistantAccessibilityService? = null
        fun arm() {
            pendingUntil = System.currentTimeMillis() + 20000L
        }
    }

    private val h = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (System.currentTimeMillis() > pendingUntil) return
        if (clickSendNow()) pendingUntil = 0L
    }

    private fun clickableAncestor(n: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        var x = n
        var d = 0
        while (x != null && d < 6) {
            if (x.isClickable) return x
            x = x.parent
            d++
        }
        return null
    }

    fun clickSendNow(): Boolean {
        val root = rootInActiveWindow ?: return false
        val pkg = root.packageName?.toString() ?: return false
        if (!pkg.startsWith("com.whatsapp")) return false
        var nodes: List<AccessibilityNodeInfo>? = root.findAccessibilityNodeInfosByViewId(pkg + ":id/send")
        if (nodes.isNullOrEmpty()) {
            val byText: List<AccessibilityNodeInfo>? = root.findAccessibilityNodeInfosByText("Send")
            nodes = byText?.filter {
                it.contentDescription?.toString().equals("Send", true) || it.text?.toString().equals("Send", true)
            }
        }
        val target = nodes?.firstOrNull() ?: return false
        val btn = clickableAncestor(target) ?: return false
        return btn.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    fun globalAction(cmd: String): Boolean {
        return when (cmd.lowercase()) {
            "home" -> performGlobalAction(GLOBAL_ACTION_HOME)
            "back" -> performGlobalAction(GLOBAL_ACTION_BACK)
            "recents" -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            "notifications" -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            "quick_settings" -> performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
            "power_menu" -> performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
            "lock" -> if (Build.VERSION.SDK_INT >= 28) performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) else false
            "screenshot" -> if (Build.VERSION.SDK_INT >= 28) performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT) else false
            else -> false
        }
    }

    fun toggleTile(labels: List<String>, desiredOn: Boolean) {
        performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
        h.postDelayed({
            val roots = ArrayList<AccessibilityNodeInfo>()
            try {
                for (w in windows) {
                    val r = w.root
                    if (r != null) roots.add(r)
                }
            } catch (e: Exception) {
            }
            val active = rootInActiveWindow
            if (active != null) roots.add(active)
            var done = false
            for (r in roots) {
                if (done) break
                for (l in labels) {
                    val found: List<AccessibilityNodeInfo>? = r.findAccessibilityNodeInfosByText(l)
                    if (found.isNullOrEmpty()) continue
                    val tile = clickableAncestor(found[0])
                    if (tile != null) {
                        val already = tile.isCheckable && tile.isChecked == desiredOn
                        if (!already) tile.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        done = true
                        break
                    }
                }
            }
            h.postDelayed({ performGlobalAction(GLOBAL_ACTION_BACK) }, 700L)
        }, 1200L)
    }
}
