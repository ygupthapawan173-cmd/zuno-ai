package com.zeno.ai

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/** Speech-to-text (SpeechRecognizer) and text-to-speech (TextToSpeech). Call from the main thread. */
class VoiceController(private val ctx: Context) {
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    var onPartial: (String) -> Unit = {}
    var onFinal: (String) -> Unit = {}
    var onError: (Int) -> Unit = {}
    var onLevel: (Float) -> Unit = {}
    var onSpeakDone: () -> Unit = {}

    init {
        tts = TextToSpeech(ctx.applicationContext) { status -> ttsReady = status == TextToSpeech.SUCCESS }
    }

    fun listen(lang: String) {
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
            onError(-1)
            return
        }
        stopSpeaking()
        recognizer?.destroy()
        val r = SpeechRecognizer.createSpeechRecognizer(ctx)
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {
                onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
            }

            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                onLevel(0f)
            }

            override fun onError(error: Int) {
                onLevel(0f)
                this@VoiceController.onError(error)
            }

            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull().orEmpty()
                if (text.isBlank()) this@VoiceController.onError(SpeechRecognizer.ERROR_NO_MATCH)
                else onFinal(text)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.let { onPartial(it) }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        recognizer = r
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        r.startListening(intent)
    }

    fun cancelListening() {
        recognizer?.cancel()
        onLevel(0f)
    }

    fun speak(text: String) {
        val clean = text.replace(Regex("[*_`#>]"), "").replace(Regex("\\s+"), " ").trim()
        if (!ttsReady || clean.isEmpty()) {
            main.post { onSpeakDone() }
            return
        }
        val telugu = clean.count { it in 'ఀ'..'౿' } > clean.length / 4
        tts?.language = Locale.forLanguageTag(if (telugu) "te-IN" else "en-IN")
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                if (utteranceId == "last") main.post { onSpeakDone() }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                if (utteranceId == "last") main.post { onSpeakDone() }
            }
        })
        val parts = clean.chunked(3000)
        parts.forEachIndexed { i, p ->
            tts?.speak(
                p,
                if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD,
                null,
                if (i == parts.lastIndex) "last" else "part$i",
            )
        }
    }

    fun stopSpeaking() {
        tts?.stop()
    }

    fun release() {
        recognizer?.destroy()
        tts?.shutdown()
    }
}
