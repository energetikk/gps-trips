package ru.gpstrips

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.gpstrips.data.GpxParser
import ru.gpstrips.data.TripBuilder
import java.time.LocalDate

class GpxTest {

    private val head = """<?xml version="1.0" encoding="UTF-8"?>
<gpx version="1.1" creator="test" xmlns="http://www.topografix.com/GPX/1/1">
<trk><name>t</name><trkseg>
"""

    private val tail = """
</trkseg></trk>
</gpx>
"""

    @Test
    fun `фиктивное время 1970 помечается как недостоверное`() {
        val gpx = head + """
<trkpt lat="56.387985" lon="43.734364"><time>1970-01-01T00:00:01.000Z</time></trkpt>
<trkpt lat="56.387810" lon="43.734259"><ele>100.5</ele></trkpt>
<trkpt lat="56.387435" lon="43.733585"><time>1970-01-01T00:00:01.000Z</time></trkpt>
""" + tail

        val (points, reliable) = GpxParser.parse(gpx, LocalDate.of(2025, 3, 27))
        assertEquals(3, points.size)
        assertFalse(reliable)
        assertEquals(56.387985, points[0].lat, 1e-6)
        assertEquals(43.734364, points[0].lon, 1e-6)
        assertEquals(100.5, points[1].altM!!, 1e-6)
    }

    @Test
    fun `нормальные метки времени дают настоящую поездку`() {
        val gpx = head + """
<trkpt lat="56.000000" lon="43.000000"><time>2025-03-27T08:00:00Z</time></trkpt>
<trkpt lat="56.000900" lon="43.000000"><time>2025-03-27T08:00:10Z</time></trkpt>
<trkpt lat="56.001800" lon="43.000000"><time>2025-03-27T08:00:20Z</time></trkpt>
<trkpt lat="56.002700" lon="43.000000"><time>2025-03-27T08:00:30Z</time></trkpt>
""" + tail

        val (points, reliable) = GpxParser.parse(gpx, LocalDate.of(2025, 3, 27))
        assertTrue(reliable)
        assertEquals(4, points.size)

        val trips = TripBuilder.build(points, "test.gpx", reliable)
        assertEquals(1, trips.size)
        assertEquals(30L, trips.first().durationSec)
        assertTrue(trips.first().distanceM > 280.0)
    }
}
