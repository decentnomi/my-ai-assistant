package com.decentnomi.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

class WakeService : Service(), RecognitionListener, TextToSpeech.OnInitListener {

    companion object {
        const val CH = "nomi_wake"
    }

    private lateinit var prefs: SharedPreferences
    private lateinit var actions: Actions
    private val h = Handler(Looper.getMainLooper())
    private var sr: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var started = false
    private var mode = 0
    private var busy = false
    private val safety = Runnable { resume() }

    private val words = listOf(
        "hello nomi", "hello no me", "hello know me", "hello nome", "hello gnome", "hello numi",
        "nomi", "nomy", "numi", "noomi", "naumi", "gnomi", "nomee", "nomie",
        "\u0928\u094B\u092E\u0940", "\u0928\u094B\u092E\u093F", "\u0928\u094C\u092E\u0940", "\u0928\u0942\u092E\u0940",
        "\u0928\u094B\u092E\u0947", "\u0928\u0949\u092E\u0940", "\u0928\u0941\u092E\u0940",
        "\u0646\u0648\u0645\u06CC", "\u0646\u0648\u0645\u0626\u06CC"
    )

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("assistant", Context.MODE_PRIVATE)
        actions = Actions(this)
        tts = TextToSpeech(this, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            ttsReady = true
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    h.post { resume() }
                }
                override fun onError(utteranceId: String?) {
                    h.post { resume() }
                }
            })
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!started) {
            started = true
            startInForeground()
            beepMute(true)
            h.post { listen() }
        }
        val t = intent?.getStringExtra("text")
        if (t != null && t.isNotBlank()) {
            h.post { process(t) }
        }
        if (intent?.getBooleanExtra("listen", false) == true) {
            h.post { startCommandMode() }
        }
        return START_STICKY
    }

    private fun buildNotif(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CH) else Notification.Builder(this)
        return b.setContentTitle("Hello Nomi")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    private fun startInForeground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CH, "Hello Nomi", NotificationManager.IMPORTANCE_LOW))
        }
        val n = buildNotif("'Hello Nomi' bolen")
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(1, n)
        }
    }

    private fun setStatus(s: String) {
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(1, buildNotif(s))
        } catch (e: Exception) {
        }
    }

    private fun beepMute(mute: Boolean) {
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val d = if (mute) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE
            am.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, d, 0)
            am.adjustStreamVolume(AudioManager.STREAM_SYSTEM, d, 0)
        } catch (e: Exception) {
        }
    }

    private fun tone() {
        try {
            ToneGenerator(AudioManager.STREAM_MUSIC, 80).startTone(ToneGenerator.TONE_PROP_BEEP, 150)
        } catch (e: Exception) {
        }
    }

    private fun listen() {
        if (!started || busy) return
        try {
            if (sr == null) {
                sr = SpeechRecognizer.createSpeechRecognizer(this)
                sr?.setRecognitionListener(this)
            }
            val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            val lang = (prefs.getString("rlang", "hi-IN") ?: "hi-IN").ifBlank { "hi-IN" }
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
            i.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            i.putExtra("android.speech.extra.SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS", 1000L)
            sr?.startListening(i)
            setStatus(if (mode == 1) "Boliye, sun raha hoon..." else "'Hello Nomi' bolen")
        } catch (e: Exception) {
            h.postDelayed({ listen() }, 2000)
        }
    }

    private fun wakeRemainder(s: String): String? {
        val low = s.lowercase(Locale.ROOT)
        var bestIdx = -1
        var bestLen = 0
        for (w in words) {
            var from = 0
            while (true) {
                val i = low.indexOf(w, from)
                if (i < 0) break
                if (i == 0 || !low[i - 1].isLetter()) {
                    if (bestIdx < 0 || i < bestIdx) {
                        bestIdx = i
                        bestLen = w.length
                    }
                    break
                }
                from = i + 1
            }
        }
        if (bestIdx < 0) return null
        return low.substring(bestIdx + bestLen).trim().trimStart(',', '.', '!', '?', '-', ' ', '\u060C', '\u0964').trim()
    }

    private fun startCommandMode() {
        mode = 1
        tone()
        try {
            sr?.cancel()
        } catch (e: Exception) {
        }
        h.postDelayed({ listen() }, 200)
    }

    private fun resume() {
        h.removeCallbacks(safety)
        if (!busy) return
        busy = false
        mode = 0
        h.postDelayed({ listen() }, 400)
    }

    private fun process(text: String) {
        busy = true
        h.removeCallbacks(safety)
        try {
            sr?.cancel()
        } catch (e: Exception) {
        }
        ChatLog.add(true, text)
        setStatus("Soch raha hoon...")
        val pr = NomiCore.tryPending(text, actions, prefs)
        if (pr != null) {
            deliver(pr)
            return
        }
        val key = prefs.getString("key", "") ?: ""
        if (key.isBlank()) {
            deliver(Reply("Pehle app ma Gemini key paste karke Save karen", "Pehle app me Gemini key save karen", "en-IN"))
            return
        }
        Thread {
            val res = NomiCore.ask(prefs, text)
            h.post {
                if (res.first in 200..299) {
                    val raw = NomiCore.extractText(res.second)
                    if (raw != null) {
                        NomiCore.remember(text, raw)
                        deliver(NomiCore.dispatch(raw, actions, prefs))
                    } else {
                        deliver(Reply("Jawab samajh nahi aaya", "Jawab samajh nahi aaya", "en-IN"))
                    }
                } else {
                    deliver(Reply("Gemini error " + res.first + ": " + res.second.take(150), "Gemini ma masla aaya", "en-IN"))
                }
            }
        }.start()
    }

    private fun deliver(r: Reply) {
        ChatLog.add(false, r.display)
        speak(r.speak, r.lang)
    }

    private fun speak(text: String, lang: String) {
        val t = tts
        if (t == null || !ttsReady || text.isBlank()) {
            resume()
            return
        }
        val loc = if (lang.isBlank()) Locale("en", "IN") else Locale.forLanguageTag(lang)
        val r = t.setLanguage(loc)
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
            t.setLanguage(Locale("en", "IN"))
        }
        setStatus("Bol raha hoon...")
        t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "u")
        h.postDelayed(safety, 20000)
    }

    override fun onResults(results: Bundle?) {
        if (busy) return
        val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (list.isNullOrEmpty()) {
            h.postDelayed({ listen() }, 150)
            return
        }
        if (mode == 1) {
            mode = 0
            val t = list[0].trim()
            if (t.isNotBlank()) process(t) else listen()
            return
        }
        for (s in list) {
            val rest = wakeRemainder(s)
            if (rest != null) {
                if (rest.length >= 2) process(rest) else startCommandMode()
                return
            }
        }
        h.postDelayed({ listen() }, 100)
    }

    override fun onError(error: Int) {
        if (busy) return
        if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
            stopSelf()
            return
        }
        if (error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT || error == SpeechRecognizer.ERROR_NO_MATCH) {
            mode = 0
        }
        val wait = if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 1200L else 200L
        h.postDelayed({ listen() }, wait)
    }

    override fun onPartialResults(partialResults: Bundle?) {}
    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {}
    override fun onEvent(eventType: Int, params: Bundle?) {}

    override fun onDestroy() {
        started = false
        h.removeCallbacksAndMessages(null)
        try {
            sr?.destroy()
        } catch (e: Exception) {
        }
        tts?.shutdown()
        beepMute(false)
        super.onDestroy()
    }
}
