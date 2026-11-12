package ru.gpstrips.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.gpstrips.data.Fmt
import ru.gpstrips.data.LiveState
import ru.gpstrips.data.Trip

private const val DAY_MS = 86_400_000L

private class DayGroup(val label: String, val trips: List<Trip>, val meters: Double, val sec: Long)

@Composable
fun TripListScreen(
    state: UiState,
    onOpen: (Trip) -> Unit,
    onRefresh: () -> Unit,
    onSettings: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(P.bg)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("GPS Поездки", color = P.text, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    text = when {
                        state.loading -> "читаю логи…"
                        state.target == null -> "папка с логами не выбрана"
                        else -> "файлов: ${state.fileCount} · поездок: ${state.trips.size}"
                    },
                    color = P.dim,
                    fontSize = 13.sp
                )
                if (state.liveEnabled) {
                    val live by LiveState.status.collectAsState()
                    Text(
                        text = when {
                            live.lastError != null -> "передача координат: ${live.lastError}"
                            live.lastSentAt > 0 ->
                                "передаю координаты · ${Fmt.hhmm(live.lastSentAt)} · ${live.sentCount}"

                            else -> "передача координат включена, жду точку…"
                        },
                        color = if (live.lastError != null) P.warn else P.accent,
                        fontSize = 11.sp
                    )
                }
            }
            ActionButton("Обновить", onRefresh)
            Spacer(Modifier.width(10.dp))
            ActionButton("Настройки", onSettings)
        }
        Spacer(Modifier.height(16.dp))

        if (state.target == null) {
            Panel(Modifier.fillMaxWidth()) {
                Text("Нужно указать папку", color = P.text, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Укажите папку, куда приложение GPS-модуля пишет лог. " +
                        "Подойдут файлы .nmea, .txt, .log и .gpx.",
                    color = P.dim,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(14.dp))
                ActionButton("Открыть настройки", onSettings, primary = true)
            }
            return@Column
        }

        val groups = remember(state.trips) { groupByDay(state.trips) }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { SummaryPanel(state.trips) }

            state.error?.let { message ->
                item {
                    Panel(Modifier.fillMaxWidth(), color = P.surface2) {
                        Text(message, color = P.warn, fontSize = 13.sp)
                    }
                }
            }

            groups.forEach { group ->
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, start = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(group.label, color = P.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        Text(
                            "${Fmt.km(group.meters)} · ${Fmt.dur(group.sec)}",
                            color = P.dim,
                            fontSize = 12.sp
                        )
                    }
                }
                items(group.trips, key = { it.id }) { trip ->
                    TripRow(trip) { onOpen(trip) }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

private fun groupByDay(trips: List<Trip>): List<DayGroup> =
    trips.groupBy { Fmt.dayKey(it.startMs) }
        .map { (_, list) ->
            DayGroup(
                label = Fmt.dayLabel(list.first().startMs),
                trips = list,
                meters = list.sumOf { it.distanceM },
                sec = list.sumOf { it.durationSec }
            )
        }

@Composable
private fun TripRow(trip: Trip, onClick: () -> Unit) {
    Panel(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${Fmt.hhmm(trip.startMs)} → ${Fmt.hhmm(trip.endMs)}",
                    color = P.text,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    if (trip.timeReliable) {
                        "${Fmt.dur(trip.durationSec)} · ср. ${Fmt.speed(trip.avgSpeedKmh)}"
                    } else {
                        "${Fmt.dur(trip.durationSec)} · время в файле недостоверно"
                    },
                    color = P.dim,
                    fontSize = 12.sp
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Fmt.km(trip.distanceM), color = P.accent, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                Text("макс ${Fmt.speed(trip.maxSpeedKmh)}", color = P.dim, fontSize = 11.sp)
            }
            if (trip.track.size > 2) {
                Spacer(Modifier.width(14.dp))
                SpeedChart(
                    values = trip.track.map { (it.speedKmh ?: 0.0).toFloat() },
                    modifier = Modifier
                        .width(90.dp)
                        .height(38.dp),
                    showFill = false
                )
            }
        }
    }
}

@Composable
private fun SummaryPanel(trips: List<Trip>) {
    val zone = java.time.ZoneId.systemDefault()
    val startOfDay = java.time.LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()

    fun line(label: String, fromMs: Long): Triple<String, String, String> {
        val list = trips.filter { it.startMs >= fromMs }
        return Triple(
            label,
            Fmt.km(list.sumOf { it.distanceM }),
            "${list.size} поезд. · ${Fmt.dur(list.sumOf { it.durationSec })}"
        )
    }

    val rows = listOf(
        line("Сегодня", startOfDay),
        line("7 дней", startOfDay - 6 * DAY_MS),
        line("30 дней", startOfDay - 29 * DAY_MS)
    )

    Panel(Modifier.fillMaxWidth()) {
        rows.forEachIndexed { index, row ->
            if (index > 0) Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(row.first, color = P.dim, fontSize = 13.sp, modifier = Modifier.width(80.dp))
                Text(row.second, color = P.text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text(row.third, color = P.dim, fontSize = 12.sp)
            }
        }
    }
}
