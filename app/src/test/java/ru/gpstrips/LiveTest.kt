package ru.gpstrips

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.gpstrips.data.GpxTail
import ru.gpstrips.data.LiveFix
import ru.gpstrips.data.LiveSender
import java.time.Instant

class LiveTest {

    @Test
    fun endpointValidation() {
        assertTrue(LiveSender.isValidEndpoint("https://example.ru/api/position"))
        assertTrue(LiveSender.isValidEndpoint("http://192.168.1.10:8080/api/position"))
        assertFalse(LiveSender.isValidEndpoint("example.ru/api/position"))
        assertFalse(LiveSender.isValidEndpoint(""))
    }

    @Test
    fun payloadContainsCoordinatesAndDevice() {
        val fix = LiveFix(
            lat = 56.326123,
            lon = 44.002456,
            speedKmh = 42.5,
            timeMs = 1_700_000_000_000L,
            source = "log \"today\".nmea"
        )
        val json = LiveSender.payload(fix, "car")
        assertTrue(json.contains("\"lat\":56.326123"))
        assertTrue(json.contains("\"lon\":44.002456"))
        assertTrue(json.contains("\"speed_kmh\":42.5"))
        assertTrue(json.contains("\"device\":\"car\""))
        assertTrue(json.contains("log \\\"today\\\".nmea"))
    }

    @Test
    fun payloadOmitsMissingSpeed() {
        val fix = LiveFix(56.0, 44.0, null, 1_700_000_000_000L, "a.gpx")
        assertFalse(LiveSender.payload(fix, "car").contains("speed_kmh"))
    }

    @Test
    fun gpxTailTakesLastPoint() {
        val gpx = """
            <gpx><trk><trkseg>
            <trkpt lat="56.326" lon="44.002"><ele>100</ele><time>2026-11-13T08:00:00Z</time><speed>10</speed></trkpt>
            <trkpt lat="56.327" lon="44.003"><ele>101</ele><time>2026-11-13T08:00:10Z</time><speed>12.5</speed></trkpt>
            </trkseg></trk></gpx>
        """.trimIndent()

        val fix = GpxTail.lastFix(gpx, "test.gpx")!!
        assertEquals(56.327, fix.lat, 1e-9)
        assertEquals(44.003, fix.lon, 1e-9)
        assertEquals(45.0, fix.speedKmh!!, 1e-6)
        assertEquals(Instant.parse("2026-11-13T08:00:10Z").toEpochMilli(), fix.timeMs)
    }

    @Test
    fun gpxTailIgnoresTruncatedTag() {
        val gpx = """<trkpt lat="56.1" lon="44.1"><time>2026-11-13T08:00:00Z</time></trkpt>
            <trkpt lat="56.2" lon="44.2"><time>2026-11-13T08:00:05Z</time></trkpt>
            <trkpt lat="56.3" lon="44.3"
        """.trimIndent()
        val fix = GpxTail.lastFix(gpx, "test.gpx")!!
        assertEquals(56.2, fix.lat, 1e-9)
        assertEquals(44.2, fix.lon, 1e-9)
    }
}
