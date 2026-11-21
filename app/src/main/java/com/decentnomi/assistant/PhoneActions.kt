package com.decentnomi.assistant

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.telephony.SmsManager
import android.view.KeyEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PhoneActions(private val act: Activity, private val say: (String) -> Unit) {

    private fun has(p: String): Boolean {
        return act.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
    }

    private fun go(i: Intent, okMsg: String) {
        try {
            act.startActivity(i)
            if (okMsg.isNotEmpty()) say(okMsg)
        } catch (e: Exception) {
            say("Ye nahi ho saka: " + e.message)
        }
    }

    private fun findContact(name: String): Pair<String, String>? {
        val phone = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val cols = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val sel = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?"
        val cursor = act.contentResolver.query(phone, cols, sel, arrayOf("%" + name + "%"), null)
        val list = ArrayList<Pair<String, String>>()
        cursor?.use {
            while (it.moveToNext()) {
                list.add(Pair(it.getString(0) ?: "", it.getString(1) ?: ""))
            }
        }
        return list.firstOrNull { it.first.equals(name, ignoreCase = true) }
            ?: list.firstOrNull { it.first.startsWith(name, ignoreCase = true) }
            ?: list.firstOrNull()
    }

    private fun normalizeNumber(raw: String): String {
        var d = raw.filter { it.isDigit() }
        if (d.startsWith("00")) {
            d = d.substring(2)
        } else if (d.startsWith("0")) {
            d = "92" + d.substring(1)
        }
        return d
    }

    fun call(name: String) {
        if (name.isBlank()) {
            say("Naam samajh nahi aaya")
            return
        }
        if (!has(Manifest.permission.READ_CONTACTS) || !has(Manifest.permission.CALL_PHONE)) {
            say("Contacts aur Phone ki ijazat dein (Settings, Apps, Hello Nomi, Permissions)")
            return
        }
        val found = findContact(name)
        if (found == null) {
            say("Contact " + name + " nahi mila")
            return
        }
        go(Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode(found.second))), found.first + " ko call laga raha hoon")
    }

    fun whatsapp(name: String, message: String, autoSend: Boolean) {
        if (name.isBlank() || message.isBlank()) {
            say("Naam ya message samajh nahi aaya")
            return
        }
        if (!has(Manifest.permission.READ_CONTACTS)) {
            say("Contacts ki ijazat dein (Settings, Apps, Hello Nomi, Permissions)")
            return
        }
        val found = findContact(name)
        if (found == null) {
            say("Contact " + name + " nahi mila")
            return
        }
        val number = normalizeNumber(found.second)
        val uri = Uri.parse("https://wa.me/" + number + "?text=" + Uri.encode(message))
        say(found.first + " ko WhatsApp par likh raha hoon: " + message)
        if (autoSend) {
            AssistantAccessibilityService.arm()
        }
        var opened = false
        for (pkg in arrayOf("com.whatsapp", "com.whatsapp.w4b")) {
            if (opened) break
            try {
                val i = Intent(Intent.ACTION_VIEW, uri)
                i.setPackage(pkg)
                act.startActivity(i)
                opened = true
            } catch (e: Exception) {
            }
        }
        if (!opened) {
            say("WhatsApp nahi mili")
        }
    }

    fun sms(name: String, message: String) {
        if (name.isBlank() || message.isBlank()) {
            say("Naam ya message samajh nahi aaya")
            return
        }
        if (!has(Manifest.permission.READ_CONTACTS) || !has(Manifest.permission.SEND_SMS)) {
            say("Contacts aur SMS ki ijazat dein (Settings, Apps, Hello Nomi, Permissions)")
            return
        }
        val found = findContact(name)
        if (found == null) {
            say("Contact " + name + " nahi mila")
            return
        }
        try {
            @Suppress("DEPRECATION") val sm = SmsManager.getDefault()
            val parts = sm.divideMessage(message)
            sm.sendMultipartTextMessage(found.second, null, parts, null, null)
            say(found.first + " ko SMS bhej diya: " + message)
        } catch (e: Exception) {
            say("SMS nahi gaya: " + e.message)
        }
    }

    fun openApp(app: String) {
        if (app.isBlank()) {
            say("App ka naam samajh nahi aaya")
            return
        }
        val pm = act.packageManager
        val main = Intent(Intent.ACTION_MAIN, null)
        main.addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(main, 0)
        val match = apps.firstOrNull { it.loadLabel(pm).toString().contains(app, ignoreCase = true) }
        val launch = if (match != null) pm.getLaunchIntentForPackage(match.activityInfo.packageName) else null
        if (launch == null) {
            say(app + " app nahi mili")
            return
        }
        go(launch, app + " khol raha hoon")
    }

    @Suppress("DEPRECATION", "MissingPermission")
    fun toggle(target: String, on: Boolean) {
        val t = target.lowercase(Locale.ROOT)
        if (t == "flashlight" || t == "torch") {
            torch(on)
            return
        }
        if (t == "bluetooth" && Build.VERSION.SDK_INT <= 30) {
            val ad = BluetoothAdapter.getDefaultAdapter()
            if (ad == null) {
                say("Is phone ma Bluetooth nahi hai")
                return
            }
            try {
                if (on) ad.enable() else ad.disable()
                say(if (on) "Bluetooth on kar diya" else "Bluetooth off kar diya")
            } catch (e: Exception) {
                say("Bluetooth ma masla: " + e.message)
            }
            return
        }
        val labels = when (t) {
            "wifi" -> listOf("Wi-Fi", "WLAN", "Wifi")
            "bluetooth" -> listOf("Bluetooth")
            "mobile_data", "data", "internet" -> listOf("Mobile data", "Internet", "Data")
            "airplane" -> listOf("Airplane mode", "Flight mode", "Airplane")
            "hotspot" -> listOf("Hotspot")
            "location" -> listOf("Location")
            "rotate" -> listOf("Auto-rotate", "Auto rotate", "Rotation")
            "dnd" -> listOf("Do not disturb")
            "nfc" -> listOf("NFC")
            else -> null
        }
        if (labels == null) {
            say("Ye kaam abhi mumkin nahi: " + target)
            return
        }
        val svc = AssistantAccessibilityService.instance
        if (svc == null) {
            say("Pehle Accessibility on karen")
            return
        }
        say(target + (if (on) " on" else " off") + " kar raha hoon")
        svc.toggleTile(labels, on)
    }

    private fun torch(on: Boolean) {
        try {
            val cm = act.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            var id: String? = null
            for (cid in cm.cameraIdList) {
                val flash = cm.getCameraCharacteristics(cid).get(CameraCharacteristics.FLASH_INFO_AVAILABLE)
                if (flash == true) {
                    id = cid
                    break
                }
            }
            if (id == null) {
                say("Flash nahi mili")
                return
            }
            cm.setTorchMode(id, on)
            say(if (on) "Torch on kar di" else "Torch off kar di")
        } catch (e: Exception) {
            say("Torch ma masla: " + e.message)
        }
    }

    fun brightness(level: Int) {
        if (!Settings.System.canWrite(act)) {
            say("Brightness ke liye ijazat chahiye. Settings khul rahi hai, Hello Nomi ko allow karen, phir dobara bolen.")
            try {
                act.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + act.packageName)))
            } catch (e: Exception) {
            }
            return
        }
        val pct = level.coerceIn(0, 100)
        val v = maxOf(10, pct * 255 / 100)
        try {
            Settings.System.putInt(act.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            Settings.System.putInt(act.contentResolver, Settings.System.SCREEN_BRIGHTNESS, v)
            say("Brightness " + pct + " percent kar di")
        } catch (e: Exception) {
            say("Brightness ma masla: " + e.message)
        }
    }

    fun volume(level: Int, dir: String) {
        val am = act.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val d = dir.lowercase(Locale.ROOT)
        if (level in 0..100) {
            am.setStreamVolume(AudioManager.STREAM_MUSIC, level * max / 100, AudioManager.FLAG_SHOW_UI)
            say("Awaz " + level + " percent kar di")
        } else if (d == "up") {
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            say("Awaz barha di")
        } else if (d == "down") {
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            say("Awaz kam kar di")
        } else if (d == "mute") {
            am.setStreamVolume(AudioManager.STREAM_MUSIC, 0, AudioManager.FLAG_SHOW_UI)
        } else {
            say("Awaz ka hukm samajh nahi aaya")
        }
    }

    fun ringer(mode: String) {
        val am = act.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val m = when (mode.lowercase(Locale.ROOT)) {
            "silent" -> AudioManager.RINGER_MODE_SILENT
            "vibrate" -> AudioManager.RINGER_MODE_VIBRATE
            "normal" -> AudioManager.RINGER_MODE_NORMAL
            else -> -1
        }
        if (m == -1) {
            say("Mode samajh nahi aaya")
            return
        }
        try {
            am.ringerMode = m
            say("Theek hai, " + mode + " mode")
        } catch (e: SecurityException) {
            say("Silent ke liye Do Not Disturb ki ijazat chahiye. Settings khul rahi hai.")
            try {
                act.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            } catch (e2: Exception) {
            }
        }
    }

    fun media(cmd: String) {
        val code = when (cmd.lowercase(Locale.ROOT)) {
            "play", "pause", "play_pause" -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
            "previous" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            "stop" -> KeyEvent.KEYCODE_MEDIA_STOP
            else -> -1
        }
        if (code == -1) {
            say("Media ka hukm samajh nahi aaya")
            return
        }
        val am = act.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        say("Theek hai")
    }

    fun alarm(hour: Int, minute: Int) {
        val i = Intent(AlarmClock.ACTION_SET_ALARM)
        i.putExtra(AlarmClock.EXTRA_HOUR, hour)
        i.putExtra(AlarmClock.EXTRA_MINUTES, minute)
        i.putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        go(i, "Alarm " + hour + ":" + String.format(Locale.ENGLISH, "%02d", minute) + " ka laga diya")
    }

    fun timer(seconds: Int) {
        val i = Intent(AlarmClock.ACTION_SET_TIMER)
        i.putExtra(AlarmClock.EXTRA_LENGTH, seconds)
        i.putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        go(i, "Timer " + seconds + " second ka laga diya")
    }

    fun system(cmd: String) {
        val svc = AssistantAccessibilityService.instance
        if (svc == null) {
            say("Pehle Accessibility on karen")
            return
        }
        val ok = svc.globalAction(cmd.lowercase(Locale.ROOT))
        say(if (ok) "Theek hai" else "Ye kaam nahi ho saka")
    }

    fun settings(target: String) {
        val action = when (target.lowercase(Locale.ROOT)) {
            "wifi" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "display", "brightness" -> Settings.ACTION_DISPLAY_SETTINGS
            "sound", "volume" -> Settings.ACTION_SOUND_SETTINGS
            "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            "apps" -> Settings.ACTION_APPLICATION_SETTINGS
            "airplane" -> Settings.ACTION_AIRPLANE_MODE_SETTINGS
            "data", "mobile_data" -> Settings.ACTION_DATA_ROAMING_SETTINGS
            "nfc" -> Settings.ACTION_NFC_SETTINGS
            "date" -> Settings.ACTION_DATE_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        go(Intent(action), "Settings khol raha hoon")
    }

    fun info(kind: String) {
        if (kind.lowercase(Locale.ROOT) == "battery") {
            val bm = act.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            say("Battery " + pct + " percent hai")
        } else {
            val t = SimpleDateFormat("hh:mm a, EEEE d MMMM", Locale.ENGLISH).format(Date())
            say("Abhi waqt hai " + t)
        }
    }

    fun search(query: String) {
        if (query.isBlank()) {
            say("Kya search karna hai, samajh nahi aaya")
            return
        }
        go(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(query))), query + " search kar raha hoon")
    }

    fun maps(query: String) {
        if (query.isBlank()) {
            say("Jagah ka naam samajh nahi aaya")
            return
        }
        go(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(query))), query + " Maps ma dhoondh raha hoon")
    }

    fun camera() {
        go(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA), "Camera khol raha hoon")
    }
}
