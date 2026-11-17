package ru.gpstrips

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.gpstrips.data.Yuyv

class YuyvTest {

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun whiteFrameBecomesWhite() {
        val src = bytes(235, 128, 235, 128)
        val argb = Yuyv.toArgb(src, 0, 2, 1, strideBytes = 4)
        assertEquals(0xFFFFFFFF.toInt(), argb[0])
        assertEquals(0xFFFFFFFF.toInt(), argb[1])
    }

    @Test
    fun blackFrameBecomesBlack() {
        val src = bytes(16, 128, 16, 128)
        val argb = Yuyv.toArgb(src, 0, 2, 1, strideBytes = 4)
        assertEquals(0xFF000000.toInt(), argb[0])
        assertEquals(0xFF000000.toInt(), argb[1])
    }

    @Test
    fun redFrameBecomesRed() {
        val src = bytes(81, 90, 81, 240)
        val argb = Yuyv.toArgb(src, 0, 2, 1, strideBytes = 4)
        assertEquals(0xFFFF0000.toInt(), argb[0])
    }

    @Test
    fun secondRowIsReadWithStride() {
        // две строки по два пикселя, шаг 8 байт
        val src = bytes(
            16, 128, 16, 128, 0, 0, 0, 0,
            235, 128, 235, 128
        )
        val argb = Yuyv.toArgb(src, 0, 2, 2, strideBytes = 8)
        assertEquals(0xFF000000.toInt(), argb[0])
        assertEquals(0xFFFFFFFF.toInt(), argb[2])
    }

    @Test
    fun tilesSitSideBySide() {
        assertEquals(0, Yuyv.tileOffset(0))
        assertEquals(1280 * 2, Yuyv.tileOffset(1))
        assertEquals(2 * 1280 * 2, Yuyv.tileOffset(2))
        assertEquals(3 * 1280 * 2, Yuyv.tileOffset(3))
    }

    @Test
    fun frameSizeMatchesVendorFormat() {
        assertEquals(5120 * 800 * 2, Yuyv.FRAME_BYTES)
        assertEquals(1280 * 800 * 2, Yuyv.TILE_BYTES)
    }

    @Test(expected = IllegalArgumentException::class)
    fun shortSourceIsRejected() {
        Yuyv.toArgb(bytes(235, 128, 235, 128), 0, 2, 2, strideBytes = 4)
    }
}
