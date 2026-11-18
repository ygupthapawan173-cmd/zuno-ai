package com.zeno.ai

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Streams replies from OpenRouter (OpenAI-compatible chat completions API). */
class OpenRouterClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    fun stream(
        apiKey: String,
        model: String,
        system: String,
        history: List<ChatMessage>,
    ): Flow<String> = callbackFlow {
        val messages = JSONArray()
        messages.put(JSONObject().put("role", "system").put("content", system))
        history.forEach { m ->
            messages.put(
                JSONObject().put("role", if (m.isUser) "user" else "assistant").put("content", m.text)
            )
        }
        val body = JSONObject()
            .put("model", model)
            .put("stream", true)
            .put("messages", messages)

        val request = Request.Builder()
            .url("https://openrouter.ai/api/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("HTTP-Referer", "https://github.com/ygupthapawan173-cmd/zuno-ai")
            .header("X-Title", "Zeno AI")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val call = http.newCall(request)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                close(e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { r ->
                    if (!r.isSuccessful) {
                        val msg = r.body?.string().orEmpty().take(300)
                        close(IOException("OpenRouter HTTP ${r.code}: $msg"))
                        return
                    }
                    try {
                        val source = r.body!!.source()
                        while (!source.exhausted()) {
                            val line = source.readUtf8Line() ?: break
                            if (!line.startsWith("data:")) continue // skips ": OPENROUTER PROCESSING" comments
                            val data = line.removePrefix("data:").trim()
                            if (data == "[DONE]") break
                            val text = extractText(data)
                            if (text.isNotEmpty()) trySend(text)
                        }
                        close()
                    } catch (e: Exception) {
                        close(e)
                    }
                }
            }
        })
        awaitClose { call.cancel() }
    }

    private fun extractText(json: String): String = try {
        JSONObject(json).optJSONArray("choices")?.optJSONObject(0)
            ?.optJSONObject("delta")?.optString("content", "").orEmpty()
    } catch (e: Exception) {
        ""
    }
}
