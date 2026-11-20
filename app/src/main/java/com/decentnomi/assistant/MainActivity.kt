package com.decentnomi.assistant

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
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
    private val prefs by lazy { getSharedPreferences("assistant", Context.MODE_PRIVATE) }

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
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("en", "IN")
        }
    }

    private fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "reply")
    }

    private fun startListening() {
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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startListening()
        } else {
            statusView.text = "Mic ki ijazat chahiye"
        }
    }

    private fun askGemini(question: String) {
        val key = prefs.getString("key", "") ?: ""
        val model = prefs.getString("model", "gemini-flash-latest") ?: "gemini-flash-latest"
        if (key.isBlank()) {
            statusView.text = "Pehle upar key paste karke Save karen"
            return
        }
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
                val sys = "Tum ek chhota madadgar voice assistant ho. Jawab Roman Urdu ma, bohat mukhtasir (1 se 3 jumlay) do."
                val body = JSONObject()
                body.put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", sys))))
                body.put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", question)))))
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
                statusView.text = if (ok) "Jawab aa gaya" else "Masla aaya"
                replyView.text = "Assistant: " + answer
                if (ok) speak(answer)
            }
        }.start()
    }

    override fun onDestroy() {
        recognizer?.destroy()
        tts?.shutdown()
        super.onDestroy()
    }
}
