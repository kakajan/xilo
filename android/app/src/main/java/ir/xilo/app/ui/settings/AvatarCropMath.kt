package ir.xilo.app.ui.settings

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.max
import kotlin.math.min

/**
 * Geometry helpers for Telegram-style circular avatar cropping.
 *
 * The source image is scaled so that at [userScale] = 1 its shorter side exactly
 * fills the crop circle diameter, then the user can pinch-zoom and pan.
 */
object AvatarCropMath {
    const val OUTPUT_SIZE = 512
    private const val MIN_USER_SCALE = 1f
    private const val MAX_USER_SCALE = 4f

    fun baseScale(bitmapWidth: Int, bitmapHeight: Int, cropDiameter: Float): Float {
        val shorter = min(bitmapWidth, bitmapHeight).toFloat().coerceAtLeast(1f)
        return cropDiameter / shorter
    }

    fun clampUserScale(scale: Float): Float = scale.coerceIn(MIN_USER_SCALE, MAX_USER_SCALE)

    /**
     * Keeps the crop circle fully covered by the transformed image.
     */
    fun clampOffset(
        offsetX: Float,
        offsetY: Float,
        bitmapWidth: Int,
        bitmapHeight: Int,
        cropDiameter: Float,
        userScale: Float,
    ): Pair<Float, Float> {
        val displayScale = baseScale(bitmapWidth, bitmapHeight, cropDiameter) * clampUserScale(userScale)
        val displayedW = bitmapWidth * displayScale
        val displayedH = bitmapHeight * displayScale
        val maxX = max(0f, (displayedW - cropDiameter) / 2f)
        val maxY = max(0f, (displayedH - cropDiameter) / 2f)
        return offsetX.coerceIn(-maxX, maxX) to offsetY.coerceIn(-maxY, maxY)
    }

    fun renderSquareCrop(
        source: Bitmap,
        cropDiameter: Float,
        userScale: Float,
        offsetX: Float,
        offsetY: Float,
        outputSize: Int = OUTPUT_SIZE,
    ): Bitmap {
        val scale = clampUserScale(userScale)
        val (ox, oy) = clampOffset(
            offsetX,
            offsetY,
            source.width,
            source.height,
            cropDiameter,
            scale,
        )
        val displayScale = baseScale(source.width, source.height, cropDiameter) * scale
        val srcSize = cropDiameter / displayScale
        val srcLeft = (source.width / 2f) - (srcSize / 2f) - (ox / displayScale)
        val srcTop = (source.height / 2f) - (srcSize / 2f) - (oy / displayScale)

        val output = Bitmap.createBitmap(outputSize, outputSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        val src = RectF(srcLeft, srcTop, srcLeft + srcSize, srcTop + srcSize)
        val dst = RectF(0f, 0f, outputSize.toFloat(), outputSize.toFloat())
        val matrix = Matrix().apply { setRectToRect(src, dst, Matrix.ScaleToFit.FILL) }
        canvas.drawBitmap(source, matrix, paint)
        return output
    }
}
