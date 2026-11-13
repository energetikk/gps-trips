package ru.gpstrips.data

/**
 * Превращает поток точек в поездки.
 *
 * Правила простые и без магии:
 *  - пауза больше [SPLIT_GAP_MS] (5 минут) заканчивает поездку;
 *  - «телепорт» (сотни метров за пару секунд) отбрасывается как мусор приёмника;
 *  - отрезок короче [MIN_TRIP_M] (300 м) поездкой не считается.
 */
object TripBuilder {

    private const val SPLIT_GAP_MS = 5L * 60 * 1000
    private const val MIN_TRIP_M = 300.0
    private const val JUMP_M = 500.0
    private const val JUMP_MAX_GAP_MS = 15_000L
    private const val MAX_KMH = 250.0
    private const val MOVING_KMH = 3.0
    private const val MIN_STEP_M = 2.0

    fun build(points: List<TrackPoint>, source: String, timeReliable: Boolean): List<Trip> {
        if (points.size < 2) return emptyList()
        val sorted = points.sortedBy { it.timeMs }

        // убрать дубли (две строки в одну секунду) и нулевые координаты
        val clean = ArrayList<TrackPoint>(sorted.size)
        for (p in sorted) {
            if (p.lat.isNaN() || p.lon.isNaN()) continue
            if (p.lat == 0.0 && p.lon == 0.0) continue
            if (p.lat < -90 || p.lat > 90 || p.lon < -180 || p.lon > 180) continue
            val prev = clean.lastOrNull()
            if (prev != null && p.timeMs - prev.timeMs < 400) continue
            clean.add(p)
        }
        if (clean.size < 2) return emptyList()

        // нарезать на сегменты: пауза по времени или скачок координат.
        // Скачок на десятки километров — это либо сбой приёмника, либо другой
        // GPS-поток; если следующая точка остаётся рядом с новой, начинаем
        // новый сегмент, иначе выброс просто пропускаем.
        val segments = ArrayList<List<TrackPoint>>()
        var cur = ArrayList<TrackPoint>()
        var index = 0
        while (index < clean.size) {
            val p = clean[index]
            val prev = cur.lastOrNull()
            if (prev == null) {
                cur.add(p)
                index++
                continue
            }

            val dt = p.timeMs - prev.timeMs
            if (dt > SPLIT_GAP_MS) {
                if (cur.size > 1) segments.add(cur)
                cur = ArrayList()
                cur.add(p)
                index++
                continue
            }

            val d = Geo.distanceM(prev.lat, prev.lon, p.lat, p.lon)
            val impliedKmh = if (dt > 0) d / (dt / 1000.0) * 3.6 else Double.MAX_VALUE
            if (d > JUMP_M && impliedKmh > MAX_KMH) {
                val next = clean.getOrNull(index + 1)
                val relocation = next != null &&
                    next.timeMs - p.timeMs <= JUMP_MAX_GAP_MS &&
                    Geo.distanceM(p.lat, p.lon, next.lat, next.lon) <
                    Geo.distanceM(prev.lat, prev.lon, next.lat, next.lon)
                if (relocation) {
                    if (cur.size > 1) segments.add(cur)
                    cur = ArrayList()
                    cur.add(p)
                }
                index++
                continue
            }

            cur.add(p)
            index++
        }
        if (cur.size > 1) segments.add(cur)

        val trips = ArrayList<Trip>()
        for (seg in segments) {
            buildTrip(seg, source, timeReliable)?.let { trips.add(it) }
        }
        return trips
    }

    private fun buildTrip(seg: List<TrackPoint>, source: String, timeReliable: Boolean): Trip? {
        var dist = 0.0
        var movingMs = 0L
        val speeds = ArrayList<Double>(seg.size)
        val enriched = ArrayList<TrackPoint>(seg.size)
        var prev: TrackPoint? = null
        for (p in seg) {
            val pv = prev
            if (pv == null) {
                enriched.add(p)
            } else {
                val d = Geo.distanceM(pv.lat, pv.lon, p.lat, p.lon)
                val dt = (p.timeMs - pv.timeMs).coerceAtLeast(1L)
                if (d >= MIN_STEP_M) dist += d
                val v = p.speedKmh ?: (d / dt * 3.6)
                speeds.add(v)
                if (v > MOVING_KMH) movingMs += dt
                enriched.add(p.copy(speedKmh = v))
            }
            prev = p
        }

        if (dist < MIN_TRIP_M || enriched.size < 2) return null

        // 98-й перцентиль вместо голого максимума — одиночный выброс не портит «макс. скорость»
        val maxV = if (speeds.isEmpty()) 0.0 else {
            val s = speeds.sorted()
            s[(s.size * 0.98).toInt().coerceAtMost(s.size - 1)]
        }
        val avgV = if (movingMs > 0) dist / (movingMs / 1000.0) * 3.6 else 0.0

        return Trip(
            id = source + "@" + enriched.first().timeMs,
            source = source,
            startMs = enriched.first().timeMs,
            endMs = enriched.last().timeMs,
            distanceM = dist,
            movingSec = movingMs / 1000,
            maxSpeedKmh = maxV,
            avgSpeedKmh = avgV,
            timeReliable = timeReliable,
            track = downsample(enriched)
        )
    }

    private fun downsample(pts: List<TrackPoint>, max: Int = 400): List<TrackPoint> {
        if (pts.size <= max) return pts
        val step = pts.size.toDouble() / max
        val res = ArrayList<TrackPoint>(max + 1)
        var i = 0.0
        while (i < pts.size) {
            res.add(pts[i.toInt()])
            i += step
        }
        res.add(pts.last())
        return res
    }
}
