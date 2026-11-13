package ru.gpstrips.data

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Одна точка трека. [speedKmh] == null, если приёмник не отдал скорость. */
data class TrackPoint(
    val timeMs: Long,
    val lat: Double,
    val lon: Double,
    val speedKmh: Double? = null,
    val altM: Double? = null,
    val sats: Int? = null,
    val hdop: Double? = null
)

/** Поездка: отрезок трека, отрезанный от остальных по паузе во времени. */
data class Trip(
    val id: String,
    val source: String,
    val startMs: Long,
    val endMs: Long,
    val distanceM: Double,
    val movingSec: Long,
    val maxSpeedKmh: Double,
    val avgSpeedKmh: Double,
    val timeReliable: Boolean,
    val track: List<TrackPoint> = emptyList()
) {
    val durationSec: Long get() = ((endMs - startMs) / 1000L).coerceAtLeast(0L)

    val startLat: Double get() = track.firstOrNull()?.lat ?: 0.0
    val startLon: Double get() = track.firstOrNull()?.lon ?: 0.0
    val endLat: Double get() = track.lastOrNull()?.lat ?: 0.0
    val endLon: Double get() = track.lastOrNull()?.lon ?: 0.0
}

object Geo {
    private const val R = 6371000.0

    fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * R * asin(min(1.0, sqrt(a)))
    }
}
