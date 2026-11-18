package com.zeno.ai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Settings in SharedPreferences, chat history in a small JSON file. */
class Store(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("zeno", Context.MODE_PRIVATE)
    private val historyFile = File(ctx.filesDir, "history.json")

    fun loadSettings(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            apiKey = prefs.getString("apiKey", d.apiKey) ?: d.apiKey,
            model = prefs.getString("model", d.model) ?: d.model,
            lang = prefs.getString("lang", d.lang) ?: d.lang,
            speak = prefs.getBoolean("speak", d.speak),
            continuous = prefs.getBoolean("continuous", d.continuous),
        )
    }

    fun saveSettings(s: AppSettings) {
        prefs.edit()
            .putString("apiKey", s.apiKey)
            .putString("model", s.model)
            .putString("lang", s.lang)
            .putBoolean("speak", s.speak)
            .putBoolean("continuous", s.continuous)
            .apply()
    }

    fun loadHistory(): List<ChatMessage> = try {
        if (!historyFile.exists()) emptyList() else {
            val arr = JSONArray(historyFile.readText())
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                ChatMessage(o.getLong("id"), o.getString("role"), o.getString("text"))
            }
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun saveHistory(list: List<ChatMessage>) {
        try {
            val arr = JSONArray()
            list.takeLast(200).forEach {
                arr.put(JSONObject().put("id", it.id).put("role", it.role).put("text", it.text))
            }
            historyFile.writeText(arr.toString())
        } catch (_: Exception) {
        }
    }
}
