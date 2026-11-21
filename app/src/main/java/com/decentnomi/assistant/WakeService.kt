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
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

class WakeService : Service(), RecognitionListener {

    private var sr: SpeechRecognizer? = null
    private val h = Handler(Looper.getMainLooper())
    private var paused = false
    private var started = false

    private val words = listOf(
        "nomi", "nomy", "numi", "gnomi", "namo", "hello nome",
        "hello no me", "hello know me", "hello knowme", "helo nomi"
    )

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!started) {
            started = true
            startInForeground()
            beep(true)
            h.post { listen() }
        }
        return START_STICKY
    }

    private fun startInForeground() {
        val chId = "nomi_wake"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(chId, "Hello Nomi sun raha hai", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, chId) else Notification.Builder(this)
        val n = b.setContentTitle("Hello Nomi")
            .setContentText("'Hello Nomi' bolen, main sun raha hoon")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(1, n)
        }
    }

    private fun beep(mute: Boolean) {
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val d = if (mute) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE
            am.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, d, 0)
            am.adjustStreamVolume(AudioManager.STREAM_SYSTEM, d, 0)
        } catch (e: Exception) {
        }
    }

    private fun listen() {
        if (paused || !started) return
        try {
            if (sr == null) {
                sr = SpeechRecognizer.createSpeechRecognizer(this)
                sr?.setRecognitionListener(this)
            }
            val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            i.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            sr?.startListening(i)
        } catch (e: Exception) {
            h.postDelayed({ listen() }, 2000)
        }
    }

    private fun check(list: List<String>?) {
        if (paused || list == null) return
        for (t in list) {
            val s = t.lowercase(Locale.ROOT)
            if (words.any { s.contains(it) }) {
                wake()
                return
            }
        }
    }

    private fun wake() {
        paused = true
        h.removeCallbacksAndMessages(null)
        try {
            sr?.cancel()
        } catch (e: Exception) {
        }
        beep(false)
        try {
            val i = Intent(this, MainActivity::class.java)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            i.putExtra("wake", true)
            startActivity(i)
        } catch (e: Exception) {
        }
        h.postDelayed({
            paused = false
            beep(true)
            listen()
        }, 20000)
    }

    override fun onResults(results: Bundle?) {
        check(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION))
        if (!paused) h.postDelayed({ listen() }, 200)
    }

    override fun onPartialResults(partialResults: Bundle?) {
        check(partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION))
    }

    override fun onError(error: Int) {
        if (paused) return
        if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
            stopSelf()
            return
        }
        val wait = if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 1500L else 250L
        h.postDelayed({ listen() }, wait)
    }

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
        beep(false)
        super.onDestroy()
    }
}
