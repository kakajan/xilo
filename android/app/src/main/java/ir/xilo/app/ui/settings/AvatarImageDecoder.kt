package ir.xilo.app.ui.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Loads a gallery / Photo Picker URI into a software [Bitmap] suitable for cropping.
 *
 * Photo Picker URIs are often one-shot streams; opening twice (bounds + decode) fails.
 */
object AvatarImageDecoder {
    private const val TAG = "AvatarImageDecoder"
    private const val MAX_SIDE = 2048

    suspend fun decode(context: Context, uri: Uri, maxSide: Int = MAX_SIDE): Bitmap? {
        // Local cache files from the picker copy path — decode by path first.
        if (uri.scheme == "file") {
            uri.path?.let { path ->
                decodeFileSampled(File(path), maxSide)?.let { return ensureSoftware(it) }
            }
        }
        decodeWithImageDecoder(context, uri, maxSide)?.let { return ensureSoftware(it) }
        decodeWithFileDescriptor(context, uri, maxSide)?.let { return ensureSoftware(it) }
        decodeFromCopiedFile(context, uri, maxSide)?.let { return ensureSoftware(it) }
        decodeFromBytes(context, uri, maxSide)?.let { return ensureSoftware(it) }
        decodeWithCoil(context, uri, maxSide)?.let { return ensureSoftware(it) }
        Log.w(TAG, "Failed to decode avatar uri=$uri mime=${context.contentResolver.getType(uri)}")
        return null
    }

    private fun decodeWithImageDecoder(context: Context, uri: Uri, maxSide: Int): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        return runCatching {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = true
                val longer = max(info.size.width, info.size.height).coerceAtLeast(1)
                if (longer > maxSide) {
                    val scale = maxSide.toFloat() / longer
                    decoder.setTargetSize(
                        (info.size.width * scale).roundToInt().coerceAtLeast(1),
                        (info.size.height * scale).roundToInt().coerceAtLeast(1),
                    )
                }
            }
        }.onFailure { Log.w(TAG, "ImageDecoder failed for $uri", it) }.getOrNull()
    }

    private fun decodeWithFileDescriptor(context: Context, uri: Uri, maxSide: Int): Bitmap? {
        return runCatching {
            // Bounds pass
            val sampleSize = context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor, null, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@use null
                calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxSide)
            } ?: return null

            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor, null, opts)
            }
        }.onFailure { Log.w(TAG, "FileDescriptor decode failed for $uri", it) }.getOrNull()
    }

    private fun decodeFromCopiedFile(context: Context, uri: Uri, maxSide: Int): Bitmap? {
        val cacheFile = runCatching {
            val file = File(context.cacheDir, "avatar_src_${System.nanoTime()}.img")
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            if (file.length() == 0L) {
                file.delete()
                return null
            }
            file
        }.onFailure { Log.w(TAG, "Copy to cache failed for $uri", it) }.getOrNull()
            ?: return null

        return try {
            decodeFileSampled(cacheFile, maxSide)
        } finally {
            cacheFile.delete()
        }
    }

    private fun decodeFromBytes(context: Context, uri: Uri, maxSide: Int): Bitmap? {
        val bytes = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.onFailure { Log.w(TAG, "Read bytes failed for $uri", it) }.getOrNull()
            ?: return null
        if (bytes.isEmpty()) return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val opts = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxSide)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }

    private suspend fun decodeWithCoil(context: Context, uri: Uri, maxSide: Int): Bitmap? {
        return runCatching {
            val loader = ImageLoader.Builder(context).build()
            try {
                val request = ImageRequest.Builder(context)
                    .data(uri)
                    .size(maxSide)
                    .allowHardware(false)
                    .build()
                val result = loader.execute(request)
                if (result is SuccessResult) {
                    result.drawable.toBitmap(config = Bitmap.Config.ARGB_8888)
                } else {
                    null
                }
            } finally {
                loader.shutdown()
            }
        }.onFailure { Log.w(TAG, "Coil decode failed for $uri", it) }.getOrNull()
    }

    private fun decodeFileSampled(file: File, maxSide: Int): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching {
                val source = ImageDecoder.createSource(file)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = true
                    val longer = max(info.size.width, info.size.height).coerceAtLeast(1)
                    if (longer > maxSide) {
                        val scale = maxSide.toFloat() / longer
                        decoder.setTargetSize(
                            (info.size.width * scale).roundToInt().coerceAtLeast(1),
                            (info.size.height * scale).roundToInt().coerceAtLeast(1),
                        )
                    }
                }
            }.getOrNull()?.let { return it }
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val opts = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxSide)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(file.absolutePath, opts)
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxSide: Int): Int {
        var sample = 1
        val longer = max(width, height).coerceAtLeast(1)
        while (longer / sample > maxSide) {
            sample *= 2
        }
        return sample
    }

    private fun ensureSoftware(bitmap: Bitmap): Bitmap {
        if (bitmap.config != Bitmap.Config.HARDWARE) return bitmap
        val copy = bitmap.copy(Bitmap.Config.ARGB_8888, true) ?: return bitmap
        bitmap.recycle()
        return copy
    }
}
