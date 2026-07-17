package ir.xilo.app.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AvatarCropMathTest {

    @Test
    fun baseScale_fillsShorterSide() {
        val scale = AvatarCropMath.baseScale(bitmapWidth = 400, bitmapHeight = 200, cropDiameter = 200f)
        assertEquals(1f, scale, 0.001f)
    }

    @Test
    fun clampUserScale_boundsZoom() {
        assertEquals(1f, AvatarCropMath.clampUserScale(0.2f), 0.001f)
        assertEquals(4f, AvatarCropMath.clampUserScale(9f), 0.001f)
        assertEquals(2.5f, AvatarCropMath.clampUserScale(2.5f), 0.001f)
    }

    @Test
    fun clampOffset_keepsCircleCovered() {
        val (x, y) = AvatarCropMath.clampOffset(
            offsetX = 10_000f,
            offsetY = -10_000f,
            bitmapWidth = 400,
            bitmapHeight = 400,
            cropDiameter = 200f,
            userScale = 2f,
        )
        // baseScale=0.5, userScale=2 → displayed 400px vs circle 200 → max offset 100.
        assertEquals(100f, x, 0.001f)
        assertEquals(-100f, y, 0.001f)
        assertTrue(x in -100f..100f)
        assertTrue(y in -100f..100f)
    }
}
