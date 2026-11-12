package ru.gpstrips.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.yandex.mapkit.Animation
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.geometry.Geometry
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.geometry.Polyline
import com.yandex.mapkit.map.CameraPosition
import com.yandex.mapkit.map.IconStyle
import com.yandex.mapkit.map.LineStyle
import com.yandex.mapkit.map.MapWindow
import com.yandex.mapkit.map.SizeChangedListener
import com.yandex.mapkit.mapview.MapView
import com.yandex.runtime.image.ImageProvider
import ru.gpstrips.BuildConfig
import ru.gpstrips.data.Fmt
import ru.gpstrips.data.Trip

/**
 * Маршрут поездки на карте Яндекса (MapKit).
 *
 * Точки трека рисуются полилинией, старт и финиш — метками. Камера
 * автоматически охватывает весь трек, кнопка «Весь маршрут» возвращает её
 * к маршруту после ручных жестов.
 */
@Composable
fun TripMapScreen(trip: Trip, onBack: () -> Unit) {
    if (BuildConfig.MAPKIT_API_KEY.isBlank()) {
        MapKeyMissing(onBack)
        return
    }
    if (trip.track.size < 2) {
        EmptyTrack(trip, onBack)
        return
    }

    val context = LocalContext.current
    val mapView = remember { MapView(context) }

    val fitToTrack: () -> Unit = {
        val points = trip.track.map { Point(it.lat, it.lon) }
        if (points.size >= 2) {
            val position = mapView.mapWindow.map.cameraPosition(
                Geometry.fromPolyline(Polyline(points))
            )
            mapView.mapWindow.map.move(position, Animation(Animation.Type.SMOOTH, 0.25f), null)
        } else if (points.size == 1) {
            mapView.mapWindow.map.move(CameraPosition(points[0], 16f, 0f, 0f))
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, mapView) {
        var started = false
        val start = {
            if (!started) {
                MapKitFactory.getInstance().onStart()
                mapView.onStart()
                started = true
            }
        }
        val stop = {
            if (started) {
                mapView.onStop()
                MapKitFactory.getInstance().onStop()
                started = false
            }
        }
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) start()
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> start()
                Lifecycle.Event.ON_STOP -> stop()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            stop()
        }
    }

    DisposableEffect(mapView, trip.id) {
        val map = mapView.mapWindow.map
        val collection = map.mapObjects.addCollection()

        val start = trip.track.first()
        val end = trip.track.last()
        val points = trip.track.map { Point(it.lat, it.lon) }

        collection.addPolyline(Polyline(points)).apply {
            setStrokeColor(P.accent2.toArgb())
            style = LineStyle().apply {
                strokeWidth = 5f
                outlineWidth = 2.5f
                outlineColor = 0xCC000000.toInt()
            }
        }
        collection.addPlacemark().apply {
            geometry = Point(start.lat, start.lon)
            setIcon(ImageProvider.fromBitmap(dot(P.accent.toArgb())))
            setIconStyle(IconStyle().apply { anchor = PointF(0.5f, 0.5f) })
        }
        collection.addPlacemark().apply {
            geometry = Point(end.lat, end.lon)
            setIcon(ImageProvider.fromBitmap(dot(P.danger.toArgb())))
            setIconStyle(IconStyle().apply { anchor = PointF(0.5f, 0.5f) })
        }

        // Камера ждёт первого размера окна: до этого MapKit не знает границ экрана.
        val fitted = booleanArrayOf(false)
        val sizeListener = object : SizeChangedListener {
            override fun onMapWindowSizeChanged(mapWindow: MapWindow, width: Int, height: Int) {
                if (!fitted[0] && width > 0 && height > 0) {
                    fitted[0] = true
                    fitToTrack()
                }
            }
        }
        mapView.mapWindow.addSizeChangedListener(sizeListener)
        if (mapView.mapWindow.width() > 0 && mapView.mapWindow.height() > 0) {
            fitted[0] = true
            fitToTrack()
        }

        onDispose {
            mapView.mapWindow.removeSizeChangedListener(sizeListener)
            collection.clear()
        }
    }

    Box(Modifier.fillMaxSize().background(P.bg)) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

        Row(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(P.bg.copy(alpha = 0.86f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ActionButton("‹ Назад", onBack)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Маршрут · ${Fmt.dayLabel(trip.startMs)}",
                    color = P.text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "${Fmt.hhmm(trip.startMs)} → ${Fmt.hhmm(trip.endMs)} · ${Fmt.km(trip.distanceM)}",
                    color = P.dim,
                    fontSize = 12.sp
                )
            }
            ActionButton("Весь маршрут", { fitToTrack() })
        }

        Row(
            Modifier
                .align(Alignment.BottomStart)
                .padding(14.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(P.bg.copy(alpha = 0.82f))
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("● старт", color = P.accent, fontSize = 11.sp)
            Text("● финиш", color = P.danger, fontSize = 11.sp)
        }
    }
}

@Composable
private fun MapKeyMissing(onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(P.bg)
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ActionButton("‹ Назад", onBack)
            Spacer(Modifier.width(14.dp))
            Text("Карта недоступна", color = P.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(16.dp))
        Panel(Modifier.fillMaxWidth()) {
            Text(
                "В сборку не встроен ключ MapKit. Задайте переменную окружения MAPKITSDK " +
                    "(ключ MapKit из кабинета разработчика Яндекса) и пересоберите приложение.",
                color = P.text,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun EmptyTrack(trip: Trip, onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(P.bg)
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ActionButton("‹ Назад", onBack)
            Spacer(Modifier.width(14.dp))
            Text(
                "Маршрут · ${Fmt.dayLabel(trip.startMs)}",
                color = P.text,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(16.dp))
        Panel(Modifier.fillMaxWidth()) {
            Text("У этой поездки нет сохранённого трека.", color = P.dim, fontSize = 14.sp)
        }
    }
}

/** Кружок-метка старта/финиша: без drawable-ресурсов. */
private fun dot(color: Int, sizePx: Int = 30): Bitmap {
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        this.color = 0xFF000000.toInt()
    }
    val r = sizePx / 2f - 3f
    canvas.drawCircle(sizePx / 2f, sizePx / 2f, r, fill)
    canvas.drawCircle(sizePx / 2f, sizePx / 2f, r, ring)
    return bmp
}
