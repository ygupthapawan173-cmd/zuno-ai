package com.zeno.ai

import kotlinx.coroutines.delay
import org.json.JSONObject

/**
 * Looks at the screen, asks the AI for the next action, does it, and repeats until the task is done.
 * [ask] sends one system + user prompt to the chosen AI provider and returns the full reply.
 */
class PhoneAgent(private val ask: suspend (system: String, user: String) -> String) {

    suspend fun run(task: String, onStep: (String) -> Unit): String {
        val svc = PhoneControlService.instance ?: return "NEEDS_SERVICE"
        val history = mutableListOf<String>()
        var parseFailures = 0
        for (step in 1..MAX_STEPS) {
            val user = "Task: $task\nStep $step of $MAX_STEPS\nCurrent app: ${svc.currentPackage()}\n" +
                "Actions so far:\n" + (history.takeLast(8).joinToString("\n").ifEmpty { "(none)" }) +
                "\n\nScreen elements:\n" + svc.snapshot()
            val reply = ask(SYSTEM, user)
            val json = parse(reply)
            if (json == null) {
                if (++parseFailures >= 2) return "AI nunchi sarigga action raaledu. Malli try cheyandi, leda Gemini provider vaadandi."
                history += "$step. (invalid reply, try again with JSON only)"
                continue
            }
            val action = json.optString("action")
            if (action == "done") return json.optString("say", "Pani ayipoyindi")
            val result = svc.perform(json)
            history += "$step. ${json} -> $result"
            onStep("$step. " + describe(json) + " → " + result)
            delay(1300)
        }
        return "Chala steps ayyayi, aapesanu. Migilina pani meere chudandi."
    }

    private fun describe(a: JSONObject): String = when (a.optString("action")) {
        "open_app" -> "open " + a.optString("name")
        "click" -> "click #" + a.optInt("id")
        "type" -> "type \"" + a.optString("text") + "\""
        else -> a.optString("action")
    }

    private fun parse(reply: String): JSONObject? {
        val start = reply.indexOf('{')
        val end = reply.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return try {
            JSONObject(reply.substring(start, end + 1))
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        const val MAX_STEPS = 14
        private const val SYSTEM =
            "You control an Android phone through an accessibility API. Each turn you get the user's task, the current app " +
                "and a numbered list of on-screen elements. Reply with ONE JSON object only, no other text. Actions: " +
                "{\"action\":\"open_app\",\"name\":\"whatsapp\"} " +
                "{\"action\":\"click\",\"id\":N} " +
                "{\"action\":\"type\",\"id\":N,\"text\":\"...\"} " +
                "{\"action\":\"scroll\",\"direction\":\"down\"} (or \"up\") " +
                "{\"action\":\"enter\"} " +
                "{\"action\":\"back\"} {\"action\":\"home\"} {\"action\":\"wait\"} " +
                "{\"action\":\"done\",\"say\":\"short reply to the user in the user's language\"}. " +
                "Rules: if the needed app is not open, start with open_app. Use element ids exactly as listed. " +
                "After typing in a search box, use enter or click the search button. Do one action per turn. " +
                "Never make payments, transfers or purchases and never delete things; if the task needs that, finish with done " +
                "and tell the user to do that step themselves. When the task is complete or impossible, use done."
    }
}
