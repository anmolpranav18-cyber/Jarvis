package com.jarvis.app

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Sends open-ended questions to Groq's free API (no credit card
 * required) for a real AI response, used when no local canned
 * command matches what you said.
 */
object AiClient {
    fun ask(apiKey: String, prompt: String): String {
        val url = URL("https://api.groq.com/openai/v1/chat/completions")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Authorization", "Bearer $apiKey")
        conn.setRequestProperty("content-type", "application/json")
        conn.doOutput = true
        conn.connectTimeout = 15000
        conn.readTimeout = 20000

        val messages = JSONArray().put(
            JSONObject().apply {
                put("role", "user")
                put(
                    "content",
                    "Answer briefly, in one or two short sentences suitable for being spoken aloud by a voice assistant: $prompt"
                )
            }
        )
        val body = JSONObject().apply {
            put("model", "openai/gpt-oss-20b")
            put("max_tokens", 200)
            put("messages", messages)
        }

        conn.outputStream.use { it.write(body.toString().toByteArray()) }

        val responseCode = conn.responseCode
        val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
        val text = stream.bufferedReader().use { it.readText() }

        if (responseCode !in 200..299) {
            return "AI service error, code $responseCode."
        }

        val json = JSONObject(text)
        val choices = json.getJSONArray("choices")
        if (choices.length() == 0) return "I didn't get a clear answer from the AI service."
        val message = choices.getJSONObject(0).getJSONObject("message")
        return message.optString("content").ifBlank { "I didn't get a clear answer from the AI service." }
    }
}

