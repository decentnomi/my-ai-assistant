package com.decentnomi.assistant

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {

    private lateinit var keyBox: EditText
    private lateinit var modelBox: EditText
    private lateinit var statusView: TextView
    private lateinit var youView: TextView
    private lateinit var replyView: TextView
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var langCode = "ur-PK"
    private var busy = false
    private val prefs by lazy { getSharedPreferences("assistant", Context.MODE_PRIVATE) }

    private val systemPrompt = """
Tum ek phone assistant ho. User ki baat (Urdu, English ya Roman Urdu) ko sirf ek JSON ma badlo, aur kuch nahi likho.
Formats:
{"action":"call","name":"<contact ka naam English letters ma>"} jab user kisi ko call karna chahe.
{"action":"open_app","app":"<app ka naam English ma, jaise WhatsApp, YouTube, Chrome>"} jab user koi app kholna chahe.
{"action":"chat","reply":"<Roman Urdu ma 1 se 3 jumlon ka mukhtasir jawab>"} baqi har baat ke liye.
"""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)

        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(pad, pad * 2, pad, pad)

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

        val langBtn = Button(this)
        langBtn.text = "Zubaan: Urdu (tap karke badlen)"
        langBtn.setOnClickListener {
            if (langCode == "ur-PK") {
                langCode = "en-US"
                langBtn.text = "Zubaan: English (tap karke badlen)"
            } else {
                langCode = "ur-PK"
                langBtn.text = "Zubaan: Urdu (tap karke badlen)"
            }
        }

        val micBtn = Button(this)
        micBtn.text = "BOLEN"
        micBtn.textSize = 22f
        micBtn.setOnClickListener { startListening() }

        statusView = TextView(this)
        statusView.textSize = 16f
        youView = TextView(this)
        youView.textSize = 18f
        replyView = TextView(this)
        replyView.textSize = 18f

        root.addView(keyBox)
        root.addView(modelBox)
        root.addView(saveBtn)
        root.addView(langBtn)
        root.addView(micBtn)
        root.addView(statusView)
        root.addView(youView)
        root.addView(replyView)

        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)

        val need = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE
        ).filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (need.isNotEmpty()) {
            requestPermissions(need.toTypedArray(), 1)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("en", "IN")
        }
    }

    private fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "reply")
    }

    private fun say(text: String) {
        replyView.text = "Assistant: " + text
        speak(text)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        statusView.text = "Ijazat check ho gayi. Ab BOLEN dabayen."
    }

    private fun startListening() {
        if (busy) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            statusView.text = "Is phone ma speech recognition nahi mili"
            return
        }
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onError(error: Int) {
                statusView.text = "Samajh nahi aaya (error " + error + "). Dobara BOLEN dabayen."
            }
            override fun onResults(results: Bundle?) {
                val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val said = list?.firstOrNull()
                if (said == null) {
                    statusView.text = "Kuch sunai nahi diya"
                } else {
                    youView.text = "Aap: " + said
                    askGemini(said)
                }
            }
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, langCode)
        recognizer?.startListening(i)
        statusView.text = "Sun raha hoon... bolen"
    }

    private fun askGemini(question: String) {
        val key = prefs.getString("key", "") ?: ""
        val model = prefs.getString("model", "gemini-flash-latest") ?: "gemini-flash-latest"
        if (key.isBlank()) {
            statusView.text = "Pehle upar key paste karke Save karen"
            return
        }
        busy = true
        statusView.text = "Soch raha hoon..."
        Thread {
            var ok = false
            val answer = try {
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
                body.put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", question)))))
                body.put("generationConfig", JSONObject().put("responseMimeType", "application/json"))
                c.outputStream.use { it.write(body.toString().toByteArray()) }
                val code = c.responseCode
                val stream = if (code in 200..299) c.inputStream else c.errorStream
                val text = stream.bufferedReader().readText()
                if (code in 200..299) {
                    ok = true
                    JSONObject(text).getJSONArray("candidates").getJSONObject(0)
                        .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
                } else {
                    "Gemini error " + code + ": " + text.take(300)
                }
            } catch (e: Exception) {
                "Masla: " + e.message
            }
            runOnUiThread {
                busy = false
                if (ok) {
                    statusView.text = "Jawab aa gaya"
                    handleReply(answer)
                } else {
                    statusView.text = "Masla aaya"
                    replyView.text = "Assistant: " + answer
                }
            }
        }.start()
    }

    private fun handleReply(raw: String) {
        val fence = "\u0060\u0060\u0060"
        val clean = raw.trim().removePrefix(fence + "json").removePrefix(fence).removeSuffix(fence).trim()
        val j = try { JSONObject(clean) } catch (e: Exception) { null }
        val action = j?.optString("action", "chat") ?: "chat"
        if (action == "call") {
            doCall(j?.optString("name") ?: "")
        } else if (action == "open_app") {
            doOpenApp(j?.optString("app") ?: "")
        } else {
            say(j?.optString("reply", clean) ?: clean)
        }
    }

    private fun findContact(name: String): Pair<String, String>? {
        val phone = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val cols = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val sel = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?"
        val cursor = contentResolver.query(phone, cols, sel, arrayOf("%" + name + "%"), null)
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

    private fun doCall(name: String) {
        if (name.isBlank()) {
            say("Naam samajh nahi aaya")
            return
        }
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED
        ) {
            say("Contacts aur Phone ki ijazat dein (Settings, Apps, My AI Assistant, Permissions)")
            return
        }
        val found = findContact(name)
        if (found == null) {
            say("Contact " + name + " nahi mila")
            return
        }
        say(found.first + " ko call laga raha hoon")
        try {
            startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode(found.second))))
        } catch (e: Exception) {
            say("Call nahi lag saki: " + e.message)
        }
    }

    private fun doOpenApp(app: String) {
        if (app.isBlank()) {
            say("App ka naam samajh nahi aaya")
            return
        }
        val pm = packageManager
        val main = Intent(Intent.ACTION_MAIN, null)
        main.addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(main, 0)
        val match = apps.firstOrNull { it.loadLabel(pm).toString().contains(app, ignoreCase = true) }
        val launch = if (match != null) pm.getLaunchIntentForPackage(match.activityInfo.packageName) else null
        if (launch == null) {
            say(app + " app nahi mili")
            return
        }
        say(app + " khol raha hoon")
        startActivity(launch)
    }

    override fun onDestroy() {
        recognizer?.destroy()
        tts?.shutdown()
        super.onDestroy()
    }
}
