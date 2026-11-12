package ru.gpstrips.data

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * Разбор GPX-треков. Возвращает точки и признак «времени можно верить»:
 * некоторые сервисы (например nakarte) пишут фиктивное время 1970-го года,
 * в этом случае длительность и скорость не показываются.
 */
object GpxParser {

    fun looksLikeGpx(text: String): Boolean =
        text.take(4000).contains("<gpx", ignoreCase = true)

    fun parse(text: String, fallbackDate: LocalDate): Pair<List<TrackPoint>, Boolean> {
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(StringReader(text))

        val points = ArrayList<TrackPoint>()
        try {
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name == "trkpt") {
                    readPoint(parser)?.let { points.add(it) }
                }
                event = parser.next()
            }
        } catch (e: Exception) {
            if (points.isEmpty()) return emptyList<TrackPoint>() to false
        }

        if (points.isEmpty()) return emptyList<TrackPoint>() to false

        val minValid = LocalDate.of(2001, 1, 1).toEpochDay() * 86_400_000L
        val reliable = points.all { it.timeMs > minValid }
        if (reliable) return points to true

        // фиктивное время: ставим точки по 1 секунде от начала суток файла, чтобы трек рисовался
        val base = fallbackDate.toEpochDay() * 86_400_000L
        val synthesized = points.mapIndexed { i, p -> p.copy(timeMs = base + i * 1000L) }
        return synthesized to false
    }

    private fun readPoint(parser: XmlPullParser): TrackPoint? {
        val lat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull()
        val lon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull()
        if (lat == null || lon == null) return null

        var timeMs: Long? = null
        var alt: Double? = null
        var speed: Double? = null
        val depth = parser.depth
        while (true) {
            val e = parser.next()
            if (e == XmlPullParser.END_DOCUMENT) break
            if (e == XmlPullParser.END_TAG && parser.depth <= depth) break
            if (e == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "time" -> timeMs = parseTime(parser.nextText())
                    "ele" -> alt = parser.nextText().toDoubleOrNull()
                    "speed" -> speed = parser.nextText().toDoubleOrNull()?.times(3.6)
                }
            }
        }
        return TrackPoint(timeMs ?: 0L, lat, lon, speed, alt)
    }

    private fun parseTime(s: String): Long? {
        val v = s.trim()
        if (v.isEmpty()) return null
        return try {
            Instant.parse(v).toEpochMilli()
        } catch (e: Exception) {
            try {
                OffsetDateTime.parse(v).toInstant().toEpochMilli()
            } catch (e2: Exception) {
                try {
                    LocalDateTime.parse(v).toInstant(ZoneOffset.UTC).toEpochMilli()
                } catch (e3: Exception) {
                    null
                }
            }
        }
    }
}
