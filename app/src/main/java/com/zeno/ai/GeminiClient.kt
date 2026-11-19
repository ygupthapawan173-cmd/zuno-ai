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

/** Streams Gemini replies token-by-token over server-sent events. */
class GeminiClient {
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
        val contents = JSONArray()
        history.forEachIndexed { i, m ->
            val parts = JSONArray().put(JSONObject().put("text", m.text.ifEmpty { "Describe this image." }))
            // only the newest message carries its image, to keep requests small
            if (m.image != null && i == history.lastIndex) {
                parts.put(
                    JSONObject().put(
                        "inline_data",
                        JSONObject().put("mime_type", "image/jpeg").put("data", m.image)
                    )
                )
            }
            contents.put(JSONObject().put("role", if (m.isUser) "user" else "model").put("parts", parts))
        }
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("contents", contents)

        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$model:streamGenerateContent?alt=sse")
            .header("x-goog-api-key", apiKey)
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
                        close(IOException("HTTP ${r.code}: $msg"))
                        return
                    }
                    try {
                        val source = r.body!!.source()
                        while (!source.exhausted()) {
                            val line = source.readUtf8Line() ?: break
                            if (!line.startsWith("data:")) continue
                            val text = extractText(line.removePrefix("data:").trim())
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
        val parts = JSONObject(json)
            .optJSONArray("candidates")?.optJSONObject(0)
            ?.optJSONObject("content")?.optJSONArray("parts")
        val sb = StringBuilder()
        if (parts != null) {
            for (i in 0 until parts.length()) sb.append(parts.getJSONObject(i).optString("text", ""))
        }
        sb.toString()
    } catch (e: Exception) {
        ""
    }
}
