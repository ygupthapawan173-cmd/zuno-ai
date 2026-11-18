package com.zeno.ai

enum class OrbState { Idle, Listening, Thinking, Speaking }

data class ChatMessage(val id: Long, val role: String, val text: String) {
    val isUser: Boolean get() = role == "user"
}

data class AppSettings(
    val apiKey: String = "",
    val model: String = "gemini-2.5-flash",
    val lang: String = "te-IN",
    val speak: Boolean = true,
    val continuous: Boolean = false,
)
