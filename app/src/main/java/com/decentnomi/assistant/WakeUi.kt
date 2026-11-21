package com.decentnomi.assistant

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.Toast

object WakeUi {
    private fun startSvc(act: Activity) {
        val i = Intent(act, WakeService::class.java)
        if (Build.VERSION.SDK_INT >= 26) act.startForegroundService(i) else act.startService(i)
    }

    fun add(act: Activity, root: LinearLayout) {
        val prefs = act.getSharedPreferences("assistant", Context.MODE_PRIVATE)
        val box = CheckBox(act)
        box.text = "Mode C: Decent bolte hi jag jaye (hamesha sunta rahe)"
        box.isChecked = prefs.getBoolean("wake", false)
        box.setOnCheckedChangeListener { b, on ->
            if (on) {
                if (!Settings.canDrawOverlays(act)) {
                    b.isChecked = false
                    Toast.makeText(act, "Decent ko 'Display over other apps' ki ijazat den, phir dobara tick karen", Toast.LENGTH_LONG).show()
                    act.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + act.packageName)))
                } else if (act.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    b.isChecked = false
                    Toast.makeText(act, "Pehle mic ki ijazat den", Toast.LENGTH_LONG).show()
                } else {
                    prefs.edit().putBoolean("wake", true).apply()
                    val pm = act.getSystemService(Context.POWER_SERVICE) as PowerManager
                    if (!pm.isIgnoringBatteryOptimizations(act.packageName)) {
                        try {
                            act.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + act.packageName)))
                        } catch (e: Exception) {
                        }
                    }
                    startSvc(act)
                }
            } else {
                prefs.edit().putBoolean("wake", false).apply()
                act.stopService(Intent(act, WakeService::class.java))
            }
        }
        root.addView(box)
        if (box.isChecked &&
            Settings.canDrawOverlays(act) &&
            act.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        ) {
            startSvc(act)
        }
    }
}
