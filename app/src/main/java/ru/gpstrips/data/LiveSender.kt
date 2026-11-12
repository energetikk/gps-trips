package ru.gpstrips.data

import java.net.HttpURLConnection
import java.net.URL

/**
 * Отправка одной позиции на сайт по HTTP POST с JSON-телом.
 * Возвращает null при успехе или текст ошибки.
 */
object LiveSender {

    fun isValidEndpoint(url: String): Boolean =
        url.startsWith("https://") || url.startsWith("http://")

    fun payload(fix: LiveFix, device: String): String = buildString {
        append('{')
        append("\"lat\":").append(fix.lat)
        append(",\"lon\":").append(fix.lon)
        fix.speedKmh?.let { append(",\"speed_kmh\":").append(it) }
        append(",\"ts\":").append(fix.timeMs)
        append(",\"device\":").append(json(device))
        append(",\"source\":").append(json(fix.source))
        append('}')
    }

    fun send(url: String, token: String, device: String, fix: LiveFix): String? {
        if (!isValidEndpoint(url)) return "Адрес должен начинаться с http:// или https://"

        val body = payload(fix, device).toByteArray(Charsets.UTF_8)
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("User-Agent", "GpsTrips/1.1")
            if (token.isNotBlank()) conn.setRequestProperty("X-Token", token)
            conn.outputStream.use { it.write(body) }

            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()
            conn.disconnect()

            if (code in 200..299) null else "HTTP $code: ${text.take(200)}"
        } catch (e: Exception) {
            e.message ?: e.javaClass.simpleName
        }
    }

    private fun json(value: String): String = buildString {
        append('"')
        for (c in value) {
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (c < ' ') append(String.format("\\u%04x", c.code)) else append(c)
            }
        }
        append('"')
    }
}
