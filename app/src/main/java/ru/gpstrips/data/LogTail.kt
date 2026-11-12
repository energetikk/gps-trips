package ru.gpstrips.data

/** Позиция, готовая к отправке на сайт. */
data class LiveFix(
    val lat: Double,
    val lon: Double,
    val speedKmh: Double?,
    val timeMs: Long,
    val source: String
)

/**
 * Слежение за логом GPS-приёмника: файл дописывается другим приложением,
 * поэтому читаем только новые байты с прошлой позиции.
 *
 * Для NMEA-логов парсится приросший кусок, для GPX — последние 128 КБ файла
 * (переписывать 10-мегабайтный GPX каждые 2 секунды смысла нет).
 */
class LogTail(private val ctx: android.content.Context) {

    private var fileName: String? = null
    private var offset = 0L
    private var leftover = ""
    private var lastFix: LiveFix? = null

    /** Новые точки с прошлого вызова. null — новых данных нет. */
    fun poll(target: String): LiveFix? {
        val src = newest(target) ?: return null
        val isGpx = src.name.endsWith(".gpx", ignoreCase = true)

        if (src.name != fileName) {
            fileName = src.name
            offset = 0L
            leftover = ""
            lastFix = null
        }

        val fix = if (isGpx) {
            if (src.size <= offset) return null
            val tail = readTail(src, TAIL_BYTES) ?: return null
            offset = src.size
            GpxTail.lastFix(tail, src.name)
        } else {
            if (src.size <= offset) return null
            val want = (src.size - offset).coerceAtMost(MAX_CHUNK).toInt()
            val bytes = readFrom(src, offset, want) ?: return null
            if (bytes.isEmpty()) return null
            offset += bytes.size
            parseNmeaChunk(bytes, src)
        } ?: return null

        return fixWithSpeed(fix)
    }

    private fun fixWithSpeed(fix: LiveFix): LiveFix? {
        if (!valid(fix.lat, fix.lon)) return null
        val prev = lastFix
        val speed = fix.speedKmh ?: if (prev != null) {
            val dt = fix.timeMs - prev.timeMs
            if (dt in 1..30_000) {
                Geo.distanceM(prev.lat, prev.lon, fix.lat, fix.lon) / (dt / 1000.0) * 3.6
            } else null
        } else null
        val result = fix.copy(speedKmh = speed)
        lastFix = result
        return result
    }

    private fun parseNmeaChunk(bytes: ByteArray, src: SrcRef): LiveFix? {
        val text = String(bytes, Charsets.UTF_8)
        val lines = (leftover + text).split('\n')
        leftover = lines.last()
        val complete = lines.dropLast(1).joinToString("\n")
        if (complete.isBlank()) return null
        val date = java.time.Instant.ofEpochMilli(
            if (src.modified > 0) src.modified else System.currentTimeMillis()
        ).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        val last = NmeaParser.parse(complete, date).lastOrNull() ?: return null
        return LiveFix(last.lat, last.lon, last.speedKmh, last.timeMs, src.name)
    }

    private fun valid(lat: Double, lon: Double): Boolean =
        !lat.isNaN() && !lon.isNaN() && lat in -90.0..90.0 && lon in -180.0..180.0 &&
            !(lat == 0.0 && lon == 0.0)

    // ---------- источники ----------

    private class SrcRef(
        val name: String,
        val size: Long,
        val modified: Long,
        val file: java.io.File?,
        val uri: android.net.Uri?
    )

    private fun newest(target: String): SrcRef? =
        if (target.startsWith("/")) newestFile(java.io.File(target), 0)
        else newestSaf(target, 0)

    private fun newestFile(dir: java.io.File, depth: Int): SrcRef? {
        if (depth > 3 || !dir.isDirectory) return null
        val files = try {
            dir.listFiles()
        } catch (e: Exception) {
            null
        } ?: return null
        var best: SrcRef? = null
        for (f in files) {
            if (f.isDirectory) {
                val nested = newestFile(f, depth + 1)
                if (nested != null && (best == null || nested.modified > best.modified)) best = nested
            } else if (isLog(f.name) && f.length() > 0) {
                if (best == null || f.lastModified() > best.modified) {
                    best = SrcRef(f.name, f.length(), f.lastModified(), f, null)
                }
            }
        }
        return best
    }

    private fun newestSaf(uri: String, depth: Int): SrcRef? {
        val root = try {
            androidx.documentfile.provider.DocumentFile.fromTreeUri(ctx, android.net.Uri.parse(uri))
        } catch (e: Exception) {
            null
        } ?: return null
        return newestSafDoc(root, depth)
    }

    private fun newestSafDoc(dir: androidx.documentfile.provider.DocumentFile, depth: Int): SrcRef? {
        if (depth > 3 || !dir.isDirectory) return null
        val children = try {
            dir.listFiles()
        } catch (e: Exception) {
            null
        } ?: return null
        var best: SrcRef? = null
        for (f in children) {
            if (f.isDirectory) {
                val nested = newestSafDoc(f, depth + 1)
                if (nested != null && (best == null || nested.modified > best.modified)) best = nested
            } else if (isLog(f.name) && f.length() > 0) {
                if (best == null || f.lastModified() > best.modified) {
                    best = SrcRef(f.name ?: "log", f.length(), f.lastModified(), null, f.uri)
                }
            }
        }
        return best
    }

    private fun isLog(name: String?): Boolean {
        val n = (name ?: return false).lowercase()
        return n.endsWith(".nmea") || n.endsWith(".txt") || n.endsWith(".log") || n.endsWith(".gpx")
    }

    private fun readFrom(src: SrcRef, from: Long, count: Int): ByteArray? = try {
        if (src.file != null) {
            java.io.RandomAccessFile(src.file, "r").use { raf ->
                raf.seek(from)
                val buf = ByteArray(count)
                val read = raf.read(buf)
                if (read <= 0) ByteArray(0) else buf.copyOf(read)
            }
        } else if (src.uri != null) {
            ctx.contentResolver.openInputStream(src.uri)?.use { stream ->
                skipFully(stream, from)
                val buf = ByteArray(count)
                var total = 0
                while (total < count) {
                    val n = stream.read(buf, total, count - total)
                    if (n <= 0) break
                    total += n
                }
                buf.copyOf(total)
            }
        } else null
    } catch (e: Exception) {
        null
    }

    private fun readTail(src: SrcRef, count: Int): String? = try {
        val from = (src.size - count).coerceAtLeast(0L)
        readFrom(src, from, (src.size - from).toInt())?.toString(Charsets.UTF_8)
    } catch (e: Exception) {
        null
    }

    private fun skipFully(stream: java.io.InputStream, offset: Long) {
        var left = offset
        val buf = ByteArray(8192)
        while (left > 0) {
            val n = stream.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
            if (n <= 0) return
            left -= n
        }
    }

    companion object {
        private const val MAX_CHUNK = 64 * 1024L
        private const val TAIL_BYTES = 128 * 1024
    }
}
