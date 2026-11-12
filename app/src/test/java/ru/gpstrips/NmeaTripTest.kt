package ru.gpstrips

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.gpstrips.data.NmeaParser
import ru.gpstrips.data.TripBuilder
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs

class NmeaTripTest {

    private fun nmeaCoord(value: Double, degDigits: Int): String {
        val abs = abs(value)
        val deg = abs.toInt()
        val min = (abs - deg) * 60.0
        return String.format(Locale.US, "%0${degDigits}d%07.4f", deg, min)
    }

    /** РЎС‚СЂРѕРєР° $GPRMC РЅР° Р·Р°РґР°РЅРЅСѓСЋ СЃРµРєСѓРЅРґСѓ СЃСѓС‚РѕРє, СЃРєРѕСЂРѕСЃС‚СЊ РІ СѓР·Р»Р°С…. */
    private fun rmc(secOfDay: Int, lat: Double, lon: Double, knots: Double, day: Int): String {
        val h = secOfDay / 3600
        val m = (secOfDay % 3600) / 60
        val s = secOfDay % 60
        val time = String.format(Locale.US, "%02d%02d%02d.00", h, m, s)
        val date = String.format(Locale.US, "%02d%02d25", day, 6)
        return "\$GPRMC,$time,A,${nmeaCoord(lat, 2)},N,${nmeaCoord(lon, 3)},E," +
            String.format(Locale.US, "%.2f", knots) + ",0.0,$date,,,A"
    }

    private fun log(points: List<Triple<Int, Double, Double>>, knots: Double = 19.44): String =
        points.joinToString("\n") { rmc(it.first, it.second, it.third, knots, 6) }

    @Test
    fun `СЃС‡РёС‚Р°РµС‚ РѕРґРЅСѓ РїРѕРµР·РґРєСѓ СЃ СЂР°СЃСЃС‚РѕСЏРЅРёРµРј Рё РґР»РёС‚РµР»СЊРЅРѕСЃС‚СЊСЋ`() {
        // 60 С‚РѕС‡РµРє РїРѕ 1 СЃ, ~10 Рј/СЃ РЅР° СЃРµРІРµСЂ
        val stepDeg = 10.0 / 111_320.0
        val points = (0 until 60).map { i ->
            Triple(34_000 + i, 56.0 + i * stepDeg, 43.0)
        }
        val parsed = NmeaParser.parse(log(points), LocalDate.of(2025, 6, 6))
        assertEquals(60, parsed.size)

        val trips = TripBuilder.build(parsed, "test.nmea", true)
        assertEquals(1, trips.size)

        val trip = trips.first()
        assertEquals(59L, trip.durationSec)
        assertTrue("РґРёСЃС‚Р°РЅС†РёСЏ ${trip.distanceM}", abs(trip.distanceM - 590.0) < 25.0)
        assertTrue("СЃСЂРµРґРЅСЏСЏ ${trip.avgSpeedKmh}", abs(trip.avgSpeedKmh - 36.0) < 3.0)
        assertTrue("РІ РґРІРёР¶РµРЅРёРё ${trip.movingSec}", trip.movingSec >= 58)
    }

    @Test
    fun `РїР°СѓР·Р° Р±РѕР»СЊС€Рµ РїСЏС‚Рё РјРёРЅСѓС‚ РґРµР»РёС‚ С‚СЂРµРє РЅР° РґРІРµ РїРѕРµР·РґРєРё`() {
        val stepDeg = 10.0 / 111_320.0
        val first = (0 until 60).map { i -> Triple(34_000 + i, 56.0 + i * stepDeg, 43.0) }
        val gap = 11 * 60
        val second = (0 until 60).map { i -> Triple(34_100 + gap + i, 56.0 + i * stepDeg, 43.0) }
        val parsed = NmeaParser.parse(log(first + second), LocalDate.of(2025, 6, 6))
        val trips = TripBuilder.build(parsed, "test.nmea", true)
        assertEquals(2, trips.size)
    }

    @Test
    fun `РєРѕСЂРѕС‚РєРёР№ РѕС‚СЂРµР·РѕРє РјРµРЅСЊС€Рµ 300 РјРµС‚СЂРѕРІ РЅРµ СЃС‡РёС‚Р°РµС‚СЃСЏ РїРѕРµР·РґРєРѕР№`() {
        val stepDeg = 2.0 / 111_320.0   // ~2 Рј/СЃ, 20 С‚РѕС‡РµРє -> ~38 Рј
        val points = (0 until 20).map { i -> Triple(34_000 + i, 56.0 + i * stepDeg, 43.0) }
        val parsed = NmeaParser.parse(log(points), LocalDate.of(2025, 6, 6))
        assertTrue(TripBuilder.build(parsed, "test.nmea", true).isEmpty())
    }
}

