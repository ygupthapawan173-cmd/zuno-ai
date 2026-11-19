package com.zeno.ai

import android.app.Application
import android.content.Context
import android.content.Intent
import android.provider.Settings
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
    var diag by mutableStateOf("")
        private set
    /** Bumped when the built-in mic path fails, so the UI can open Google's voice dialog instead. */
    var fallbackRequest by mutableStateOf(0)
        private set

    private var wakePending = false
    private var fromWake = false

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
            val wasWake = fromWake
            if (fromWake) {
                fromWake = false
                val said = if (code == SpeechRecognizer.ERROR_NO_MATCH || code == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
                    "Sorry, I didn't catch that." else "Sorry, the microphone had a problem."
                if (settings.speak) {
                    orb = OrbState.Speaking
                    voice.speak(said)
                }
            }
            when (code) {
                -1 -> error = "Ee phone lo speech recognition ledu"
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> Unit
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> error = "Mic permission ivvandi"
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> error = "Voice kosam internet kavali"
                12, 13 -> error = "Ee language ki speech recognition ledu (error $code). Google app lo Telugu voice typing download cheyandi, leda vere language try cheyandi."
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> error = "Mic busy undi (error 8). Konchem sepu aagi malli try cheyandi."
                SpeechRecognizer.ERROR_AUDIO -> error = "Mic access ledu (error 3). Vere app mic vaduthunnadi emo."
                SpeechRecognizer.ERROR_SERVER, 11 -> error = "Speech service error ($code). Google app update cheyandi, internet check cheyandi."
                else -> error = "Voice error ($code)"
            }
            if (!wasWake && code in FALLBACK_CODES) fallbackRequest++
        }
        voice.onTtsIssue = { error = it }
        voice.onSpeakDone = {
            orb = OrbState.Idle
            if (wakePending) {
                wakePending = false
                startListening()
            } else if (settings.continuous) {
                startListening()
            }
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

    /** Called by the "Hey Zeno" service: say "Yes?" so the user knows we heard, then listen. */
    fun onWake() {
        if (orb != OrbState.Idle) return
        error = null
        fromWake = true
        wakePending = true
        orb = OrbState.Speaking
        voice.speak("Yes?")
    }

    fun runDiagnostics() {
        val mic = androidx.core.content.ContextCompat.checkSelfPermission(app, android.Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        val key = if (settings.provider == "openrouter") settings.orKey else settings.apiKey
        diag = voice.diagnose() +
            "\nMic permission: " + (if (mic) "OK" else "ivvaledu") +
            "\nAPI key: " + (if (key.isBlank()) "ledu" else "set") +
            "\nHey Zeno: " + (if (settings.wake) "on" else "off")
        wakePending = false
        orb = OrbState.Speaking
        voice.speak("Voice test. Hello from Zeno.")
    }

    fun stop() {
        wakePending = false
        fromWake = false
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
        fromWake = false
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
            fail("Settings lo " + (if (useOr) "OpenRouter" else "Gemini") + " API key add cheyandi",
                "Please add an API key in Zeno settings.", viaVoice)
            return
        }

        if (settings.mode == "agent" && image == null) {
            runAgentTask(t, viaVoice)
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
                if (sb.isEmpty() && idx <= messages.lastIndex) messages.removeAt(idx)
                store.saveHistory(messages)
                fail(e.message ?: "Network error", "Sorry, I could not get an answer. Please check your internet or API key.", viaVoice)
                return@launch
            }
            if (sb.isEmpty()) {
                if (idx <= messages.lastIndex) messages.removeAt(idx)
                fail("AI nunchi reply raledu", "Sorry, I got no reply. Please try again.", viaVoice)
                return@launch
            }
            finish(sb.toString(), viaVoice)
        }
    }

    // ------------------------------------------------------------ phone control agent

    private suspend fun askOnce(system: String, user: String): String {
        val ctx = listOf(ChatMessage(0, "user", user))
        val flow = if (settings.provider == "openrouter") orClient.stream(settings.orKey, settings.orModel, system, ctx)
        else client.stream(settings.apiKey, settings.model, system, ctx)
        val sb = StringBuilder()
        flow.collect { sb.append(it) }
        return sb.toString()
    }

    fun openAccessibilitySettings() {
        try {
            app.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
        }
    }

    private fun runAgentTask(task: String, viaVoice: Boolean) {
        if (PhoneControlService.instance == null) {
            fail(
                "Phone control off undi. Accessibility settings lo Zeno AI ni on cheyandi.",
                "Please turn on Zeno phone control in accessibility settings.",
                viaVoice,
            )
            openAccessibilitySettings()
            return
        }
        messages += ChatMessage(seq++, "model", "Pani start chesthunnanu…")
        val idx = messages.lastIndex
        orb = OrbState.Thinking
        job = scope.launch {
            val log = StringBuilder()
            val agent = PhoneAgent { system, user -> askOnce(system, user) }
            val result = try {
                agent.run(task) { step ->
                    log.appendLine(step)
                    if (idx <= messages.lastIndex) messages[idx] = messages[idx].copy(text = log.toString().trim())
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "Network error"
                "Sorry, I could not finish. Please check your internet or API key."
            }
            val final = if (result == "NEEDS_SERVICE") "Phone control off undi" else result
            if (idx <= messages.lastIndex) {
                messages[idx] = messages[idx].copy(text = (log.toString() + "\n" + final).trim())
            }
            finish(final, viaVoice)
        }
    }

    private fun fail(message: String, spoken: String, viaVoice: Boolean) {
        error = message
        if (viaVoice && settings.speak) {
            orb = OrbState.Speaking
            voice.speak(spoken)
        } else {
            orb = OrbState.Idle
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
        private val FALLBACK_CODES = setOf(-1, 2, 3, 4, 5, 8, 11)
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
