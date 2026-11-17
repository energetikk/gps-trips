package ru.gpstrips.data

/**
 * Кадр с камер 360 приходит от вендорной библиотеки одним буфером YUYV:
 * 5120×800, четыре плитки по 1280×800 — перед, зад, лево, право.
 *
 * Здесь только арифметика: разложить буфер на плитки и перевести YUYV в ARGB.
 * Ничего android-специфичного, поэтому проверяется обычными юнит-тестами.
 */
object Yuyv {

    const val FRAME_WIDTH = 5120
    const val FRAME_HEIGHT = 800
    const val TILE_WIDTH = 1280
    const val TILE_HEIGHT = 800

    /** Два байта на пиксель. */
    const val FRAME_BYTES = FRAME_WIDTH * FRAME_HEIGHT * 2

    /** Порядок плиток в кадре совпадает с порядком камер. */
    const val TILE_BYTES = TILE_WIDTH * TILE_HEIGHT * 2

    /** Смещение плитки в буфере кадра (в байтах). */
    fun tileOffset(index: Int): Int = index * TILE_WIDTH * 2

    /**
     * YUYV → ARGB для прямоугольника внутри буфера.
     *
     * [strideBytes] — длина строки в буфере: у полного кадра это 5120×2, у
     * отдельной плитки можно читать с тем же шагом, а можно и с 1280×2.
     */
    fun toArgb(
        src: ByteArray,
        offset: Int,
        width: Int,
        height: Int,
        strideBytes: Int = FRAME_WIDTH * 2
    ): IntArray {
        require(offset >= 0) { "offset < 0" }
        require(width > 0 && height > 0) { "empty rectangle" }
        require(offset + (height - 1) * strideBytes + width * 2 <= src.size) { "source is too short" }

        val out = IntArray(width * height)
        var p = 0
        for (y in 0 until height) {
            var i = offset + y * strideBytes
            var x = 0
            while (x < width) {
                val y0 = src[i].toInt() and 0xFF
                val u = (src[i + 1].toInt() and 0xFF) - 128
                val y1 = src[i + 2].toInt() and 0xFF
                val v = (src[i + 3].toInt() and 0xFF) - 128

                out[p++] = rgb(y0, u, v)
                if (x + 1 < width) out[p++] = rgb(y1, u, v)

                i += 4
                x += 2
            }
        }
        return out
    }

    /** Коэффициенты BT.601, ограниченный диапазон (16…235). */
    private fun rgb(y: Int, u: Int, v: Int): Int {
        val c = y - 16
        val r = clamp8((298 * c + 409 * v + 128) shr 8)
        val g = clamp8((298 * c - 100 * u - 208 * v + 128) shr 8)
        val b = clamp8((298 * c + 516 * u + 128) shr 8)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun clamp8(value: Int): Int = when {
        value < 0 -> 0
        value > 255 -> 255
        else -> value
    }
}
