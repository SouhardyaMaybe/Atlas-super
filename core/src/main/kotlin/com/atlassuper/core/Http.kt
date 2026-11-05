package com.atlassuper.core

import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/** Minimal HTTP GET client. Works on both JVM (tests) and Android (the consuming app). */
object Http {
    private const val TIMEOUT_MS = 30_000

    /** Fetch a URL as a UTF-8 string. Throws [IllegalStateException] on non-2xx or transport failure. */
    fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = TIMEOUT_MS
        conn.readTimeout = TIMEOUT_MS
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty("User-Agent", "Atlas-super/0.1.0")

        val code = conn.responseCode
        val body = readFully(conn.inputStream)
        conn.disconnect()

        if (code !in 200..299) {
            throw IllegalStateException("HTTP $code for $url: $body")
        }
        return body
    }

    private fun readFully(stream: InputStream?): String {
        if (stream == null) return ""
        return String(stream.readAllBytes(), StandardCharsets.UTF_8)
    }
}
