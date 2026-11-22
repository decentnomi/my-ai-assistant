package com.decentnomi.assistant

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.telephony.SmsManager
import android.view.KeyEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Actions(private val c: Context) {

    private fun go(i: Intent): String? {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            c.startActivity(i)
            null
        } catch (e: Exception) {
            "Nahi khul saka: " + (e.message ?: "")
        }
    }

    private fun findContact(name: String): Pair<String, String>? {
        if (name.isBlank()) return null
        if (c.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null
        val cur = c.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?",
            arrayOf("%" + name + "%"),
            null
        ) ?: return null
        var best: Pair<String, String>? = null
        var bestScore = -1
        val q = name.lowercase().trim()
        cur.use {
            while (it.moveToNext()) {
                val n = it.getString(0) ?: continue
                val num = it.getString(1) ?: continue
                val l = n.lowercase().trim()
                val sc = if (l == q) 3 else if (l.startsWith(q)) 2 else 1
                if (sc > bestScore) {
                    bestScore = sc
                    best = Pair(n, num)
                }
            }
        }
        return best
    }

    private fun normalize(num: String): String {
        val d = num.filter { it.isDigit() }
        return if (num.trim().startsWith("+")) d
        else if (d.startsWith("00")) d.drop(2)
        else if (d.startsWith("0")) "92" + d.drop(1)
        else d
    }

    fun call(name: String): String? {
        val ct = findContact(name) ?: return "Contact " + name + " nahi mila"
        return go(Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode(ct.second))))
    }

    fun whatsapp(name: String, msg: String, send: Boolean): String? {
        val ct = findContact(name) ?: return "Contact " + name + " nahi mila"
        val url = "https://wa.me/" + normalize(ct.second) + "?text=" + Uri.encode(msg)
        val i = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        i.setPackage("com.whatsapp")
        if (send) AssistantAccessibilityService.arm()
        return go(i)
    }

    fun sms(name: String, msg: String): String? {
        val ct = findContact(name) ?: return "Contact " + name + " nahi mila"
        return try {
            val sm = SmsManager.getDefault()
            val parts = sm.divideMessage(msg)
            sm.sendMultipartTextMessage(ct.second, null, parts, null, null)
            null
        } catch (e: Exception) {
            "SMS nahi gaya: " + (e.message ?: "")
        }
    }

    fun openApp(name: String): String? {
        val pm = c.packageManager
        val i = Intent(Intent.ACTION_MAIN)
        i.addCategory(Intent.CATEGORY_LAUNCHER)
        val list = pm.queryIntentActivities(i, 0)
        val q = name.lowercase().trim()
        var best: ResolveInfo? = null
        var bs = 0
        for (ri in list) {
            val l = ri.loadLabel(pm).toString().lowercase()
            val s = if (l == q) 4 else if (l.startsWith(q)) 3 else if (l.contains(q)) 2 else if (l.length > 2 && q.contains(l)) 1 else 0
            if (s > bs) {
                bs = s
                best = ri
            }
        }
        val b = best ?: return Fuzzy.fallback(c, name)
        val li = pm.getLaunchIntentForPackage(b.activityInfo.packageName) ?: return "App " + name + " nahi khul saki"
        return go(li)
    }

    private fun torch(on: Boolean): String? {
        return try {
            val cm = c.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            var id: String? = null
            for (x in cm.cameraIdList) {
                if (cm.getCameraCharacteristics(x).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true) {
                    id = x
                    break
                }
            }
            cm.setTorchMode(id ?: cm.cameraIdList[0], on)
            null
        } catch (e: Exception) {
            "Torch ma masla: " + (e.message ?: "")
        }
    }

    fun toggle(target: String, on: Boolean): String? {
        val t = target.lowercase()
        if (t == "flashlight" || t == "torch") return torch(on)
        if (t == "bluetooth" && android.os.Build.VERSION.SDK_INT <= 30) {
            return try {
                val ad = BluetoothAdapter.getDefaultAdapter()
                if (on) ad.enable() else ad.disable()
                null
            } catch (e: Exception) {
                "Bluetooth ma masla: " + (e.message ?: "")
            }
        }
        val labels: List<String> = when (t) {
            "wifi" -> listOf("Wi-Fi", "WLAN", "Wi\u2011Fi", "Internet")
            "mobile_data" -> listOf("Mobile data", "Mobile Data", "Data")
            "airplane" -> listOf("Airplane mode", "Flight mode", "Airplane")
            "hotspot" -> listOf("Hotspot", "Mobile Hotspot")
            "location" -> listOf("Location")
            "rotate" -> listOf("Auto-rotate", "Auto rotate", "Rotate")
            "dnd" -> listOf("Do not disturb", "DND")
            "nfc" -> listOf("NFC")
            "bluetooth" -> listOf("Bluetooth")
            else -> return "Ye toggle mujhe nahi aata: " + target
        }
        val s = AssistantAccessibilityService.instance ?: return "Accessibility ki ijazat on karen"
        s.toggleTile(labels, on)
        return null
    }

    fun brightness(level: Int, dir: String): String? {
        if (!Settings.System.canWrite(c)) {
            go(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + c.packageName)))
            return "Brightness ke liye ijazat den, phir dobara bolen"
        }
        val cr = c.contentResolver
        val cur = try {
            Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS)
        } catch (e: Exception) {
            128
        }
        val d = dir.lowercase()
        val pct = if (level in 0..100) level
        else if (d == "up") cur * 100 / 255 + 25
        else if (d == "down") cur * 100 / 255 - 25
        else 50
        val p = pct.coerceIn(5, 100)
        val v = p * 255 / 100
        return try {
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, v)
            val now = Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS)
            "Brightness " + p + " percent. Pehle " + cur + ", ab " + now
        } catch (e: Exception) {
            "Brightness ma masla: " + (e.message ?: "")
        }
    }

    fun volume(level: Int, dir: String): String? {
        val am = c.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val mx = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val d = dir.lowercase()
        try {
            if (level in 0..100) {
                am.setStreamVolume(AudioManager.STREAM_MUSIC, mx * level / 100, AudioManager.FLAG_SHOW_UI)
            } else if (d == "up") {
                for (k in 0 until 3) am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            } else if (d == "down") {
                for (k in 0 until 3) am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            } else if (d == "mute") {
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
            }
        } catch (e: Exception) {
            return "Volume ma masla: " + (e.message ?: "")
        }
        return null
    }

    fun ringer(mode: String): String? {
        val am = c.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return try {
            am.ringerMode = when (mode.lowercase()) {
                "silent" -> AudioManager.RINGER_MODE_SILENT
                "vibrate" -> AudioManager.RINGER_MODE_VIBRATE
                else -> AudioManager.RINGER_MODE_NORMAL
            }
            null
        } catch (e: Exception) {
            "Ringer badalne ke liye Do Not Disturb ki ijazat chahiye"
        }
    }

    private fun key(code: Int) {
        val am = c.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    fun media(cmd: String): String? {
        when (cmd.lowercase()) {
            "play" -> key(KeyEvent.KEYCODE_MEDIA_PLAY)
            "pause" -> key(KeyEvent.KEYCODE_MEDIA_PAUSE)
            "next" -> key(KeyEvent.KEYCODE_MEDIA_NEXT)
            "previous" -> key(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        }
        return null
    }

    fun alarm(hour: Int, minute: Int): String? {
        val i = Intent(AlarmClock.ACTION_SET_ALARM)
        i.putExtra(AlarmClock.EXTRA_HOUR, hour)
        i.putExtra(AlarmClock.EXTRA_MINUTES, minute)
        i.putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        return go(i)
    }

    fun timer(seconds: Int): String? {
        val i = Intent(AlarmClock.ACTION_SET_TIMER)
        i.putExtra(AlarmClock.EXTRA_LENGTH, seconds)
        i.putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        return go(i)
    }

    fun system(cmd: String): String? {
        val s = AssistantAccessibilityService.instance ?: return "Accessibility ki ijazat on karen"
        s.globalAction(cmd)
        return null
    }

    fun settings(target: String): String? {
        val a = when (target.lowercase()) {
            "wifi" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "display" -> Settings.ACTION_DISPLAY_SETTINGS
            "sound" -> Settings.ACTION_SOUND_SETTINGS
            "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            "apps" -> Settings.ACTION_APPLICATION_SETTINGS
            "airplane" -> Settings.ACTION_AIRPLANE_MODE_SETTINGS
            "data" -> Settings.ACTION_DATA_ROAMING_SETTINGS
            "nfc" -> Settings.ACTION_NFC_SETTINGS
            "date" -> Settings.ACTION_DATE_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        return go(Intent(a))
    }

    fun info(kind: String): String? {
        return if (kind.lowercase() == "battery") {
            val bm = c.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            "Battery " + bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) + " percent hai"
        } else {
            "Abhi " + SimpleDateFormat("hh:mm a, EEEE d MMMM", Locale.ENGLISH).format(Date()) + " hai"
        }
    }

    fun search(q: String): String? {
        return go(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(q))))
    }

    fun maps(q: String): String? {
        return go(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(q))))
    }

    fun camera(): String? {
        return go(Intent(MediaStore.ACTION_IMAGE_CAPTURE))
    }
}
