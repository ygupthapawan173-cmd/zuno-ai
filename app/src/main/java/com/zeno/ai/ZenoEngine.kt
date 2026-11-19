package com.zeno.ai

import android.app.Application
import android.content.Context
import android.speech.SpeechRecognizer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/**
 * The brain of Zeno. A singleton so the UI and the background "Hey Zeno" service share one state.
 */
class ZenoEngine private constructor(private val app: Application) {

    private val store = Store(app)
    private val client = GeminiClient()
    private val orClient = OpenRouterClient()
    private val voice = VoiceController(app)
    private val commands = CommandHandler(app)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    var settings by mutableStateOf(store.loadSettings())
        private set
    val messages = mutableStateListOf<ChatMessage>().also { it.addAll(store.loadHistory()) }
    val memory = mutableStateListOf<String>().also { it.addAll(store.loadMemory()) }
    var orb by mutableStateOf(OrbState.Idle)
        private set
    var partial by mutableStateOf("")
        private set
    var level by mutableFloatStateOf(0f)
        private set
    var error by mutableStateOf<String?>(null)
    var openTab by mutableStateOf<Int?>(null)

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

    // ------------------------------------------------------------ voice

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

    // ------------------------------------------------------------ chat

    fun send(text: String, viaVoice: Boolean = false, image: String? = null) {
        val t = text.trim()
        if (t.isEmpty() && image == null) return
        job?.cancel()
        voice.stopSpeaking()
        error = null
        messages += ChatMessage(seq++, "user", t, image)

        val translator = settings.mode == "translator"
        if (image == null && !translator) {
            val local = memoryCommand(t) ?: commands.handle(t)
            if (local != null) {
                messages += ChatMessage(seq++, "model", local)
                finish(local, viaVoice)
                return
            }
        }

        val useOr = settings.provider == "openrouter"
        if ((if (useOr) settings.orKey else settings.apiKey).isBlank()) {
            error = "Settings lo " + (if (useOr) "OpenRouter" else "Gemini") + " API key add cheyandi"
            orb = OrbState.Idle
            return
        }

        val briefing = image == null && !translator && BRIEFING.containsMatchIn(t.lowercase())
        val history = if (translator) messages.toList().takeLast(1) else messages.toList().takeLast(20)
        val system = systemPrompt()
        messages += ChatMessage(seq++, "model", "")
        val idx = messages.lastIndex
        orb = OrbState.Thinking

        job = scope.launch {
            val sb = StringBuilder()
            try {
                var ctx = history
                if (briefing && ctx.isNotEmpty()) {
                    val prompt = buildBriefingPrompt()
                    ctx = ctx.dropLast(1) + ctx.last().copy(text = prompt)
                }
                val flow = if (useOr) orClient.stream(settings.orKey, settings.orModel, system, ctx)
                else client.stream(settings.apiKey, settings.model, system, ctx)
                flow.collect { chunk ->
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
                if (idx <= messages.lastIndex) messages.removeAt(idx)
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

    // ------------------------------------------------------------ memory

    private fun memoryCommand(t: String): String? {
        val remember = Regex("^(?:remember|gurthupettuko|గుర్తుపెట్టుకో)\\s+(?:that\\s+)?(.+)$", RegexOption.IGNORE_CASE).find(t)
        if (remember != null) {
            val fact = remember.groupValues[1].trim()
            memory += fact
            store.saveMemory(memory)
            return "Gurthupettukunnanu: $fact"
        }
        val l = t.lowercase().trim('.', '!', '?', ' ')
        if (l == "forget everything" || l == "forget all" || l == "anni marchipo") {
            memory.clear()
            store.saveMemory(memory)
            return "Anni marchipoyanu"
        }
        if (l.contains("what do you remember") || l.contains("naa gurinchi em telusu")) {
            return if (memory.isEmpty()) "Inka emi gurthupettukoledu. \"remember that …\" ani cheppandi."
            else "Naku telusu: " + memory.joinToString("; ")
        }
        return null
    }

    fun forget(index: Int) {
        if (index in memory.indices) {
            memory.removeAt(index)
            store.saveMemory(memory)
        }
    }

    // ------------------------------------------------------------ prompts

    private fun systemPrompt(): String {
        if (settings.mode == "translator") {
            return "You are a live interpreter. If the user's message is Telugu, translate it into natural English. " +
                "If it is English or Tanglish (Telugu in English letters), translate it into natural Telugu script. " +
                "Output ONLY the translation, nothing else."
        }
        val facts = if (memory.isEmpty()) "" else " Facts the user asked you to remember: " + memory.joinToString("; ") + "."
        return BASE_PROMPT + facts
    }

    private suspend fun buildBriefingPrompt(): String {
        val now = Date()
        val date = DateFormat.getDateInstance(DateFormat.FULL).format(now)
        val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(now)
        val weather = if (settings.city.isBlank()) null
        else withContext(Dispatchers.IO) { WeatherClient.fetch(settings.city) }
        val weatherPart = if (weather != null) "Weather: $weather. "
        else "Weather is unavailable (the user has not set a city in Settings, or no internet). "
        val facts = if (memory.isEmpty()) "" else "Things the user asked you to remember: " + memory.joinToString("; ") + ". "
        return "Give a short, warm good-morning briefing for the user. Today is $date, the time is $time. " +
            weatherPart + facts +
            "Mention the weather with one practical tip, and any remembered facts that matter today. " +
            "Do not invent news, appointments or numbers."
    }

    // ------------------------------------------------------------ settings

    fun updateSettings(s: AppSettings) {
        settings = s
        store.saveSettings(s)
    }

    fun exportChat(): String = messages.joinToString("\n\n") { (if (it.isUser) "You: " else "Zeno: ") + it.text }

    fun clearHistory() {
        stop()
        messages.clear()
        store.saveHistory(messages)
    }

    companion object {
        private val BRIEFING = Regex("good morning|briefing|daily brief|సుప్రభాతం|శుభోదయం")
        private const val BASE_PROMPT =
            "You are Zeno, a friendly, sharp, voice-first AI assistant. " +
                "Keep answers short and natural for speaking (2-4 sentences) unless the user asks for detail. " +
                "Reply in the language the user used: Telugu script, English, or Tanglish (Telugu in English letters). " +
                "Do not use markdown formatting except code blocks when the user asks for code."

        @Volatile
        private var instance: ZenoEngine? = null

        fun get(context: Context): ZenoEngine =
            instance ?: synchronized(this) {
                instance ?: ZenoEngine(context.applicationContext as Application).also { instance = it }
            }
    }
}
