package com.jarvis.app

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Free, no-signup lookup against Wikipedia's public summary API, used
 * as a last-resort answer for general "what is / who is" questions
 * that don't match any built-in command.
 */
object WikipediaClient {
    fun lookup(query: String): String? {
        return try {
            val title = URLEncoder.encode(query.trim(), "UTF-8")
            val url = URL("https://en.wikipedia.org/api/rest_v1/page/summary/$title")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "JarvisApp/1.0")
            conn.connectTimeout = 10000
            conn.readTimeout = 15000

            if (conn.responseCode != 200) return null

            val text = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(text)
            val extract = json.optString("extract", "")
            if (extract.isBlank()) return null

            val firstSentence = extract.split(". ").firstOrNull()?.trim() ?: extract
            if (firstSentence.endsWith(".")) firstSentence else "$firstSentence."
        } catch (e: Exception) {
            null
        }
    }
}

