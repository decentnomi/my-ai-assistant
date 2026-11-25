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

        add(root, "6. Offline sunne ka model download (42 MB, ek dafa)", { Offline.ready(this) }) {
            if (Offline.ready(this)) {
                android.widget.Toast.makeText(this, "Model pehle se maujood hai", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                android.widget.Toast.makeText(this, "Download shuru. 1 se 3 minute ruken, app band na karen", android.widget.Toast.LENGTH_LONG).show()
                Thread {
                    val err = Offline.download(this) { p ->
                        runOnUiThread { android.widget.Toast.makeText(this, "Download " + p + "%", android.widget.Toast.LENGTH_SHORT).show() }
                    }
                    runOnUiThread {
                        android.widget.Toast.makeText(this, err ?: "Download mukammal", android.widget.Toast.LENGTH_LONG).show()
                        refresh()
                    }
                }.start()
            }
        }
        add(root, "7. Offline sunna chalu/band (beep nahi aati)", { prefs().getString("engine", "google") == "vosk" }) {
            if (!Offline.ready(this)) {
                android.widget.Toast.makeText(this, "Pehle button 6 se model download karen", android.widget.Toast.LENGTH_LONG).show()
            } else {
                val on = prefs().getString("engine", "google") == "vosk"
                prefs().edit().putString("engine", if (on) "google" else "vosk").apply()
                android.widget.Toast.makeText(this, "Badal gaya. Ab Decent app ma BAND karke dobara SHURU karen", android.widget.Toast.LENGTH_LONG).show()
                refresh()
            }
        }
        add(root, "8. Awaz ka test (sab se tez awaz khud chun le)", { (prefs().getString("ttsvoice", "") ?: "") != "" }) {
            runVoiceTest()
        }
        add(root, "9. Awaz badlen (dabate jayen, jo pasand aaye rakh len)", { (prefs().getString("ttsvoice2", "") ?: "") != "" }) {
            nextVoice()
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

    private var vt: android.speech.tts.TextToSpeech? = null
    private val benchRes = ArrayList<Pair<String, Long>>()
    private var benchVoices: List<android.speech.tts.Voice> = emptyList()
    private var benchT0 = 0L
    private var benchI = 0
    private var benchStarted = false

    private fun toast(s: String) {
        runOnUiThread { android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_LONG).show() }
    }

    private fun runVoiceTest() {
        toast("Awaz ka test shuru. Kuch awazen aayengi, 1 se 2 minute ruken")
        benchRes.clear()
        vt = android.speech.tts.TextToSpeech(this) { st ->
            val t = vt
            if (st != android.speech.tts.TextToSpeech.SUCCESS || t == null) {
                toast("TTS shuru nahi hua")
                return@TextToSpeech
            }
            benchVoices = (t.voices ?: emptySet<android.speech.tts.Voice>()).filter {
                !it.isNetworkConnectionRequired && !it.features.contains("notInstalled") &&
                    (it.locale.language == "hi" || it.locale.language == "en")
            }.sortedBy { it.name }.take(12)
            t.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                override fun onStart(u: String?) {
                    if (!benchStarted && benchI < benchVoices.size) {
                        benchStarted = true
                        benchRes.add(Pair(benchVoices[benchI].name, System.currentTimeMillis() - benchT0))
                    }
                }
                override fun onDone(u: String?) {
                    runOnUiThread {
                        benchI++
                        benchNext()
                    }
                }
                @Deprecated("old")
                override fun onError(u: String?) {
                    runOnUiThread {
                        benchI++
                        benchNext()
                    }
                }
            })
            benchI = 0
            runOnUiThread { benchNext() }
        }
    }

    private fun benchNext() {
        val t = vt ?: return
        if (benchI >= benchVoices.size) {
            val best = benchRes.minByOrNull { it.second }
            if (best != null) {
                prefs().edit().putString("ttsvoice", best.first).apply()
                toast("Sab se tez awaz: " + best.first + " (" + best.second + " ms). Decent ko BAND karke dobara SHURU karen")
            } else {
                toast("Koi awaz test na ho saki")
            }
            t.shutdown()
            vt = null
            refresh()
            return
        }
        benchStarted = false
        val my = benchI
        t.voice = benchVoices[benchI]
        benchT0 = System.currentTimeMillis()
        t.speak(
            "Jaanch number " + (benchI + 20) + ", aaj ka din kaisa guzra",
            android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "b" + benchI
        )
        android.os.Handler(mainLooper).postDelayed({
            if (benchI == my && vt != null) {
                benchI++
                benchNext()
            }
        }, 9000)
    }

    private var pt: android.speech.tts.TextToSpeech? = null
    private var ptList: List<android.speech.tts.Voice> = emptyList()
    private var pi = -1

    private fun nextVoice() {
        val t0 = pt
        if (t0 == null) {
            pt = android.speech.tts.TextToSpeech(this) { st ->
                val t = pt
                if (st != android.speech.tts.TextToSpeech.SUCCESS || t == null) {
                    toast("TTS shuru nahi hua")
                    return@TextToSpeech
                }
                ptList = (t.voices ?: emptySet<android.speech.tts.Voice>()).filter {
                    !it.isNetworkConnectionRequired && !it.features.contains("notInstalled") &&
                        (it.locale.language == "hi" || it.locale.language == "en")
                }.sortedWith(compareBy<android.speech.tts.Voice>({ it.locale.language != "hi" }, { it.name }))
                runOnUiThread { nextVoice() }
            }
            return
        }
        if (ptList.isEmpty()) {
            toast("Koi local awaz nahi mili")
            return
        }
        pi = (pi + 1) % ptList.size
        val v = ptList[pi]
        t0.voice = v
        prefs().edit().putString("ttsvoice2", v.name).apply()
        toast("Awaz " + (pi + 1) + " / " + ptList.size + ": " + v.name)
        val line = if (v.locale.language == "hi") {
            "नमस्ते, मैं डिसेंट हूँ। आप का दिन कैसा गुज़रा?"
        } else {
            "Hello, I am Decent. How was your day?"
        }
        t0.speak(line, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "pick")
        refresh()
    }
}
