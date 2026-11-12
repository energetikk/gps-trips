package ru.gpstrips.data

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Текстовые сводки для Telegram (HTML parse mode). */
object Report {

    private const val DAY_MS = 86_400_000L

    fun summary(trips: List<Trip>): String {
        val zone = ZoneId.systemDefault()
        val startOfDay = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()

        fun agg(fromMs: Long): String {
            val sel = trips.filter { it.startMs >= fromMs }
            val meters = sel.sumOf { it.distanceM }
            val sec = sel.sumOf { it.durationSec }
            return "${sel.size} поезд. · ${Fmt.km(meters)} · ${Fmt.dur(sec)}"
        }

        val sb = StringBuilder()
        sb.append("<b>GPS Поездки</b> · ")
            .append(LocalDate.now(zone).format(DateTimeFormatter.ofPattern("d MMMM", Fmt.ru)))
            .append("\n")
        sb.append("<b>Сегодня:</b> ").append(agg(startOfDay)).append("\n")
        sb.append("<b>7 дней:</b> ").append(agg(startOfDay - 6 * DAY_MS)).append("\n")
        sb.append("<b>30 дней:</b> ").append(agg(startOfDay - 29 * DAY_MS))

        trips.firstOrNull()?.let {
            sb.append("\n\n<b>Последняя поездка</b>\n").append(trip(it))
        }
        return sb.toString()
    }

    fun trip(t: Trip): String = buildString {
        append("<b>")
            .append(Fmt.dayLabel(t.startMs))
            .append(", ")
            .append(Fmt.hhmm(t.startMs))
            .append(" → ")
            .append(Fmt.hhmm(t.endMs))
            .append("</b>\n")
        append(Fmt.km(t.distanceM)).append(" · ").append(Fmt.dur(t.durationSec)).append("\n")
        append("средняя ").append(Fmt.speed(t.avgSpeedKmh))
            .append(" · максимальная ").append(Fmt.speed(t.maxSpeedKmh))
    }
}
