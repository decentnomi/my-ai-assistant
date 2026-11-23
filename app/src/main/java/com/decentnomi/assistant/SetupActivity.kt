package com.decentnomi.assistant

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class SetupActivity : Activity() {

    private val rows = ArrayList<Triple<Button, String, () -> Boolean>>()
    private var modeBtn: Button? = null

    private fun prefs() = getSharedPreferences("decent", Context.MODE_PRIVATE)

    private fun perms(): List<String> {
        val l = arrayListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.SEND_SMS
        )
        if (Build.VERSION.SDK_INT >= 33) l.add(Manifest.permission.POST_NOTIFICATIONS)
        return l
    }

    private fun permsOk(): Boolean =
        perms().all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    private fun go(i: Intent) {
        try {
            startActivity(i)
        } catch (e: Exception) {
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(40, 60, 40, 40)
        root.setBackgroundColor(Color.parseColor("#1E1E1E"))

        val title = TextView(this)
        title.text = "Decent Setup"
        title.textSize = 24f
        title.setTextColor(Color.WHITE)
        root.addView(title)

        val sub = TextView(this)
        sub.text = "Har button dabayen aur Allow karen. Hara = ho chuka."
        sub.setTextColor(Color.LTGRAY)
        sub.setPadding(0, 10, 0, 30)
        root.addView(sub)

        val pkg = Uri.parse("package:" + packageName)

        add(root, "1. Mic, Contacts, Call, SMS ki ijazat", { permsOk() }) {
            requestPermissions(perms().toTypedArray(), 7)
        }
        add(root, "2. Display over other apps", { Settings.canDrawOverlays(this) }) {
            go(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkg))
        }
        add(root, "3. Modify system settings", { Settings.System.canWrite(this) }) {
            go(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, pkg))
        }
        add(root, "4. Battery rukawat hatayen", {
            (getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName)
        }) {
            try {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg))
            } catch (e: Exception) {
                go(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }

        val et = EditText(this)
        et.hint = "Gemini API key yahan paste karen"
        et.setTextColor(Color.WHITE)
        et.setHintTextColor(Color.GRAY)
        et.setSingleLine(true)
        root.addView(et)

        add(root, "5. Gemini key save karen", { (prefs().getString("gkey", "") ?: "").isNotBlank() }) {
            val k = et.text.toString().trim()
            if (k.isNotEmpty()) {
                prefs().edit().putString("gkey", k).apply()
                et.setText("")
            }
            refresh()
        }

        val mb = Button(this)
        mb.setTextColor(Color.WHITE)
        mb.isAllCaps = false
        mb.setBackgroundColor(Color.parseColor("#37474F"))
        mb.setOnClickListener {
            val gf = (prefs().getString("mode", "pro") ?: "pro") == "gf"
            prefs().edit().putString("mode", if (gf) "pro" else "gf").apply()
            refresh()
        }
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.setMargins(0, 0, 0, 24)
        root.addView(mb, lp)
        modeBtn = mb

        val sv = ScrollView(this)
        sv.addView(root)
        setContentView(sv)
    }

    private fun add(root: LinearLayout, label: String, ok: () -> Boolean, act: () -> Unit) {
        val bt = Button(this)
        bt.text = label
        bt.setTextColor(Color.WHITE)
        bt.isAllCaps = false
        bt.gravity = Gravity.CENTER
        bt.setOnClickListener { act() }
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.setMargins(0, 0, 0, 24)
        root.addView(bt, lp)
        rows.add(Triple(bt, label, ok))
    }

    private fun refresh() {
        for (r in rows) {
            val good = try {
                r.third()
            } catch (e: Exception) {
                false
            }
            r.first.text = if (good) "[OK]  " + r.second else r.second
            r.first.setBackgroundColor(Color.parseColor(if (good) "#2E7D32" else "#555555"))
        }
        val gf = (prefs().getString("mode", "pro") ?: "pro") == "gf"
        modeBtn?.text = "Mode: " + (if (gf) "Girlfriend" else "Professional") + "  (badalne ke liye dabayen)"
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onRequestPermissionsResult(rc: Int, p: Array<out String>, g: IntArray) {
        super.onRequestPermissionsResult(rc, p, g)
        refresh()
    }
}
