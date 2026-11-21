package com.decentnomi.assistant

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import java.util.Locale

object BrightnessHelper {
    fun set(act: Activity, level: Int, dir: String, say: (String) -> Unit) {
        if (!Settings.System.canWrite(act)) {
            say("Brightness ke liye ijazat chahiye. Settings khul rahi hai, Hello Nomi ko allow karen, phir dobara bolen.")
            try {
                act.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + act.packageName)))
            } catch (e: Exception) {
            }
            return
        }
        val cr = act.contentResolver
        val cur = try {
            Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS)
        } catch (e: Exception) {
            128
        }
        val d = dir.lowercase(Locale.ROOT)
        val pct = if (level in 0..100) {
            level
        } else if (d == "up") {
            cur * 100 / 255 + 25
        } else if (d == "down") {
            cur * 100 / 255 - 25
        } else {
            50
        }
        val p = pct.coerceIn(5, 100)
        val v = p * 255 / 100
        try {
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, v)
            val lp = act.window.attributes
            lp.screenBrightness = v / 255f
            act.window.attributes = lp
            val now = Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS)
            say("Brightness " + p + " percent. Pehle " + cur + ", ab " + now)
        } catch (e: Exception) {
            say("Brightness ma masla: " + e.message)
        }
    }
}
