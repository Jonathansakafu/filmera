package com.filmera.app.core.media

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Capture(
    val file: File,
    val isVideo: Boolean,
    val createdAt: Long,
    val durationMs: Long = 0,
) {
    val name: String get() = file.name
    val mimeType get() = if (isVideo) "video/mp4" else "image/jpeg"
}

/**
 * Captures live in the app's private folder (so the in-app gallery and sharing always
 * work) and can be copied to the phone's shared gallery under "FILMERA".
 */
class CaptureRepository(private val context: Context) {

    private val dir = File(context.filesDir, "captures").apply { mkdirs() }

    private val _captures = MutableStateFlow<List<Capture>>(emptyList())
    val captures: StateFlow<List<Capture>> = _captures

    suspend fun refresh() = withContext(Dispatchers.IO) {
        _captures.value = dir.listFiles().orEmpty()
            .filter { it.length() > 0 && (it.extension == "jpg" || it.extension == "mp4") }
            .sortedByDescending { it.lastModified() }
            .map { f ->
                val video = f.extension == "mp4"
                Capture(f, video, f.lastModified(), if (video) durationOf(f) else 0)
            }
    }

    fun find(name: String): Capture? = _captures.value.firstOrNull { it.name == name }
        ?: File(dir, name).takeIf { it.exists() }?.let {
            Capture(it, it.extension == "mp4", it.lastModified())
        }

    fun newVideoFile(): File = File(dir, "FILMERA_${stamp()}.mp4")

    suspend fun savePhoto(bitmap: Bitmap): Capture = withContext(Dispatchers.IO) {
        val file = File(dir, "FILMERA_${stamp()}.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        Capture(file, false, file.lastModified()).also { refresh() }
    }

    suspend fun delete(capture: Capture) = withContext(Dispatchers.IO) {
        capture.file.delete()
        refresh()
    }

    /** Copies a capture into the shared gallery (Pictures/FILMERA or Movies/FILMERA). */
    suspend fun exportToGallery(capture: Capture): Boolean = withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) exportScoped(capture) else exportLegacy(capture)
            true
        } catch (e: Exception) {
            android.util.Log.e("CaptureRepository", "Export failed", e)
            false
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun exportScoped(capture: Capture) {
        val resolver = context.contentResolver
        val collection = if (capture.isVideo) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val folder = if (capture.isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, capture.name)
            put(MediaStore.MediaColumns.MIME_TYPE, capture.mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "$folder/FILMERA")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: error("MediaStore insert failed")
        try {
            resolver.openOutputStream(uri)?.use { out -> capture.file.inputStream().use { it.copyTo(out) } }
                ?: error("Could not open gallery file")
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }

    @Suppress("DEPRECATION")
    private fun exportLegacy(capture: Capture) {
        val type = if (capture.isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
        val target = File(Environment.getExternalStoragePublicDirectory(type), "FILMERA").apply { mkdirs() }
        val out = File(target, capture.name)
        capture.file.copyTo(out, overwrite = true)
        MediaScannerConnection.scanFile(context, arrayOf(out.absolutePath), arrayOf(capture.mimeType), null)
    }

    private fun durationOf(file: File): Long = try {
        MediaMetadataRetriever().run {
            setDataSource(file.absolutePath)
            val d = extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
            release()
            d
        }
    } catch (e: Exception) {
        0
    }

    private fun stamp() = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
}
