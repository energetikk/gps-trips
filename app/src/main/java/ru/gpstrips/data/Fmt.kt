package ru.gpstrips.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Fmt {

    val ru: Locale = Locale("ru")

    private val timeHm = DateTimeFormatter.ofPattern("HH:mm", ru)
    private val dayMonth = DateTimeFormatter.ofPattern("d MMMM", ru)
    private val dateTime = DateTimeFormatter.ofPattern("d MMMM, HH:mm", ru)

    fun km(meters: Double): String = String.format(ru, "%.1f км", meters / 1000.0)

    fun kmValue(meters: Double): String = String.format(ru, "%.1f", meters / 1000.0)

    fun dur(sec: Long): String {
        if (sec <= 0) return "0 мин"
        if (sec < 60) return "$sec с"
        val h = sec / 3600
        val m = (sec % 3600) / 60
        return if (h > 0) "$h ч $m мин" else "$m мин"
    }

    fun speed(v: Double): String = String.format(ru, "%.0f км/ч", v)

    fun hhmm(ms: Long): String =
        Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(timeHm)

    fun dateTime(ms: Long): String =
        Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(dateTime)

    fun dayLabel(ms: Long): String {
        val zone = ZoneId.systemDefault()
        val day = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
        val today = LocalDate.now(zone)
        return when (day) {
            today -> "Сегодня"
            today.minusDays(1) -> "Вчера"
            else -> day.format(dayMonth)
        }
    }

    fun dayKey(ms: Long): String =
        Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().toString()
}
