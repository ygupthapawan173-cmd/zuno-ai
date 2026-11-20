package com.zeno.ai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import ai.picovoice.porcupine.Porcupine
import ai.picovoice.porcupine.PorcupineManager
import ai.picovoice.porcupine.PorcupineManagerCallback
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * Foreground service that keeps listening for "Hey Zeno".
 * When heard, it hands over to [ZenoEngine], which listens, answers and speaks even with the screen off.
 */
class WakeWordService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var running = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Hey Zeno", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        openIntent = open
        val notification = buildNotification("\"Hey Zeno\" ani cheppandi")
        try {
            ServiceCompat.startForeground(this, 1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } catch (e: Exception) {
            stopSelf()
        }
    }

    private var openIntent: PendingIntent? = null
    private var lastShown = ""

    private fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Zeno vintunnadi")
            .setContentText(text)
            .setContentIntent(openIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .build()

    /** Shows the last thing the recognizer heard, so you can see why a wake word did or did not match. */
    private fun showHeard(text: String) {
        if (text.isBlank() || text == lastShown) return
        lastShown = text
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(1, buildNotification("Vinnadi: \"$text\""))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!running) {
            running = true
            loop()
        }
        return START_STICKY
    }

    // ---- Offline wake word (Porcupine, built-in keyword "Jarvis"). Used when a Picovoice key is set.
    private var porcupine: PorcupineManager? = null

    private fun startPorcupine(key: String): Boolean {
        val engine = ZenoEngine.get(this)
        return try {
            porcupine?.delete()
            porcupine = PorcupineManager.Builder()
                .setAccessKey(key)
                .setKeyword(Porcupine.BuiltInKeyword.JARVIS)
                .setSensitivity(0.7f)
                .build(applicationContext, PorcupineManagerCallback { main.post { onPorcupineWake() } })
            porcupine?.start()
            engine.logEvent("wake word engine started (say: Jarvis)")
            showHeard("Jarvis ani cheppandi")
            true
        } catch (e: Exception) {
            engine.logEvent("wake word engine error: " + (e.message ?: e.javaClass.simpleName))
            porcupine = null
            false
        }
    }

    private fun onPorcupineWake() {
        val engine = ZenoEngine.get(this)
        engine.logEvent("wake word detected: Jarvis")
        try {
            porcupine?.stop()
        } catch (e: Exception) {
        }
        engine.onWake()
        main.postDelayed({ resumePorcupine() }, 3000)
    }

    private fun resumePorcupine() {
        if (!running) return
        val engine = ZenoEngine.get(this)
        if (engine.orb != OrbState.Idle) {
            main.postDelayed({ resumePorcupine() }, 800)
            return
        }
        try {
            porcupine?.start()
        } catch (e: Exception) {
            engine.logEvent("wake word restart error: " + (e.message ?: ""))
        }
    }

    private fun loop() {
        if (!running) return
        val engine = ZenoEngine.get(this)
        val key = engine.settings.picoKey.trim()
        if (key.isNotEmpty()) {
            if (porcupine != null || startPorcupine(key)) return
        }
        if (engine.orb != OrbState.Idle) {
            main.postDelayed({ loop() }, 800)
            return
        }
        listenForWake()
    }

    private fun listenForWake() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            main.postDelayed({ loop() }, 5000)
            return
        }
        recognizer?.destroy()
        val r = SpeechRecognizer.createSpeechRecognizer(this)
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onError(error: Int) {
                val delay = if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ||
                    error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS
                ) 4000L else 600L
                main.postDelayed({ loop() }, delay)
            }

            override fun onResults(results: Bundle?) {
                if (heardWake(results)) wake() else main.postDelayed({ loop() }, 300)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                if (heardWake(partialResults)) wake()
            }
        })
        recognizer = r
        r.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
        )
    }

    private fun heardWake(b: Bundle?): Boolean {
        val list = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: return false
        list.firstOrNull()?.let { showHeard(it) }
        return list.any { s ->
            val l = s.lowercase()
            val tokens = l.split(Regex("[^\\p{L}]+")).filter { it.isNotEmpty() }
            WAKE.any { l.contains(it) } || tokens.any { it in NAMES }
        }
    }

    private fun wake() {
        recognizer?.destroy()
        recognizer = null
        main.postDelayed({ ZenoEngine.get(this).onWake() }, 300)
        main.postDelayed({ loop() }, 2500)
    }

    override fun onDestroy() {
        running = false
        main.removeCallbacksAndMessages(null)
        try {
            porcupine?.stop()
            porcupine?.delete()
        } catch (e: Exception) {
        }
        porcupine = null
        recognizer?.destroy()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "zeno_wake"
        private val NAMES = setOf(
            "zeno", "zino", "jeno", "xeno", "zenu", "jino", "zenno", "zeeno", "jeeno", "zena", "jena", "chino", "cheno", "zenoh", "zeenu",
        )
        private val WAKE = listOf("hey zeno", "hey zino", "hey jeno", "hey xeno", "hey zenu", "a zeno", "hi zeno", "జీనో", "జెనో")

        fun start(ctx: Context) {
            try {
                ContextCompat.startForegroundService(ctx, Intent(ctx, WakeWordService::class.java))
            } catch (e: Exception) {
            }
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, WakeWordService::class.java))
        }
    }
}
