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
    var onTtsIssue: (String) -> Unit = {}

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
        if (clean.isEmpty()) {
            main.post { onSpeakDone() }
            return
        }
        if (!ttsReady) {
            onTtsIssue("Text-to-speech inka ready kaledu. Konchem sepu aagi malli try cheyandi.")
            main.post { onSpeakDone() }
            return
        }
        val telugu = clean.count { it in 'ఀ'..'౿' } > clean.length / 4
        val result = tts?.setLanguage(Locale.forLanguageTag(if (telugu) "te-IN" else "en-IN"))
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            val lang = if (telugu) "Telugu" else "English"
            onTtsIssue(
                "$lang voice ee phone lo ledu. Phone Settings > Text-to-speech output > Google lo " +
                    "$lang voice download cheyandi."
            )
            main.post { onSpeakDone() }
            return
        }
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

    fun diagnose(): String {
        fun avail(tag: String): String = when (tts?.isLanguageAvailable(Locale.forLanguageTag(tag))) {
            TextToSpeech.LANG_AVAILABLE, TextToSpeech.LANG_COUNTRY_AVAILABLE, TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE -> "OK"
            TextToSpeech.LANG_MISSING_DATA -> "voice data download cheyali"
            TextToSpeech.LANG_NOT_SUPPORTED -> "supported kaadu"
            else -> "teliyadu"
        }
        return "Speech recognition: " + (if (SpeechRecognizer.isRecognitionAvailable(ctx)) "OK" else "ee phone lo ledu") +
            "\nTTS engine: " + (tts?.defaultEngine ?: "ledu") + " (ready: $ttsReady)" +
            "\nTelugu voice: " + avail("te-IN") +
            "\nEnglish voice: " + avail("en-IN") +
            "\nHindi voice: " + avail("hi-IN")
    }

    fun stopSpeaking() {
        tts?.stop()
    }

    fun release() {
        recognizer?.destroy()
        tts?.shutdown()
    }
}
