package com.zeno.ai

import android.app.Application
import android.speech.SpeechRecognizer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class ZenoViewModel(app: Application) : AndroidViewModel(app) {

    private val store = Store(app)
    private val client = GeminiClient()
    private val voice = VoiceController(app)
    private val commands = CommandHandler(app)

    var settings by mutableStateOf(store.loadSettings())
        private set
    val messages = mutableStateListOf<ChatMessage>().also { it.addAll(store.loadHistory()) }
    var orb by mutableStateOf(OrbState.Idle)
        private set
    var partial by mutableStateOf("")
        private set
    var level by mutableFloatStateOf(0f)
        private set
    var error by mutableStateOf<String?>(null)

    private var seq: Long = (messages.maxOfOrNull { it.id } ?: 0L) + 1
    private var job: Job? = null

    init {
        voice.onPartial = { partial = it }
        voice.onLevel = { level = it }
        voice.onFinal = {
            partial = ""
            send(it, viaVoice = true)
        }
        voice.onError = { code ->
            partial = ""
            orb = OrbState.Idle
            when (code) {
                -1 -> error = "Ee phone lo speech recognition ledu"
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> Unit
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> error = "Mic permission ivvandi"
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> error = "Voice kosam internet kavali"
                else -> error = "Voice error ($code)"
            }
        }
        voice.onSpeakDone = {
            orb = OrbState.Idle
            if (settings.continuous) startListening()
        }
    }

    fun startListening() {
        error = null
        job?.cancel()
        partial = ""
        orb = OrbState.Listening
        voice.listen(settings.lang)
    }

    fun stop() {
        job?.cancel()
        voice.cancelListening()
        voice.stopSpeaking()
        partial = ""
        orb = OrbState.Idle
        val last = messages.lastOrNull()
        if (last != null && !last.isUser && last.text.isEmpty()) messages.removeAt(messages.lastIndex)
        store.saveHistory(messages)
    }

    fun send(text: String, viaVoice: Boolean = false) {
        val t = text.trim()
        if (t.isEmpty()) return
        job?.cancel()
        voice.stopSpeaking()
        error = null
        messages += ChatMessage(seq++, "user", t)

        val local = commands.handle(t)
        if (local != null) {
            messages += ChatMessage(seq++, "model", local)
            finish(local, viaVoice)
            return
        }
        if (settings.apiKey.isBlank()) {
            error = "Settings lo Gemini API key add cheyandi"
            orb = OrbState.Idle
            return
        }

        val context = messages.toList().takeLast(20)
        messages += ChatMessage(seq++, "model", "")
        val idx = messages.lastIndex
        orb = OrbState.Thinking
        job = viewModelScope.launch {
            val sb = StringBuilder()
            try {
                client.stream(settings.apiKey, settings.model, SYSTEM_PROMPT, context).collect { chunk ->
                    sb.append(chunk)
                    messages[idx] = messages[idx].copy(text = sb.toString())
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "Network error"
                if (sb.isEmpty() && idx <= messages.lastIndex) messages.removeAt(idx)
                orb = OrbState.Idle
                store.saveHistory(messages)
                return@launch
            }
            if (sb.isEmpty()) {
                messages.removeAt(idx)
                error = "AI nunchi reply raledu"
                orb = OrbState.Idle
                return@launch
            }
            finish(sb.toString(), viaVoice)
        }
    }

    private fun finish(reply: String, viaVoice: Boolean) {
        store.saveHistory(messages)
        if (viaVoice && settings.speak) {
            orb = OrbState.Speaking
            voice.speak(reply)
        } else {
            orb = OrbState.Idle
        }
    }

    fun updateSettings(s: AppSettings) {
        settings = s
        store.saveSettings(s)
    }

    fun clearHistory() {
        stop()
        messages.clear()
        store.saveHistory(messages)
    }

    override fun onCleared() {
        voice.release()
    }

    private companion object {
        const val SYSTEM_PROMPT =
            "You are Zeno, a friendly, sharp, voice-first AI assistant. " +
                "Keep answers short and natural for speaking (2-4 sentences) unless the user asks for detail. " +
                "Reply in the language the user used: Telugu script, English, or Tanglish (Telugu in English letters). " +
                "Do not use markdown formatting except code blocks when the user asks for code."
    }
}
