package ru.gpstrips.data

import java.time.LocalDate
import kotlin.math.abs

/**
 * Разбор текстовых NMEA-логов (u-blox M9N и большинство USB-приёмников пишут именно их).
 *
 * Основной источник точки выбирается один раз на файл:
 *  1. $PUBX,00 — проприетарная строка u-blox (есть скорость и высота);
 *  2. $xxRMC — стандартная строка (есть дата, время, скорость);
 *  3. $xxGGA — только координаты, время и высота (скорость считается позже по треку).
 *
 * Строки GGA дополнительно дают высоту, число спутников и HDOP. Если логгер
 * пишет два потока GPS с одинаковым временем (бывает на магнитолах), из каждой
 * пары выбирается точка с лучшим качеством — по спутникам и HDOP.
 *
 * Контрольные суммы не проверяются намеренно: часть логгеров переписывает строки
 * и ломает CRC, при этом координаты остаются валидными.
 */
object NmeaParser {

    private data class GgaFix(
        val sec: Double,
        val lat: Double,
        val lon: Double,
        val sats: Int?,
        val hdop: Double?,
        val altM: Double?
    )

    fun looksLikeNmea(text: String): Boolean =
        text.lineSequence().take(200).any { it.trimStart().startsWith("$") }

    fun parse(text: String, fallbackDate: LocalDate): List<TrackPoint> {
        val sentences = ArrayList<String>(4096)
        for (raw in text.lineSequence()) {
            val t = raw.trim()
            if (t.startsWith("$")) sentences.add(t)
        }
        if (sentences.isEmpty()) return emptyList()

        val primary = when {
            sentences.any { it.startsWith("\$PUBX,00") } -> Primary.PUBX
            sentences.any { it.length > 6 && it.substring(3, 6) == "RMC" } -> Primary.RMC
            else -> Primary.GGA
        }

        val out = ArrayList<TrackPoint>(sentences.size)
        var date = fallbackDate
        var dayOffset = 0L
        var lastSecOfDay = -1.0

        fun stamp(secOfDay: Double): Long {
            if (lastSecOfDay >= 0 && secOfDay < lastSecOfDay - 43200.0) dayOffset++
            lastSecOfDay = secOfDay
            return (date.toEpochDay() + dayOffset) * 86_400_000L + (secOfDay * 1000.0).toLong()
        }

        // GGA, пришедшая раньше своей RMC (некоторые логгеры пишут в таком порядке).
        var pendingGga: GgaFix? = null
        // Индекс последней добавленной RMC-точки, чтобы приклеить к ней GGA.
        var lastRmcIndex = -1
        var lastRmcSec = Double.NaN

        for (s in sentences) {
            val f = s.split(',')

            if (primary == Primary.RMC) {
                if (isType(f, "RMC") && f.size >= 10) {
                    parseDate(f[9])?.let { date = it }
                    if (f[2] == "A") {
                        val sec = secOfDay(f[1]) ?: continue
                        val lat = coord(f[3], f[4]) ?: continue
                        val lon = coord(f[5], f[6]) ?: continue
                        if (lat == 0.0 && lon == 0.0) continue

                        val gga = pendingGga?.takeIf { abs(sec - it.sec) < 0.05 }
                        out.add(
                            TrackPoint(
                                timeMs = stamp(sec),
                                lat = lat,
                                lon = lon,
                                speedKmh = f[7].toDoubleOrNull()?.times(1.852),
                                altM = gga?.altM,
                                sats = gga?.sats,
                                hdop = gga?.hdop
                            )
                        )
                        lastRmcIndex = out.lastIndex
                        lastRmcSec = sec
                        if (gga != null) pendingGga = null
                    }
                } else if (isType(f, "GGA")) {
                    val gga = ggaFix(f) ?: continue
                    if (lastRmcIndex >= 0 && abs(gga.sec - lastRmcSec) < 0.05) {
                        val p = out[lastRmcIndex]
                        out[lastRmcIndex] = p.copy(
                            altM = gga.altM,
                            sats = gga.sats,
                            hdop = gga.hdop
                        )
                    } else {
                        pendingGga = gga
                    }
                }
            } else if (primary == Primary.GGA) {
                if (isType(f, "GGA")) {
                    val gga = ggaFix(f) ?: continue
                    out.add(
                        TrackPoint(
                            timeMs = stamp(gga.sec),
                            lat = gga.lat,
                            lon = gga.lon,
                            speedKmh = null,
                            altM = gga.altM,
                            sats = gga.sats,
                            hdop = gga.hdop
                        )
                    )
                }
            } else {
                if (f.size >= 13 && f[0] == "\$PUBX" && f[1] == "00") {
                    val nav = f[8]
                    if (nav != "NF") {
                        val sec = secOfDay(f[2])
                        val lat = coord(f[3], f[4])
                        val lon = coord(f[5], f[6])
                        if (sec != null && lat != null && lon != null) {
                            out.add(
                                TrackPoint(
                                    timeMs = stamp(sec),
                                    lat = lat,
                                    lon = lon,
                                    speedKmh = f[11].toDoubleOrNull()?.times(1.852),
                                    altM = f[7].toDoubleOrNull()
                                )
                            )
                        }
                    }
                }
            }
        }
        return dedupeByTime(out)
    }

