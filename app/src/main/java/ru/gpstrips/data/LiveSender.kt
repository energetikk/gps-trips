package ru.gpstrips.data

import java.net.HttpURLConnection
import java.net.URL

/**
 * Отправка на сайт: текущей позиции и поездок.
 *
 * В настройках хранится базовый адрес сервера (например https://мой-сайт.ru),
 * а пути /api/position и /api/trips добавляются здесь. Если в поле вписали
 * полный адрес приёма координат, он тоже поймёт.
 */
object LiveSender {

    fun isValidEndpoint(url: String): Boolean =
        url.startsWith("https://") || url.startsWith("http://")

    fun baseUrl(url: String): String {
        var value = url.trim().trimEnd('/')
        for (suffix in listOf("/api/position", "/api/trips")) {
            if (value.endsWith(suffix)) value = value.removeSuffix(suffix).trimEnd('/')
        }
        return value
    }

    fun positionUrl(base: String): String = baseUrl(base) + "/api/position"

    fun tripsUrl(base: String): String = baseUrl(base) + "/api/trips"

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

    fun tripPayload(trip: Trip, device: String): String = buildString {
        append('{')
        append("\"id\":").append(json(trip.id))
        append(",\"source\":").append(json(trip.source))
        append(",\"device\":").append(json(device))
        append(",\"startMs\":").append(trip.startMs)
        append(",\"endMs\":").append(trip.endMs)
        append(",\"distanceM\":").append(trip.distanceM)
        append(",\"movingSec\":").append(trip.movingSec)
        append(",\"maxSpeedKmh\":").append(trip.maxSpeedKmh)
        append(",\"avgSpeedKmh\":").append(trip.avgSpeedKmh)
        append(",\"timeReliable\":").append(trip.timeReliable)
        append(",\"track\":[")
        trip.track.forEachIndexed { index, p ->
            if (index > 0) append(',')
            append('[').append(p.timeMs).append(',').append(p.lat).append(',')
                .append(p.lon).append(',')
            p.speedKmh?.let { append(it) } ?: append("null")
            append(']')
        }
        append("]}")
    }

    fun send(url: String, token: String, device: String, fix: LiveFix): String? {
        if (!isValidEndpoint(url)) return "Адрес должен начинаться с http:// или https://"
        return post(url, token, payload(fix, device))
    }

    fun uploadTrip(base: String, token: String, device: String, trip: Trip): String? {
        if (!isValidEndpoint(base)) return "Адрес должен начинаться с http:// или https://"
        return post(tripsUrl(base), token, tripPayload(trip, device))
    }

    private fun post(url: String, token: String, body: String): String? {
        val bytes = body.toByteArray(Charsets.UTF_8)
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("User-Agent", "GpsTrips/1.4")
            if (token.isNotBlank()) conn.setRequestProperty("X-Token", token)
            conn.outputStream.use { it.write(bytes) }

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
