package com.jurisgames.qr

import java.net.HttpURLConnection
import java.net.URI

/**
 * "Send feedback", the same as in every free Jurisgames app (github.com/kleiders3010/jurisgames-feedback): a problem,
 * an idea or something else, an optional email for an answer and, if the tick stays on, a few technical details
 * (app version, system, how many codes are in the history: never what they say). "See what's sent" shows exactly what
 * goes. The report is emailed to hello@jurisgames.com and not stored.
 */
object Feedback {
    const val LIVE = "https://feedback.jurisgames.com/send"
    /** Tests point it at a server on this computer; any other address is ignored (APP-RULES safety 1). */
    var endpoint = LIVE
    private fun target(): String {
        val host = runCatching { URI(endpoint).host }.getOrNull()
        return if (endpoint == LIVE || host == "127.0.0.1" || host == "localhost") endpoint else LIVE
    }

    val kinds = listOf("bug" to "Something isn't working", "idea" to "An idea", "other" to "Something else")

    private fun str(s: String) = buildString {
        append('"')
        for (c in s) when {
            c == '"' -> append("\\\"")
            c == '\\' -> append("\\\\")
            c == '\n' -> append("\\n")
            c == '\r' -> append("\\r")
            c == '\t' -> append("\\t")
            c < ' ' -> append("\\u%04x".format(c.code))
            else -> append(c)
        }
        append('"')
    }

    /** The report as JSON (the endpoint's format), pretty enough to read in "See what's sent". */
    fun json(kind: String, message: String, email: String, version: String, details: List<String>?): String = buildString {
        append("{\n  \"app\": \"qr\",\n  \"kind\": ${str(kind)},\n  \"message\": ${str(message.trim())},\n")
        append("  \"lang\": ${str(java.util.Locale.getDefault().toLanguageTag())},\n  \"version\": ${str(version)}")
        if (email.isNotBlank()) append(",\n  \"email\": ${str(email.trim())}")
        if (details != null) {
            append(",\n  \"details\": {\n    \"platform\": \"android\",\n")
            append("    \"system\": ${str("Android ${android.os.Build.VERSION.RELEASE} (${android.os.Build.MODEL})")},\n")
            append("    \"diagnostics\": [\n")
            append(details.joinToString(",\n") { "      " + str(it) })
            append("\n    ]\n  }")
        }
        append("\n}")
    }

    /** Sends it; returns null when it went, or a sentence to show. */
    fun send(body: String): String? = try {
        val c = URI(target()).toURL().openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.doOutput = true
        c.connectTimeout = 15_000
        c.readTimeout = 20_000
        c.setRequestProperty("Content-Type", "application/json")
        c.outputStream.use { it.write(body.toByteArray()) }
        val code = c.responseCode
        val answer = (if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.readText() ?: ""
        when {
            code == 200 && answer.contains("\"ok\":true") -> null
            code == 429 -> tr("Too many reports from this network today. Please try again tomorrow, or write to hello@jurisgames.com.")
            answer.contains("\"email\"") -> tr("That email doesn't look right.")
            else -> tr("It couldn't be sent. Check your connection and try again, or write to hello@jurisgames.com.")
        }
    } catch (e: Exception) {
        tr("It couldn't be sent. Check your connection and try again, or write to hello@jurisgames.com.")
    }
}
