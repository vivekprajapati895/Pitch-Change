package com.pitchplayer.app.export

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Publishes a finished export into the user's own storage so it shows up in
 * their gallery or music player rather than staying locked inside the app.
 */
object MediaStoreWriter {

    const val FOLDER = "PitchPlayer"

    data class Saved(val uri: Uri, val displayName: String, val folder: String)

    suspend fun publish(
        context: Context,
        source: File,
        displayName: String,
        audioOnly: Boolean
    ): Saved = withContext(Dispatchers.IO) {

        val mimeType = if (audioOnly) "audio/mp4" else "video/mp4"
        val topLevel = if (audioOnly) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES
        val relativePath = "$topLevel/$FOLDER"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val collection = if (audioOnly) {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(collection, values)
                ?: throw ExportFailure("Could not create the output file in shared storage.")

            try {
                resolver.openOutputStream(uri, "w")?.use { out ->
                    source.inputStream().use { it.copyTo(out, DEFAULT_BUFFER_SIZE * 8) }
                } ?: throw ExportFailure("Could not write to the output file.")
            } catch (e: Exception) {
                runCatching { resolver.delete(uri, null, null) }
                throw e
            }

            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)

            Saved(uri, displayName, relativePath)
        } else {
            // API 26 to 28 still uses direct filesystem paths.
            val dir = File(Environment.getExternalStoragePublicDirectory(topLevel), FOLDER)
            if (!dir.exists() && !dir.mkdirs()) {
                throw ExportFailure("Could not create the $relativePath folder.")
            }
            val target = File(dir, displayName)
            source.copyTo(target, overwrite = true)
            MediaScannerConnection.scanFile(
                context, arrayOf(target.absolutePath), arrayOf(mimeType), null
            )
            Saved(Uri.fromFile(target), displayName, relativePath)
        }
    }
}
