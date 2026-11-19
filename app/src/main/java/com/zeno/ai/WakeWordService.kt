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
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Zeno vintunnadi")
            .setContentText("\"Hey Zeno\" ani cheppandi")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        try {
            ServiceCompat.startForeground(this, 1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } catch (e: Exception) {
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!running) {
            running = true
            loop()
        }
        return START_STICKY
    }

    private fun loop() {
        if (!running) return
        val engine = ZenoEngine.get(this)
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
        return list.any { s ->
            val l = s.lowercase()
            WAKE.any { l.contains(it) }
        }
    }

    private fun wake() {
        recognizer?.destroy()
        recognizer = null
        main.postDelayed({ ZenoEngine.get(this).startListening() }, 700)
        main.postDelayed({ loop() }, 2500)
    }

    override fun onDestroy() {
        running = false
        main.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "zeno_wake"
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
