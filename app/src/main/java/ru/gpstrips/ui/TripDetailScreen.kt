package ru.gpstrips.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.gpstrips.data.Fmt
import ru.gpstrips.data.Trip

@Composable
fun TripDetailScreen(
    trip: Trip,
    onBack: () -> Unit,
    onSend: () -> Unit,
    onOpenMap: () -> Unit,
    sending: Boolean
) {
    val speeds = trip.track.map { (it.speedKmh ?: 0.0).toFloat() }

    Column(
        Modifier
            .fillMaxSize()
            .background(P.bg)
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ActionButton("‹ Назад", onBack)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "${Fmt.dayLabel(trip.startMs)}, ${Fmt.hhmm(trip.startMs)} → ${Fmt.hhmm(trip.endMs)}",
                    color = P.text,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(trip.source, color = P.dim, fontSize = 12.sp)
            }
            ActionButton(
                text = if (sending) "Отправляю…" else "В Telegram",
                onClick = onSend,
                primary = true,
                enabled = !sending
            )
        }

        Spacer(Modifier.height(16.dp))

        Panel(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatTile("расстояние", Fmt.km(trip.distanceM), accent = P.accent)
                StatTile("всего времени", Fmt.dur(trip.durationSec))
                StatTile("в движении", Fmt.dur(trip.movingSec))
                StatTile("точек трека", trip.track.size.toString())
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                StatTile("средняя скорость", Fmt.speed(trip.avgSpeedKmh))
                StatTile("максимальная", Fmt.speed(trip.maxSpeedKmh), accent = P.warn)
            }
            if (!trip.timeReliable) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "В файле нет корректного времени: длительность и скорости показаны приблизительно.",
                    color = P.warn,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Panel(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Профиль скорости", color = P.text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.weight(1f))
                Text(
                    "0 — ${Fmt.speed(trip.maxSpeedKmh)}",
                    color = P.dim,
                    fontSize = 12.sp
                )
            }
            Spacer(Modifier.height(10.dp))
            SpeedChart(speeds, Modifier.fillMaxWidth().height(110.dp), color = P.accent)
        }

        Spacer(Modifier.height(12.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(20.dp))
                .then(if (trip.track.size >= 2) Modifier.clickable { onOpenMap() } else Modifier)
        ) {
            if (trip.track.size >= 2) {
                TrackMap(trip.track, Modifier.fillMaxSize())
                Row(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("● старт", color = P.accent, fontSize = 11.sp)
                    Spacer(Modifier.width(12.dp))
                    Text("● финиш", color = P.danger, fontSize = 11.sp)
                }
                Row(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(P.accent)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Яндекс.Карта ›",
                        color = Color(0xFF04231B),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else {
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Трек не сохранён", color = P.dim, fontSize = 13.sp)
                }
            }
        }
    }
}
