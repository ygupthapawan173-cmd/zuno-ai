package com.zeno.ai

enum class OrbState { Idle, Listening, Thinking, Speaking }

/** [image] is a base64 JPEG kept in memory only (not saved to history). */
data class ChatMessage(val id: Long, val role: String, val text: String, val image: String? = null) {
    val isUser: Boolean get() = role == "user"
}

data class AppSettings(
    val provider: String = "openrouter",
    val orKey: String = "",
    val orModel: String = "openrouter/free",
    val apiKey: String = "",
    val model: String = "gemini-2.5-flash",
    val lang: String = "te-IN",
    val speak: Boolean = true,
    val continuous: Boolean = false,
    val mode: String = "assistant", // "assistant" or "translator"
    val wake: Boolean = false,
    val city: String = "",
    val theme: Int = 0,
    val picoKey: String = "",
)
