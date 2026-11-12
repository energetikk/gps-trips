package ru.gpstrips

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.gpstrips.data.NmeaParser
import ru.gpstrips.data.TripBuilder
import java.time.LocalDate

/** Проверка на реальном файле demo.nmea — том же, что лежит в папке sample/. */
class DemoLogTest {

    @Test
    fun `демо-лог разбирается на две поездки`() {
        val text = requireNotNull(javaClass.classLoader?.getResourceAsStream("demo.nmea"))
            .bufferedReader()
            .readText()

        val points = NmeaParser.parse(text, LocalDate.of(2025, 11, 12))
        assertEquals(600, points.size)

        val trips = TripBuilder.build(points, "demo.nmea", true)
        assertEquals(2, trips.size)

        trips.forEach { trip ->
            assertTrue("дистанция ${trip.distanceM}", trip.distanceM > 1500.0)
            assertTrue("средняя ${trip.avgSpeedKmh}", trip.avgSpeedKmh in 15.0..65.0)
            assertEquals(299L, trip.durationSec)
            assertTrue(trip.track.size >= 100)
        }
    }
}