    private fun dedupeByTime(points: List<TrackPoint>): List<TrackPoint> {
        if (points.size < 2) return points
        val sorted = points.sortedBy { it.timeMs }
        val result = ArrayList<TrackPoint>(sorted.size)
        var i = 0
        while (i < sorted.size) {
            val time = sorted[i].timeMs
            val group = ArrayList<TrackPoint>(2)
            var j = i
            while (j < sorted.size && sorted[j].timeMs == time) {
                group.add(sorted[j])
                j++
            }
            result.add(bestOf(group, result.lastOrNull()))
            i = j
        }
        return result
    }

    /**
     * Если на одно время пришло несколько точек (два GPS-потока), берём точку
     * с лучшим качеством. При близком качестве — ту, что ближе к предыдущей.
     */
    private fun bestOf(group: List<TrackPoint>, prev: TrackPoint?): TrackPoint {
        if (group.size == 1) return group[0]
        val scored = group.map { it to quality(it) }
        val top = scored.maxOf { it.second }
        val close = scored.filter { it.second >= top - 5.0 }.map { it.first }
        if (prev != null && close.size > 1) {
            return close.minByOrNull {
                Geo.distanceM(prev.lat, prev.lon, it.lat, it.lon)
            } ?: close[0]
        }
        return close[0]
    }

    private fun quality(p: TrackPoint): Double =
        (p.sats ?: -1) * 10.0 - (p.hdop ?: 20.0)

    private fun ggaFix(f: List<String>): GgaFix? {
        if (!isType(f, "GGA") || f.size < 10) return null
        val fix = f[6]
        if (fix.isEmpty() || fix == "0") return null
        val sec = secOfDay(f[1]) ?: return null
        val lat = coord(f[2], f[3]) ?: return null
        val lon = coord(f[4], f[5]) ?: return null
        if (lat == 0.0 && lon == 0.0) return null
        return GgaFix(
            sec = sec,
            lat = lat,
            lon = lon,
            sats = f[7].toIntOrNull(),
            hdop = f[8].toDoubleOrNull(),
            altM = f[9].toDoubleOrNull()
        )
    }

    private fun isType(f: List<String>, type: String): Boolean =
        f.isNotEmpty() && f[0].length >= 6 && f[0].substring(3, 6) == type

    private enum class Primary { PUBX, RMC, GGA }

    /** ddmm.mmmm / dddmm.mmmm + полушарие -> десятичные градусы. */
    private fun coord(value: String, hemi: String): Double? {
        if (value.isEmpty()) return null
        val dot = value.indexOf('.')
        if (dot < 0) return null
        val degLen = dot - 2
        if (degLen <= 0) return null
        val deg = value.substring(0, degLen).toDoubleOrNull() ?: return null
        val min = value.substring(degLen).toDoubleOrNull() ?: return null
        if (deg == 0.0 && min == 0.0) return null
        val d = deg + min / 60.0
        return if (hemi == "S" || hemi == "W") -d else d
    }

    private fun secOfDay(value: String): Double? {
        if (value.length < 6) return null
        val h = value.substring(0, 2).toIntOrNull() ?: return null
        val m = value.substring(2, 4).toIntOrNull() ?: return null
        val s = value.substring(4).toDoubleOrNull() ?: return null
        if (h > 23 || m > 59 || s >= 61.0) return null
        return h * 3600.0 + m * 60.0 + s
    }

    private fun parseDate(value: String): LocalDate? {
        if (value.length < 6) return null
        val d = value.substring(0, 2).toIntOrNull() ?: return null
        val m = value.substring(2, 4).toIntOrNull() ?: return null
        val y = value.substring(4, 6).toIntOrNull() ?: return null
        return try {
            LocalDate.of(2000 + y, m, d)
        } catch (e: Exception) {
            null
        }
    }
}
