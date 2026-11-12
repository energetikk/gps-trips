package ru.gpstrips.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.gpstrips.data.TrackPoint
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

private const val TILE = 256
private const val TILE_URL = "https://tile.openstreetmap.org"

/** Кэш тайлов живёт всё время работы процесса. */
private val tileCache = HashMap<String, ImageBitmap?>()

private fun lonToPx(lon: Double, z: Int): Double = (lon + 180.0) / 360.0 * TILE * (1 shl z)

private fun latToPx(lat: Double, z: Int): Double {
    val r = Math.toRadians(lat)
    val y = ln(tan(r) + 1.0 / cos(r))
    return (1.0 - y / PI) / 2.0 * TILE * (1 shl z)
}

private class MapView(val zoom: Int, val originX: Double, val originY: Double)

private fun fitView(points: List<TrackPoint>, w: Float, h: Float): MapView? {
    if (points.size < 2 || w <= 0f || h <= 0f) return null
    var minLat = 90.0
    var maxLat = -90.0
    var minLon = 180.0
    var maxLon = -180.0
    for (p in points) {
        if (p.lat < minLat) minLat = p.lat
        if (p.lat > maxLat) maxLat = p.lat
        if (p.lon < minLon) minLon = p.lon
        if (p.lon > maxLon) maxLon = p.lon
    }
    for (z in 17 downTo 3) {
        val x0 = lonToPx(minLon, z)
        val x1 = lonToPx(maxLon, z)
        val y0 = latToPx(maxLat, z)
        val y1 = latToPx(minLat, z)
        if (x1 - x0 <= w * 0.82f && y1 - y0 <= h * 0.82f) {
            val cx = (x0 + x1) / 2.0
            val cy = (y0 + y1) / 2.0
            return MapView(z, cx - w / 2.0, cy - h / 2.0)
        }
    }
    val cx = lonToPx((minLon + maxLon) / 2, 3)
    val cy = latToPx((minLat + maxLat) / 2, 3)
    return MapView(3, cx - w / 2.0, cy - h / 2.0)
}

private fun downloadTile(key: String): ImageBitmap? = try {
    val conn = URL("$TILE_URL/$key.png").openConnection() as HttpURLConnection
    conn.connectTimeout = 6000
    conn.readTimeout = 6000
    conn.setRequestProperty("User-Agent", "GpsTrips/1.0 (personal car head unit)")
    conn.inputStream.use { stream ->
        BitmapFactory.decodeStream(stream)?.asImageBitmap()
    }
} catch (e: Exception) {
    null
}

/** Асинхронная подгрузка тайлов видимой области. Нет сети — останется тёмный фон и линия трека. */
@Composable
private fun rememberTiles(view: MapView, w: Int, h: Int): Map<String, ImageBitmap> {
    val keys = remember(view, w, h) {
        val x0 = floor(view.originX / TILE).toInt()
        val x1 = floor((view.originX + w) / TILE).toInt()
        val y0 = floor(view.originY / TILE).toInt()
        val y1 = floor((view.originY + h) / TILE).toInt()
        val list = ArrayList<String>()
        for (x in x0..x1) for (y in y0..y1) list.add("${view.zoom}/$x/$y")
        list
    }
    var images by remember { mutableStateOf<Map<String, ImageBitmap>>(emptyMap()) }

    LaunchedEffect(keys) {
        val loaded = withContext(Dispatchers.IO) {
            val result = HashMap<String, ImageBitmap>()
            for (k in keys) {
                val cached = tileCache[k]
                if (cached != null) {
                    result[k] = cached
                    continue
                }
                if (tileCache.containsKey(k)) continue
                val bmp = downloadTile(k)
                tileCache[k] = bmp
                if (bmp != null) result[k] = bmp
            }
            result
        }
        images = loaded
    }
    return images
}

@Composable
fun TrackMap(points: List<TrackPoint>, modifier: Modifier = Modifier) {
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    val view = remember(points, boxSize) {
        if (boxSize.width > 0 && boxSize.height > 0) {
            fitView(points, boxSize.width.toFloat(), boxSize.height.toFloat())
        } else {
            null
        }
    }
    val tiles = if (view != null) rememberTiles(view, boxSize.width, boxSize.height) else emptyMap()

    Canvas(modifier.background(P.surface).onSizeChanged { boxSize = it }) {
        val canvas: Size = size
        if (points.size < 2) return@Canvas
        val v = view
        if (v == null) {
            drawCircle(P.accent2, radius = 6f, center = Offset(canvas.width / 2f, canvas.height / 2f))
            return@Canvas
        }

        for ((key, img) in tiles) {
            val parts = key.split('/')
            if (parts.size != 3) continue
            val tx = parts[1].toIntOrNull() ?: continue
            val ty = parts[2].toIntOrNull() ?: continue
            drawImage(
                image = img,
                topLeft = Offset(
                    (tx * TILE - v.originX).toFloat(),
                    (ty * TILE - v.originY).toFloat()
                ),
                alpha = 0.85f
            )
        }
        if (tiles.isNotEmpty()) drawRect(Color(0x33101820))

        fun px(lon: Double) = (lonToPx(lon, v.zoom) - v.originX).toFloat()
        fun py(lat: Double) = (latToPx(lat, v.zoom) - v.originY).toFloat()

        val path = Path()
        path.moveTo(px(points[0].lon), py(points[0].lat))
        for (i in 1 until points.size) path.lineTo(px(points[i].lon), py(points[i].lat))

        drawPath(
            path,
            Color(0xCC000000),
            style = Stroke(9f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        drawPath(
            path,
            P.accent2,
            style = Stroke(4.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        val start = points.first()
        val end = points.last()
        drawCircle(P.accent, radius = 9f, center = Offset(px(start.lon), py(start.lat)))
        drawCircle(P.bg, radius = 4f, center = Offset(px(start.lon), py(start.lat)))
        drawCircle(P.danger, radius = 9f, center = Offset(px(end.lon), py(end.lat)))
        drawCircle(P.bg, radius = 4f, center = Offset(px(end.lon), py(end.lat)))
    }
}
