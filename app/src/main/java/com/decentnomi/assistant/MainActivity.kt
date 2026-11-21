package com.decentnomi.assistant

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.text.InputType
import android.util.Base64
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {

    private lateinit var keyBox: EditText
    private lateinit var modelBox: EditText
    private lateinit var autoSend: CheckBox
    private lateinit var autoListen: CheckBox
    private lateinit var statusView: TextView
    private lateinit var youView: TextView
    private lateinit var replyView: TextView
    private lateinit var actions: PhoneActions
    private var tts: TextToSpeech? = null
    private var busy = false
    private val prefs by lazy { getSharedPreferences("assistant", Context.MODE_PRIVATE) }

    private val systemPrompt = """
Tumhara naam Hello Nomi hai. Tum ek phone assistant ho. User ki awaz kisi bhi zubaan ma ho sakti hai (Urdu, Punjabi, English, Hindi, Chinese, Arabic, Pashto, Sindhi waghera). Uska hukm samajh kar sirf ek JSON object do, aur kuch nahi.
Har JSON ma "heard" (user ne jo kaha, usi zubaan ma chhota text) aur "lang" (user ki zubaan ka code, jaise ur-PK, pa-IN, en-US, zh-CN) zaroor shamil karo.
Naam hamesha English letters ma likho. Mumkin actions:
{"action":"call","name":"Ali"}
{"action":"whatsapp","name":"Ali","message":"user ki taraf se chhota, qudrati message (Urdu ya Punjabi ho to Roman Urdu ma, warna user ki zubaan ma), main ke andaz ma"}
{"action":"sms","name":"Ali","message":"user ki taraf se chhota message"}
{"action":"open_app","app":"YouTube"}
{"action":"toggle","target":"wifi|bluetooth|mobile_data|airplane|hotspot|location|flashlight|rotate|dnd|nfc","on":true}
{"action":"brightness","level":0 se 100} ya {"action":"brightness","dir":"up|down"}
{"action":"volume","level":0 se 100} ya {"action":"volume","dir":"up|down|mute"}
{"action":"ringer","mode":"silent|vibrate|normal"}
{"action":"media","cmd":"play|pause|next|previous"}
{"action":"alarm","hour":0 se 23,"minute":0 se 59}
{"action":"timer","seconds":300}   (agar user "X minute baad" kahe to timer use karo)
{"action":"system","cmd":"home|back|recents|notifications|screenshot|lock|power_menu"}
{"action":"settings","target":"wifi|bluetooth|display|sound|battery|location|apps|airplane|data|nfc|date|main"}
{"action":"info","kind":"battery|time"}
{"action":"search","query":"..."}
{"action":"maps","query":"..."}
{"action":"camera"}
{"action":"chat","reply":"user ki apni zubaan ma 1 se 2 jumlon ka mukhtasir jawab"}   (baqi har baat ke liye)
"""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)

        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(pad, pad * 2, pad, pad)

        val title = TextView(this)
        title.text = "Hello Nomi"
        title.textSize = 28f

        keyBox = EditText(this)
        keyBox.hint = "Gemini API key yahan paste karen"
        keyBox.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        keyBox.setText(prefs.getString("key", ""))

        modelBox = EditText(this)
        modelBox.hint = "Model ka naam"
        modelBox.setText(prefs.getString("model", "gemini-flash-latest"))

        val saveBtn = Button(this)
        saveBtn.text = "Save karen"
        saveBtn.setOnClickListener {
            prefs.edit()
                .putString("key", keyBox.text.toString().trim())
                .putString("model", modelBox.text.toString().trim())
                .apply()
            statusView.text = "Save ho gaya"
        }

        val accBtn = Button(this)
        accBtn.text = "Accessibility ki ijazat (ek baar)"
        accBtn.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        autoSend = CheckBox(this)
        autoSend.text = "WhatsApp message khud send karen"
        autoSend.isChecked = prefs.getBoolean("autosend", true)
        autoSend.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("autosend", checked).apply()
        }

        autoListen = CheckBox(this)
        autoListen.text = "Mode A: app khulte hi khud sunna shuru karen"
        autoListen.isChecked = prefs.getBoolean("autolisten", false)
        autoListen.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("autolisten", checked).apply()
        }

        val micBtn = Button(this)
        micBtn.text = "BOLEN"
        micBtn.textSize = 22f
        micBtn.setOnClickListener { startRecording() }

        statusView = TextView(this)
        statusView.textSize = 16f
        youView = TextView(this)
        youView.textSize = 18f
        replyView = TextView(this)
        replyView.textSize = 18f

        root.addView(title)
        root.addView(keyBox)
        root.addView(modelBox)
        root.addView(saveBtn)
        root.addView(accBtn)
        root.addView(autoSend)
        root.addView(autoListen)
        WakeUi.add(this, root)
        root.addView(micBtn)
        root.addView(statusView)
        root.addView(youView)
        root.addView(replyView)

        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)

        actions = PhoneActions(this) { text -> say(text) }

        val need = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.SEND_SMS
        ).filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (need.isNotEmpty()) {
            requestPermissions(need.toTypedArray(), 1)
        } else {
            maybeAutoListen()
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        maybeAutoListen()
    }

    private fun maybeAutoListen() {
        val wakeFlag = intent?.getBooleanExtra("wake", false) == true
        intent?.removeExtra("wake")
        if ((autoListen.isChecked || wakeFlag) &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        ) {
            Handler(Looper.getMainLooper()).postDelayed({ startRecording() }, 700L)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("en", "IN")
        }
    }

    private fun speak(text: String, lang: String) {
        val t = tts ?: return
        val loc = if (lang.isBlank()) Locale("en", "IN") else Locale.forLanguageTag(lang)
        val r = t.setLanguage(loc)
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
            t.setLanguage(Locale("en", "IN"))
        }
        t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "reply")
    }

    private fun say(text: String) {
        replyView.text = "Assistant: " + text
        speak(text, "en-IN")
    }

    private fun sayLang(text: String, lang: String) {
        replyView.text = "Assistant: " + text
        speak(text, lang)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        statusView.text = "Ijazat check ho gayi. Ab BOLEN dabayen."
    }

    private fun startRecording() {
        if (busy) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
            return
        }
        val key = prefs.getString("key", "") ?: ""
        if (key.isBlank()) {
            statusView.text = "Pehle upar key paste karke Save karen"
            return
        }
        busy = true
        tts?.stop()
        statusView.text = "Sun raha hoon... bolen"
        Thread {
            val wav = recordUtterance()
            if (wav == null) {
                runOnUiThread {
                    busy = false
                    statusView.text = "Kuch sunai nahi diya. Dobara BOLEN dabayen."
                }
            } else {
                runOnUiThread { statusView.text = "Samajh raha hoon..." }
                sendToGemini(wav)
            }
        }.start()
    }

    private fun recordUtterance(): ByteArray? {
        val rate = 16000
        val minBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuf <= 0) return null
        val rec = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            rate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuf, rate) * 2
        )
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            return null
        }
        val pcm = ByteArrayOutputStream()
        val buf = ShortArray(rate / 10)
        val bytes = ByteArray(buf.size * 2)
        var spoke = false
        var silentMs = 0
        var totalMs = 0
        var maxPeak = 0
        rec.startRecording()
        while (true) {
            val n = rec.read(buf, 0, buf.size)
            if (n <= 0) break
            var peak = 0
            for (i in 0 until n) {
                val s = buf[i].toInt()
                bytes[2 * i] = (s and 0xFF).toByte()
                bytes[2 * i + 1] = ((s shr 8) and 0xFF).toByte()
                val a = if (s < 0) -s else s
                if (a > peak) peak = a
            }
            pcm.write(bytes, 0, n * 2)
            val ms = n * 1000 / rate
            totalMs += ms
            if (peak > maxPeak) maxPeak = peak
            val loud = peak > maxOf(1000, maxPeak / 5)
            if (loud) {
                spoke = true
                silentMs = 0
            } else if (spoke) {
                silentMs += ms
            }
            if (spoke && silentMs >= 900) break
            if (!spoke && totalMs >= 6000) break
            if (totalMs >= 12000) break
        }
        try {
            rec.stop()
        } catch (e: Exception) {
        }
        rec.release()
        return if (spoke) wavWrap(pcm.toByteArray(), rate) else null
    }

    private fun wavWrap(pcm: ByteArray, rate: Int): ByteArray {
        val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        h.put("RIFF".toByteArray())
        h.putInt(pcm.size + 36)
        h.put("WAVE".toByteArray())
        h.put("fmt ".toByteArray())
        h.putInt(16)
        h.putShort(1)
        h.putShort(1)
        h.putInt(rate)
        h.putInt(rate * 2)
        h.putShort(2)
        h.putShort(16)
        h.put("data".toByteArray())
        h.putInt(pcm.size)
        return h.array() + pcm
    }

    private fun callGemini(key: String, model: String, audioB64: String, thinkingOff: Boolean): Pair<Int, String> {
        return try {
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent")
            val c = url.openConnection() as HttpURLConnection
            c.requestMethod = "POST"
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("x-goog-api-key", key)
            c.connectTimeout = 20000
            c.readTimeout = 40000
            c.doOutput = true
            val body = JSONObject()
            body.put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt))))
            val audioPart = JSONObject().put(
                "inlineData",
                JSONObject().put("mimeType", "audio/wav").put("data", audioB64)
            )
            val textPart = JSONObject().put("text", "Ye user ki awaz hai. Hukm samajh kar sirf JSON do.")
            val parts = JSONArray().put(audioPart).put(textPart)
            body.put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", parts)))
            val gen = JSONObject().put("responseMimeType", "application/json")
            if (thinkingOff) {
                gen.put("thinkingConfig", JSONObject().put("thinkingBudget", 0))
            }
            body.put("generationConfig", gen)
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            Pair(code, stream.bufferedReader().readText())
        } catch (e: Exception) {
            Pair(-1, "Masla: " + e.message)
        }
    }

    private fun extractText(body: String): String? {
        return try {
            JSONObject(body).getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
        } catch (e: Exception) {
            null
        }
    }

    private fun sendToGemini(wav: ByteArray) {
        val key = prefs.getString("key", "") ?: ""
        val model = prefs.getString("model", "gemini-flash-latest") ?: "gemini-flash-latest"
        val b64 = Base64.encodeToString(wav, Base64.NO_WRAP)
        var res = callGemini(key, model, b64, true)
        if (res.first == 400) {
            res = callGemini(key, model, b64, false)
        }
        val code = res.first
        val body = res.second
        runOnUiThread {
            busy = false
            val text = if (code in 200..299) extractText(body) else null
            if (text != null) {
                statusView.text = "Jawab aa gaya"
                handleReply(text)
            } else {
                statusView.text = "Masla aaya"
                replyView.text = "Assistant: Gemini error " + code + ": " + body.take(300)
            }
        }
    }

    private fun handleReply(raw: String) {
        val fence = "\u0060\u0060\u0060"
        val clean = raw.trim().removePrefix(fence + "json").removePrefix(fence).removeSuffix(fence).trim()
        val j = try { JSONObject(clean) } catch (e: Exception) { null }
        if (j == null) {
            say(clean)
            return
        }
        val heard = j.optString("heard")
        if (heard.isNotBlank()) {
            youView.text = "Aap: " + heard
        }
        val lang = j.optString("lang")
        val action = j.optString("action", "chat")
        when (action) {
            "call" -> actions.call(j.optString("name"))
            "whatsapp" -> actions.whatsapp(j.optString("name"), j.optString("message"), autoSend.isChecked)
            "sms" -> actions.sms(j.optString("name"), j.optString("message"))
            "open_app" -> actions.openApp(j.optString("app"))
            "toggle" -> actions.toggle(j.optString("target"), j.optBoolean("on", true))
            "brightness" -> BrightnessHelper.set(this, j.optInt("level", -1), j.optString("dir")) { t -> say(t) }
            "volume" -> actions.volume(j.optInt("level", -1), j.optString("dir"))
            "ringer" -> actions.ringer(j.optString("mode"))
            "media" -> actions.media(j.optString("cmd"))
            "alarm" -> actions.alarm(j.optInt("hour", 0), j.optInt("minute", 0))
            "timer" -> actions.timer(j.optInt("seconds", 60))
            "system" -> actions.system(j.optString("cmd"))
            "settings" -> actions.settings(j.optString("target"))
            "info" -> actions.info(j.optString("kind"))
            "search" -> actions.search(j.optString("query"))
            "maps" -> actions.maps(j.optString("query"))
            "camera" -> actions.camera()
            else -> sayLang(j.optString("reply", clean), lang)
        }
    }

    override fun onDestroy() {
        tts?.shutdown()
        super.onDestroy()
    }
}
