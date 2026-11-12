package ru.gpstrips.data

import java.time.LocalDate

/**
 * Разбор текстовых NMEA-логов (u-blox M9N и большинство USB-приёмников пишут именно их).
 *
 * Основной источник точки выбирается один раз на файл:
 *  1. $PUBX,00 — проприетарная строка u-blox (есть скорость и высота);
 *  2. $xxRMC — стандартная строка (есть дата, время, скорость);
 *  3. $xxGGA — только координаты, время и высота (скорость считается позже по треку).
 *
 * Контрольные суммы не проверяются намеренно: часть логгеров переписывает строки
 * и ломает CRC, при этом координаты остаются валидными.
 */
object NmeaParser {

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

        for (s in sentences) {
            val f = s.split(',')
            when (primary) {
                Primary.RMC -> if (f.size >= 10 && f[0].length >= 6 && f[0].substring(3, 6) == "RMC") {
                    parseDate(f[9])?.let { date = it }
                    if (f[2] == "A") {
                        val sec = secOfDay(f[1])
                        val lat = coord(f[3], f[4])
                        val lon = coord(f[5], f[6])
                        if (sec != null && lat != null && lon != null) {
                            out.add(
                                TrackPoint(
                                    timeMs = stamp(sec),
                                    lat = lat,
                                    lon = lon,
                                    speedKmh = f[7].toDoubleOrNull()?.times(1.852)
                                )
                            )
                        }
                    }
                }

                Primary.GGA -> if (f.size >= 10 && f[0].length >= 6 && f[0].substring(3, 6) == "GGA") {
                    val fix = f[6]
                    if (fix.isNotEmpty() && fix != "0") {
                        val sec = secOfDay(f[1])
                        val lat = coord(f[2], f[3])
                        val lon = coord(f[4], f[5])
                        if (sec != null && lat != null && lon != null) {
                            out.add(
                                TrackPoint(
                                    timeMs = stamp(sec),
                                    lat = lat,
                                    lon = lon,
                                    speedKmh = null,
                                    altM = f[9].toDoubleOrNull()
                                )
                            )
                        }
                    }
                }

                Primary.PUBX -> if (f.size >= 13 && f[0] == "\$PUBX" && f[1] == "00") {
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
        return out
    }

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
