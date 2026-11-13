package ru.gpstrips.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.time.Instant
import java.time.ZoneId

/**
 * Читает логи из папки, разбирает их и складывает готовые поездки в кэш.
 *
 * Поддерживаются два способа доступа:
 *  - путь файловой системы ("/storage/emulated/0/GPSLogs") — нужен на магнитолах,
 *    где нет системного выбора папок;
 *  - SAF-URI ("content://...") — обычный выбор папки на телефоне.
 *
 * Кэш пересобирается только когда изменился набор файлов (имя/размер/дата).
 */
class LogRepository(private val ctx: Context) {

    data class Loaded(
        val trips: List<Trip>,
        val files: Int,
        val fromCache: Boolean,
        val message: String? = null
    )

    private data class Src(
        val name: String,
        val size: Long,
        val modified: Long,
        val open: () -> InputStream?
    )

    private val cacheFile = File(ctx.filesDir, "trips_cache.json")

    fun load(target: String, force: Boolean): Loaded {
        val sources = (if (target.startsWith("/")) fileSources(File(target)) else safSources(target))
            ?: return Loaded(emptyList(), 0, false, "Нет доступа к папке — выберите её заново")
        if (sources.isEmpty()) {
            return Loaded(emptyList(), 0, false, "В папке нет файлов .nmea / .txt / .log / .gpx")
        }

        val sig = signature(sources)
        if (!force) {
            readCache(sig)?.let { return Loaded(it, sources.size, true) }
        }

        val trips = ArrayList<Trip>()
        for (src in sources) {
            val text = try {
                src.open()?.use { it.bufferedReader(Charsets.UTF_8).readText() }
            } catch (e: Exception) {
                null
            }
            if (text.isNullOrBlank()) continue

            val date = Instant
                .ofEpochMilli(if (src.modified > 0) src.modified else System.currentTimeMillis())
                .atZone(ZoneId.systemDefault())
                .toLocalDate()

            val parsed = when {
                GpxParser.looksLikeGpx(text) -> GpxParser.parse(text, date)
                NmeaParser.looksLikeNmea(text) -> NmeaParser.parse(text, date) to true
                else -> emptyList<TrackPoint>() to true
            }
            trips += TripBuilder.build(parsed.first, src.name, parsed.second)
        }

        val sorted = trips.sortedByDescending { it.startMs }
        runCatching { writeCache(sig, sorted) }
        val msg = if (sorted.isEmpty()) "Поездок не найдено (нужно минимум 300 м движения)" else null
        return Loaded(sorted, sources.size, false, msg)
    }

    // ---------- источники ----------

    private fun fileSources(dir: File): List<Src>? {
        if (!dir.isDirectory || !dir.canRead()) return null
        return walk(dir, 0)
    }

    private fun walk(dir: File, depth: Int): List<Src> {
        if (depth > 3) return emptyList()
        val out = ArrayList<Src>()
        val files = try {
            dir.listFiles()
        } catch (e: Exception) {
            null
        } ?: return out
        for (f in files) {
            if (f.isDirectory) {
                out += walk(f, depth + 1)
            } else if (isLog(f.name)) {
                out += Src(f.name, f.length(), f.lastModified()) { f.inputStream() }
            }
        }
        return out
    }

    private fun safSources(uri: String): List<Src>? {
        val root = try {
            DocumentFile.fromTreeUri(ctx, Uri.parse(uri))
        } catch (e: Exception) {
            null
        } ?: return null
        if (!root.canRead()) return null
        return safWalk(root, 0)
    }

    private fun safWalk(dir: DocumentFile, depth: Int): List<Src> {
        if (depth > 3) return emptyList()
        val out = ArrayList<Src>()
        val children = try {
            dir.listFiles()
        } catch (e: Exception) {
            null
        } ?: emptyArray<DocumentFile>()
        for (f in children) {
            if (f.isDirectory) {
                out += safWalk(f, depth + 1)
            } else if (isLog(f.name)) {
                val u = f.uri
                out += Src(f.name ?: "log", f.length(), f.lastModified()) {
                    ctx.contentResolver.openInputStream(u)
                }
            }
        }
        return out
    }

    private fun isLog(name: String?): Boolean {
        val n = (name ?: return false).lowercase()
        return n.endsWith(".nmea") || n.endsWith(".txt") || n.endsWith(".log") || n.endsWith(".gpx")
    }

    private fun signature(srcs: List<Src>): String {
        val sb = StringBuilder()
        for (s in srcs.sortedBy { it.name }) {
            sb.append(s.name).append(':').append(s.size).append(':').append(s.modified).append('|')
        }
        return Integer.toHexString(sb.toString().hashCode()) + "-" + srcs.size
    }

    // ---------- кэш ----------

    private fun readCache(sig: String): List<Trip>? = try {
        if (!cacheFile.exists()) null else {
            val root = JSONObject(cacheFile.readText())
            if (root.optInt("v", 1) != 2) null
            else if (root.optString("sig") != sig) null else {
                val arr = root.optJSONArray("trips") ?: JSONArray()
                val trips = ArrayList<Trip>(arr.length())
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val pts = ArrayList<TrackPoint>()
                    val pa = o.optJSONArray("track") ?: JSONArray()
                    for (j in 0 until pa.length()) {
                        val p = pa.getJSONArray(j)
                        val v = p.optDouble(3, -1.0)
                        val sats = p.optInt(4, -1)
                        val hdop = p.optDouble(5, -1.0)
                        val alt = p.optDouble(6, -99999.0)
                        pts.add(
                            TrackPoint(
                                timeMs = p.optLong(0),
                                lat = p.optDouble(1),
                                lon = p.optDouble(2),
                                speedKmh = if (v >= 0) v else null,
                                altM = if (alt <= -99999.0) null else alt,
                                sats = if (sats >= 0) sats else null,
                                hdop = if (hdop >= 0) hdop else null
                            )
                        )
                    }
                    trips.add(
                        Trip(
                            id = o.optString("id"),
                            source = o.optString("src"),
                            startMs = o.optLong("start"),
                            endMs = o.optLong("end"),
                            distanceM = o.optDouble("dist"),
                            movingSec = o.optLong("moving"),
                            maxSpeedKmh = o.optDouble("max"),
                            avgSpeedKmh = o.optDouble("avg"),
                            timeReliable = o.optBoolean("rel", true),
                            track = pts
                        )
                    )
                }
                trips
            }
        }
    } catch (e: Exception) {
        null
    }

    private fun writeCache(sig: String, trips: List<Trip>) {
        val root = JSONObject()
        root.put("v", 2)
        root.put("sig", sig)
        val arr = JSONArray()
        for (t in trips) {
            val o = JSONObject()
            o.put("id", t.id)
            o.put("src", t.source)
            o.put("start", t.startMs)
            o.put("end", t.endMs)
            o.put("dist", t.distanceM)
            o.put("moving", t.movingSec)
            o.put("max", t.maxSpeedKmh)
            o.put("avg", t.avgSpeedKmh)
            o.put("rel", t.timeReliable)
            val pts = JSONArray()
            for (p in t.track) {
                pts.put(
                    JSONArray()
                        .put(p.timeMs)
                        .put(p.lat)
                        .put(p.lon)
                        .put(p.speedKmh ?: -1.0)
                        .put(p.sats ?: -1)
                        .put(p.hdop ?: -1.0)
                        .put(p.altM ?: -99999.0)
                )
            }
            o.put("track", pts)
            arr.put(o)
        }
        root.put("trips", arr)
        cacheFile.writeText(root.toString())
    }
}
