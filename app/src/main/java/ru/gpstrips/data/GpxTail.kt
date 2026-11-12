package ru.gpstrips.data

/**
 * Разбор «хвоста» GPX-файла: нужна последняя точка, а не весь трек.
 * Используется живым слежением, чтобы не перечитывать файл целиком.
 */
object GpxTail {

    private val openTag = Regex("""<trkpt\b([^>]*)>""", RegexOption.IGNORE_CASE)
    private val attr = Regex("""(lat|lon)\s*=\s*"([-\d.]+)"""", RegexOption.IGNORE_CASE)
    private val speedTag = Regex("""<speed>\s*([-\d.]+)\s*</speed>""", RegexOption.IGNORE_CASE)
    private val timeTag = Regex("""<time>\s*([^<]+?)\s*</time>""", RegexOption.IGNORE_CASE)

    fun lastFix(text: String, source: String): LiveFix? {
        val matches = openTag.findAll(text).toList()
        if (matches.isEmpty()) return null
        val tag = matches.last()

        var lat: Double? = null
        var lon: Double? = null
        for (m in attr.findAll(tag.groupValues[1])) {
            when (m.groupValues[1].lowercase()) {
                "lat" -> lat = m.groupValues[2].toDoubleOrNull()
                "lon" -> lon = m.groupValues[2].toDoubleOrNull()
            }
        }
        if (lat == null || lon == null) return null

        val rest = text.substring(tag.range.last + 1)
        val close = rest.indexOf("</trkpt>", ignoreCase = true)
        val inner = if (close >= 0) rest.substring(0, close) else rest

        val speed = speedTag.find(inner)?.groupValues?.get(1)?.toDoubleOrNull()?.times(3.6)
        val time = timeTag.find(inner)?.groupValues?.get(1)?.let { GpxParser.parseTime(it) }
        return LiveFix(lat, lon, speed, time ?: System.currentTimeMillis(), source)
    }
}
