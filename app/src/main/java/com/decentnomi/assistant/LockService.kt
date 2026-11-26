package com.decentnomi.assistant

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class LockService : AccessibilityService() {
    override fun onServiceConnected() { inst = this }
    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onUnbind(i: Intent?): Boolean { inst = null; return super.onUnbind(i) }

    private val mh = Handler(Looper.getMainLooper())
    private fun log(s: String) { mh.post { Bus.add(s); Bus.changed() } }

    private fun walk(n: AccessibilityNodeInfo?, f: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (n == null) return null
        if (f(n)) return n
        for (i in 0 until n.childCount) {
            val r = walk(n.getChild(i), f)
            if (r != null) return r
        }
        return null
    }

    private fun lab(n: AccessibilityNodeInfo): String {
        val a = n.text?.toString() ?: ""
        val b = n.contentDescription?.toString() ?: ""
        val c = n.viewIdResourceName ?: ""
        return (a + " | " + b + " | " + c).lowercase()
    }

    private fun waitFor(ms: Long, f: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) {
            val r = walk(rootInActiveWindow, f)
            if (r != null) return r
            Thread.sleep(300)
        }
        return null
    }

    private fun click(n: AccessibilityNodeInfo): Boolean {
        var c: AccessibilityNodeInfo? = n
        var k = 0
        while (c != null && k < 6) {
            if (c.isClickable) return c.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            c = c.parent
            k++
        }
        return false
    }

    private fun typeInto(n: AccessibilityNodeInfo, s: String): Boolean {
        n.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val b = Bundle()
        b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, s)
        return n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b)
    }

    private fun dump(): String {
        val out = ArrayList<String>()
        walk(rootInActiveWindow) { n ->
            val t = n.text?.toString() ?: n.contentDescription?.toString() ?: ""
            if (t.isNotBlank() && out.size < 25) out.add(t.take(18))
            false
        }
        return out.joinToString(",")
    }

    fun sendMsg(ctx: Context, pkg: String, name: String, body: String, done: (String) -> Unit) {
        Thread {
            var step = "app kholna"
            try {
                val li = ctx.packageManager.getLaunchIntentForPackage(pkg)
                if (li == null) { done("Ye app install nahi hai"); return@Thread }
                li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                ctx.startActivity(li)
                Thread.sleep(2500)
                step = "search"
                val nm = name.lowercase()
                var ed = walk(rootInActiveWindow) { it.isEditable && lab(it).contains("search") }
                if (ed == null) {
                    val s = waitFor(8000) { !it.isEditable && lab(it).contains("search") }
                    if (s != null) { click(s); Thread.sleep(900) }
                    ed = waitFor(5000) { it.isEditable }
                }
                if (ed == null) throw Exception("search box nahi mila")
                typeInto(ed, name)
                Thread.sleep(1800)
                step = "contact"
                val c = waitFor(6000) { !it.isEditable && lab(it).contains(nm) && !lab(it).contains("search") }
                if (c == null) throw Exception("contact nahi mila")
                click(c)
                Thread.sleep(1800)
                step = "message box"
                val mb = waitFor(6000) { it.isEditable && !lab(it).contains("search") }
                if (mb == null) throw Exception("message box nahi mila")
                typeInto(mb, body)
                Thread.sleep(800)
                step = "send"
                val sb = waitFor(5000) { !it.isEditable && lab(it).contains("send") && !lab(it).contains("send to") }
                if (sb == null) throw Exception("send button nahi mila")
                click(sb)
                log("MSG ok")
                done("Message bhej diya")
            } catch (e: Exception) {
                log("MSG fail (" + step + "): " + e.message + " | " + dump())
                done("Message nahi bhej saka")
            }
        }.start()
    }

    companion object {
        var inst: LockService? = null
        fun lock(): Boolean = inst?.performGlobalAction(8) ?: false
    }
}
