package ir.xilo.app.ui.settings

import android.content.Context
import android.net.Uri
import java.io.File

/** Copy a Photo Picker URI into app cache while the one-shot grant is still valid. */
fun copyPickedImageToCache(context: Context, source: Uri): Uri? {
    return runCatching {
        val dir = File(context.cacheDir, "avatar_crop").apply { mkdirs() }
        val dest = File(dir, "source_${System.nanoTime()}.img")
        context.contentResolver.openInputStream(source)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        if (dest.length() == 0L) {
            dest.delete()
            return null
        }
        Uri.fromFile(dest)
    }.getOrNull()
}

fun deleteCachedCropSource(context: Context, uri: Uri) {
    if (uri.scheme != "file") return
    val path = uri.path ?: return
    val cacheRoot = File(context.cacheDir, "avatar_crop").canonicalFile
    val file = File(path).canonicalFile
    if (file.path.startsWith(cacheRoot.path)) {
        file.delete()
    }
}
