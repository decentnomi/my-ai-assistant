package com.decentnomi.assistant

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.BatteryManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Commands {

    private val STOP = setOf(
        "open", "opan", "kholo", "khol", "kholiye", "kholna", "chalao", "chala",
        "karo", "kar", "do", "dena", "de", "app", "application", "ko", "ki", "ka", "ke",
        "please", "plz", "jaldi", "start", "launch", "mujhe", "mera", "meri", "zara",
        "ap", "aap", "ji", "haan", "aur", "phir", "karke"
    )
    private val OPEN = setOf(
        "open", "opan", "kholo", "khol", "kholiye", "kholna", "chalao", "chala", "start", "launch"
    )

    fun run(ctx: Context, said: String): String? {
        val t = said.lowercase(Locale.ROOT)
        val w = t.split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
        fun has(vararg k: String): Boolean = k.any { w.contains(it) }

        if (has("torch", "tarch", "flashlight", "flash", "light", "lite", "laait", "roshni")) {
            val on = !has("band", "off", "bujha", "bujhao", "bandh", "bund")
            return torch(ctx, on)
        }
        if (has("volume", "volyoom", "vol", "awaz", "awaaz", "aavaz", "aavaaj", "aawaz")) {
            return volume(ctx, w)
        }
        if (has("battery", "charge", "charging", "baitri", "betri", "battary")) {
            return battery(ctx)
        }
        if (has("time", "taaim", "waqt", "vakt", "samay", "baje")) {
            val s = SimpleDateFormat("h:mm a", Locale.US).format(Date())
            return "Abhi " + s + " baje hain"
        }
        if (w.any { OPEN.contains(it) }) {
            return openApp(ctx, w)
        }
        return null
    }

    private fun torch(ctx: Context, on: Boolean): String {
        return try {
            val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val id = cm.cameraIdList.firstOrNull {
                cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return "Flash nahi mila"
            cm.setTorchMode(id, on)
            if (on) "Torch jala di" else "Torch band kar di"
        } catch (e: Exception) {
            "Torch ma masla aaya"
        }
    }

    private fun volume(ctx: Context, w: List<String>): String {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        fun has(vararg k: String): Boolean = k.any { w.contains(it) }
        return try {
            if (has("full", "max", "maximum")) {
                am.setStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    am.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
                    AudioManager.FLAG_SHOW_UI
                )
                "Awaz full kar di"
            } else if (has("kam", "kum", "ghatao", "ghata", "down", "low", "dheema", "decrease")) {
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                "Awaz kam kar di"
            } else {
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                "Awaz badha di"
            }
        } catch (e: Exception) {
            "Awaz ma masla aaya"
        }
    }

    private fun battery(ctx: Context): String {
        val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return "Battery ka pata nahi chala"
        val lv = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val sc = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val st = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val pct = if (lv >= 0 && sc > 0) lv * 100 / sc else -1
        val chg = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
        return "Battery " + pct + " percent hai" + (if (chg) ", charge ho rahi hai" else "")
    }

    private fun skel(s: String): String {
        var x = s.lowercase(Locale.ROOT).replace(Regex("[^a-z]"), "")
        x = x.replace("vh", "w").replace("v", "w").replace("ph", "f")
            .replace("kh", "k").replace("sh", "s").replace("ch", "c").replace("h", "")
        x = x.replace(Regex("[aeiouy]"), "")
        val sb = StringBuilder()
        for (c in x) {
            if (sb.isEmpty() || sb[sb.length - 1] != c) sb.append(c)
        }
        return sb.toString()
    }

    private fun lev(a: String, b: String): Int {
        val d = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = d[0]
            d[0] = i
            for (j in 1..b.length) {
                val tmp = d[j]
                d[j] = minOf(d[j] + 1, d[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                prev = tmp
            }
        }
        return d[b.length]
    }

    private fun openApp(ctx: Context, w: List<String>): String {
        val words = w.filter { !STOP.contains(it) }
        if (words.isEmpty()) return "Kaun si app kholni hai?"
        val cands = ArrayList<String>()
        cands.add(skel(words.joinToString("")))
        for (x in words) cands.add(skel(x))
        val pm = ctx.packageManager
        val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        var best = 99
        var bestLabel = ""
        var bestPkg = ""
        for (ri in pm.queryIntentActivities(q, 0)) {
            val pkg = ri.activityInfo.packageName
            if (pkg == ctx.packageName) continue
            val label = ri.loadLabel(pm).toString()
            val sa = skel(label)
            if (sa.isEmpty()) continue
            for (sq in cands) {
                if (sq.isEmpty()) continue
                var sc = 99
                if (sa == sq) sc = 0
                else if (sa.length >= 4 && sq.length >= 4 && lev(sa, sq) <= 1) sc = 1
                else if (sq.length >= 3 && sa.length >= 3 && (sa.contains(sq) || sq.contains(sa))) sc = 2
                if (sc < best) {
                    best = sc
                    bestLabel = label
                    bestPkg = pkg
                }
            }
        }
        if (bestPkg.isEmpty()) return words.joinToString(" ") + " naam ki app nahi mili"
        val li = pm.getLaunchIntentForPackage(bestPkg) ?: return bestLabel + " khul nahi saki"
        li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            if (!android.provider.Settings.canDrawOverlays(ctx)) return "Pehle Display over other apps ki ijazat den"
            ctx.startActivity(li)
            bestLabel + " khol raha hun"
        } catch (e: Exception) {
            bestLabel + " kholne ma masla aaya"
        }
    }
}
