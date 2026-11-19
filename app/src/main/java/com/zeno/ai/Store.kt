package com.zeno.ai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Settings in SharedPreferences, chat history and memory in small JSON files. */
class Store(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("zeno", Context.MODE_PRIVATE)
    private val historyFile = File(ctx.filesDir, "history.json")
    private val memoryFile = File(ctx.filesDir, "memory.json")

    fun loadSettings(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            provider = prefs.getString("provider", d.provider) ?: d.provider,
            orKey = prefs.getString("orKey", null)?.takeIf { it.isNotBlank() } ?: BuildConfig.DEFAULT_OR_KEY,
            orModel = prefs.getString("orModel", d.orModel) ?: d.orModel,
            apiKey = prefs.getString("apiKey", null)?.takeIf { it.isNotBlank() } ?: BuildConfig.DEFAULT_API_KEY,
            model = prefs.getString("model", d.model) ?: d.model,
            lang = prefs.getString("lang", d.lang) ?: d.lang,
            speak = prefs.getBoolean("speak", d.speak),
            continuous = prefs.getBoolean("continuous", d.continuous),
            mode = prefs.getString("mode", d.mode) ?: d.mode,
            wake = prefs.getBoolean("wake", d.wake),
            city = prefs.getString("city", d.city) ?: d.city,
            theme = prefs.getInt("theme", d.theme),
        )
    }

    fun saveSettings(s: AppSettings) {
        prefs.edit()
            .putString("provider", s.provider)
            .putString("orKey", s.orKey)
            .putString("orModel", s.orModel)
            .putString("apiKey", s.apiKey)
            .putString("model", s.model)
            .putString("lang", s.lang)
            .putBoolean("speak", s.speak)
            .putBoolean("continuous", s.continuous)
            .putString("mode", s.mode)
            .putBoolean("wake", s.wake)
            .putString("city", s.city)
            .putInt("theme", s.theme)
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

    fun loadMemory(): List<String> = try {
        if (!memoryFile.exists()) emptyList() else {
            val arr = JSONArray(memoryFile.readText())
            (0 until arr.length()).map { arr.getString(it) }
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun saveMemory(list: List<String>) {
        try {
            val arr = JSONArray()
            list.forEach { arr.put(it) }
            memoryFile.writeText(arr.toString())
        } catch (_: Exception) {
        }
    }
}
