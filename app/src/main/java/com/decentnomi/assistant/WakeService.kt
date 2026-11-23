package com.decentnomi.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

class WakeService : Service() {

    private val h = Handler(Looper.getMainLooper())
    private var rec: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var hindiOk = false
    private var alive = false
    private var preferOffline = true
    private var speaking = false
    private var wl: PowerManager.WakeLock? = null

    private val speakTimeout = Runnable {
        if (speaking) {
            Bus.add("TTS timeout (awaz nahi aayi)")
            afterSpeak()
        }
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel("decent", "Decent", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, "decent")
        else Notification.Builder(this)
        val n = b.setContentTitle("Decent")
            .setContentText("Main sun raha hun, bolen")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(1, n)
        }
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "decent:wake")
            wl?.acquire()
        } catch (e: Exception) {
        }
        tts = TextToSpeech(this) { st ->
            ttsReady = (st == TextToSpeech.SUCCESS)
            Bus.add("TTS init " + st)
            if (ttsReady) setupVoice()
        }
        alive = true
        Bus.running = true
        Bus.add("Service chalu")
        Bus.changed()
        listen()
    }

    private fun setupVoice() {
        val t = tts ?: return
        val r = t.setLanguage(Locale("hi", "IN"))
        hindiOk = r >= TextToSpeech.LANG_AVAILABLE
        if (!hindiOk) t.setLanguage(Locale("en", "IN"))
        Bus.add("TTS hindi awaz: " + (if (hindiOk) "haan" else "nahi, English"))
        t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {
                h.post { Bus.add("TTS bol raha"); Bus.changed() }
            }
            override fun onDone(id: String?) {
                h.post { Bus.add("TTS khatam"); Bus.changed(); afterSpeak() }
            }
            @Deprecated("old")
            override fun onError(id: String?) {
                h.post { Bus.add("TTS ERR"); Bus.changed(); afterSpeak() }
            }
        })
    }

    override fun onStartCommand(i: Intent?, f: Int, s: Int): Int = START_STICKY

    private fun muteBeep(on: Boolean) {
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val d = if (on) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE
            am.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, d, 0)
            am.adjustStreamVolume(AudioManager.STREAM_SYSTEM, d, 0)
        } catch (e: Exception) {
        }
    }

    private fun killRec() {
        try {
            rec?.setRecognitionListener(null)
            rec?.cancel()
            rec?.destroy()
        } catch (e: Exception) {
        }
        rec = null
    }

    private fun later(ms: Long) {
        h.postDelayed({ if (alive && !speaking) listen() }, ms)
    }

    private fun listen() {
        if (!alive) return
        killRec()
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Bus.add("Recognizer nahi mila")
            Bus.changed()
            later(3000)
            return
        }
        val lang = getSharedPreferences("decent", Context.MODE_PRIVATE)
            .getString("rlang", "hi-IN") ?: "hi-IN"
        val r = SpeechRecognizer.createSpeechRecognizer(this)
        rec = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(p: Bundle?) {}
            override fun onEvent(t: Int, p: Bundle?) {}
            override fun onError(e: Int) {
                muteBeep(false)
                if (e != SpeechRecognizer.ERROR_NO_MATCH && e != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    Bus.add("err " + e)
                }
                if ((e == 12 || e == 13) && preferOffline) {
                    preferOffline = false
                    Bus.add("offline nahi, online use")
                }
                Bus.changed()
                later(if (e == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 1500 else 400)
            }
            override fun onResults(res: Bundle?) {
                muteBeep(false)
                val list = res?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: arrayListOf()
                handle(list)
            }
        })
        val it = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        it.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        it.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
        it.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline)
        it.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        it.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        it.putExtra("android.speech.extra.SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS", 800)
        it.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        muteBeep(true)
        try {
            r.startListening(it)
        } catch (e: Exception) {
            muteBeep(false)
            Bus.add("start ERR " + e.message)
            later(1500)
        }
    }

    private fun handle(list: List<String>) {
        if (list.isEmpty() || list[0].isBlank()) {
            later(300)
            return
        }
        val said = list[0]
        Bus.heard++
        Bus.wakes++
        Bus.add("HUKM: " + said)
        Bus.changed()
        val rep = Commands.run(this, Roman.of(said)); if (rep != null) { Bus.add("JAWAB: " + rep); Bus.changed(); speakBack(rep) } else { Bus.add("Gemini se pooch raha"); Bus.changed(); Thread { val r = Gem.ask(this, said); h.post { if (alive) { Bus.add("JAWAB: " + r); Bus.changed(); speakBack(r) } } }.start() }
    }

    private fun speakBack(s: String) {
        killRec()
        val t = tts
        if (!ttsReady || t == null) {
            Bus.add("TTS tayyar nahi")
            Bus.changed()
            later(300)
            return
        }
        speaking = true
        h.removeCallbacks(speakTimeout)
        h.postDelayed(speakTimeout, 15000)
        val rc = t.speak(s, TextToSpeech.QUEUE_FLUSH, null, "say")
        if (rc != TextToSpeech.SUCCESS) {
            Bus.add("speak ERR " + rc)
            Bus.changed()
            afterSpeak()
        }
    }

    private fun afterSpeak() {
        if (!speaking) return
        speaking = false
        h.removeCallbacks(speakTimeout)
        if (!alive) return
        h.postDelayed({ if (alive && !speaking) listen() }, 250)
    }

    override fun onDestroy() {
        alive = false
        h.removeCallbacksAndMessages(null)
        killRec()
        muteBeep(false)
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
        }
        try {
            wl?.release()
        } catch (e: Exception) {
        }
        Bus.running = false
        Bus.add("Service band")
        Bus.changed()
        super.onDestroy()
    }
}
