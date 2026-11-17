package ru.gpstrips

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.gpstrips.data.Camera

class CameraRequestTest {

    @Test
    fun parsesRequestTime() {
        assertEquals(1234L, Camera.requested("""{"ok":true,"camera":1234}"""))
        assertEquals(1794522347168L, Camera.requested("""{"ok": true, "camera": 1794522347168}"""))
    }

    @Test
    fun zeroMeansNoRequest() {
        assertEquals(0L, Camera.requested("""{"ok":true,"camera":0}"""))
    }

    @Test
    fun toleratesUnexpectedBody() {
        assertEquals(0L, Camera.requested(""))
        assertEquals(0L, Camera.requested("<html>502</html>"))
        assertEquals(0L, Camera.requested("""{"camera":"soon"}"""))
    }
}
