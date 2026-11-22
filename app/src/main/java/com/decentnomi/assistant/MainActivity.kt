package com.decentnomi.assistant

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var stats: TextView
    private lateinit var logView: TextView
    private lateinit var toggle: Button
    private lateinit var langBtn: Button
    private val langs = listOf("hi-IN", "en-US")
    private val prefs by lazy { getSharedPreferences("decent", Context.MODE_PRIVATE) }

    private fun render() {
        status.text = if (Bus.running) "Service: CHALU. Ab 'Decent' bolen." else "Service: BAND"
        toggle.text = if (Bus.running) "BAND KAREN" else "SHURU KAREN"
        stats.text = "Kul suna: " + Bus.heard + "   |   Decent pakda: " + Bus.wakes
        logView.text = Bus.lines.asReversed().joinToString("\n")
        langBtn.text = "Sunne ki zubaan: " + (prefs.getString("rlang", "hi-IN") ?: "hi-IN") + "  (badlen)"
    }

    private fun startSvc() {
        val i = Intent(this, WakeService::class.java)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(pad, pad * 2, pad, pad)

        val title = TextView(this)
        title.text = "Decent  -  Marhala 1"
        title.textSize = 26f
        status = TextView(this)
        status.textSize = 16f
        stats = TextView(this)
        stats.textSize = 16f

        toggle = Button(this)
        toggle.setOnClickListener {
            if (Bus.running) {
                stopService(Intent(this, WakeService::class.java))
            } else {
                val need = ArrayList<String>()
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    need.add(Manifest.permission.RECORD_AUDIO)
                }
                if (Build.VERSION.SDK_INT >= 33 &&
                    checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED
                ) {
                    need.add("android.permission.POST_NOTIFICATIONS")
                }
                if (need.isNotEmpty()) requestPermissions(need.toTypedArray(), 1) else startSvc()
            }
        }

        langBtn = Button(this)
        langBtn.setOnClickListener {
            val cur = prefs.getString("rlang", "hi-IN") ?: "hi-IN"
            val next = langs[(langs.indexOf(cur) + 1) % langs.size]
            prefs.edit().putString("rlang", next).apply()
            render()
        }

        val batt = Button(this)
        batt.text = "Battery rukawat hatayen (zaroori)"
        batt.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + packageName)))
            } catch (e: Exception) {
            }
        }

        logView = TextView(this)
        logView.textSize = 12f
        logView.typeface = Typeface.MONOSPACE
        val scroll = ScrollView(this)
        scroll.addView(logView)

        root.addView(title)
        root.addView(status)
        root.addView(stats)
        root.addView(toggle)
        root.addView(langBtn)
        root.addView(batt)
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startSvc()
    }

    override fun onResume() {
        super.onResume()
        Bus.onChange = { render() }
        render()
    }

    override fun onPause() {
        Bus.onChange = null
        super.onPause()
    }
}
