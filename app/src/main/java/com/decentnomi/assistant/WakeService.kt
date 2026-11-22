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
        const val CH = "decent_wake"
        val WORDS = listOf(
            "decent", "descent", "disent", "desent", "deesent", "dicent",
            "डिसेंट", "डीसेंट", "डेसेंट", "डिसेन्ट", "ڈیسنٹ"
        )
    }

    private lateinit var prefs: SharedPreferences
    private val h = Handler(Looper.getMainLooper())
    private var sr: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var started = false
    private var mode = 0
    private var nextMode = 0
    private var busy = false
    private var preferOffline = true
    private val safety = Runnable { resume() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("decent", Context.MODE_PRIVATE)
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
            Bus.running = true
            Bus.add("Service chalu")
            startInForeground()
            beepMute(true)
            h.post { listen() }
        }
        return START_STICKY
    }

    private fun buildNotif(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CH) else Notification.Builder(this)
        return b.setContentTitle("Decent")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    private fun startInForeground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CH, "Decent", NotificationManager.IMPORTANCE_LOW))
        }
        val n = buildNotif("Decent bolen")
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

    private fun listen() {
        if (!started || busy) return
        try {
            if (sr == null) {
                sr = SpeechRecognizer.createSpeechRecognizer(this)
                sr?.setRecognitionListener(this)
            }
            val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            val lang = prefs.getString("rlang", "hi-IN") ?: "hi-IN"
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
            i.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            i.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline)
            i.putExtra("android.speech.extra.SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS", 800L)
            sr?.startListening(i)
            setStatus(if (mode == 1) "Boliye, sun raha hoon..." else "Decent bolen")
        } catch (e: Exception) {
            Bus.add("listen masla: " + e.message)
            h.postDelayed({ listen() }, 2000)
        }
    }

    private fun wakeRemainder(s: String): String? {
        val low = s.lowercase(Locale.ROOT)
        var bestIdx = -1
        var bestLen = 0
        for (w in WORDS) {
            var from = 0
            while (true) {
                val i = low.indexOf(w, from)
                if (i < 0) break
                val endOk = i + w.length >= low.length || !low[i + w.length].isLetter()
                if ((i == 0 || !low[i - 1].isLetter()) && endOk) {
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
        return low.substring(bestIdx + bestLen).trim().trimStart(',', '.', '!', '?', '-', ' ').trim()
    }

    private fun speak(text: String, lang: String) {
        val t = tts
        if (t == null || !ttsReady) {
            resume()
            return
        }
        val r = t.setLanguage(Locale.forLanguageTag(lang))
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
            t.setLanguage(Locale("en", "IN"))
            t.speak("Ji, boliye", TextToSpeech.QUEUE_FLUSH, null, "u")
        } else {
            t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "u")
        }
        h.postDelayed(safety, 8000)
    }

    private fun startCommandMode() {
        busy = true
        nextMode = 1
        h.removeCallbacks(safety)
        try {
            sr?.cancel()
        } catch (e: Exception) {
        }
        speak("जी, बोलिए", "hi-IN")
    }

    private fun resume() {
        h.removeCallbacks(safety)
        if (!busy) return
        busy = false
        mode = nextMode
        nextMode = 0
        h.postDelayed({ listen() }, 400)
    }

    override fun onResults(results: Bundle?) {
        if (busy) return
        val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (list.isNullOrEmpty()) {
            h.postDelayed({ listen() }, 150)
            return
        }
        Bus.heard++
        if (mode == 1) {
            mode = 0
            Bus.add("HUKM: " + list[0])
            h.postDelayed({ listen() }, 100)
            return
        }
        for (s in list) {
            val rest = wakeRemainder(s)
            if (rest != null) {
                Bus.wakes++
                Bus.add("WAKE ok: " + s)
                if (rest.length >= 2) {
                    Bus.add("HUKM (ek saans): " + rest)
                    h.postDelayed({ listen() }, 100)
                } else {
                    startCommandMode()
                }
                return
            }
        }
        Bus.add("suna: " + list[0].take(60))
        h.postDelayed({ listen() }, 100)
    }

    override fun onError(error: Int) {
        if (busy) return
        if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
            Bus.add("Mic ki ijazat nahi")
            stopSelf()
            return
        }
        if (error == 12 || error == 13) preferOffline = false
        if (error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT || error == SpeechRecognizer.ERROR_NO_MATCH) {
            mode = 0
        } else {
            Bus.add("err " + error)
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
        Bus.running = false
        Bus.add("Service band")
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
