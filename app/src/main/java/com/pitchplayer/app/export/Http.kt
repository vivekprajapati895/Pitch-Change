package com.pitchplayer.app.export

import com.pitchplayer.app.media.NewPipeDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

object Http {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    /**
     * Streams a URL to disk, reporting 0..1 progress.
     *
     * Google's media hosts sometimes throttle or refuse plain GETs without a
     * Range header, so one is always sent.
     */
    suspend fun download(
        url: String,
        target: File,
        onProgress: (Float) -> Unit
    ): File = withContext(Dispatchers.IO) {

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", NewPipeDownloader.USER_AGENT)
            .header("Range", "bytes=0-")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw ExportFailure(
                    "Download failed with code ${response.code}. " +
                        "YouTube stream links expire, so reload the video and try again."
                )
            }
            val body = response.body ?: throw ExportFailure("Empty response from server.")
            val total = body.contentLength()

            target.outputStream().buffered(DEFAULT_BUFFER_SIZE * 8).use { out ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 8)
                    var written = 0L
                    var lastReported = -1
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        out.write(buffer, 0, read)
                        written += read
                        if (total > 0) {
                            val pct = ((written * 100) / total).toInt()
                            if (pct != lastReported) {
                                lastReported = pct
                                onProgress(pct / 100f)
                            }
                        }
                    }
                }
            }
        }
        onProgress(1f)
        target
    }
}

class ExportFailure(message: String, cause: Throwable? = null) : Exception(message, cause)
