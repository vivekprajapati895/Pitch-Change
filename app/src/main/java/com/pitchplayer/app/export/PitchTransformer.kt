package com.pitchplayer.app.export

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.pitchplayer.app.core.Pitch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Renders a pitch-shifted AAC audio file.
 *
 * Sonic is the same processor ExoPlayer uses for live playback, so what the
 * user hears while previewing is what lands in the exported file. Speed is
 * deliberately left at 1.0: changing tempo would desynchronise the video track
 * that gets muxed back in afterwards.
 */
@UnstableApi
object PitchTransformer {

    suspend fun renderAudio(
        context: Context,
        input: Uri,
        semitones: Int,
        output: File,
        onProgress: (Float) -> Unit
    ): Unit = suspendCancellableCoroutine { cont ->

        val main = Handler(Looper.getMainLooper())

        main.post {
            if (!cont.isActive) return@post

            val transformer = try {
                val sonic = SonicAudioProcessor().apply {
                    setPitch(Pitch.factor(semitones))
                    setSpeed(1f)
                }

                val item = EditedMediaItem.Builder(MediaItem.fromUri(input))
                    .setRemoveVideo(true)
                    .setEffects(Effects(listOf(sonic), emptyList()))
                    .build()

                Transformer.Builder(context)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, result: ExportResult) {
                            onProgress(1f)
                            if (cont.isActive) cont.resume(Unit)
                        }

                        override fun onError(
                            composition: Composition,
                            result: ExportResult,
                            exception: ExportException
                        ) {
                            if (cont.isActive) {
                                cont.resumeWithException(
                                    ExportFailure(
                                        "Audio rendering failed (${exception.errorCode}). " +
                                            "The source format may not be supported on this device.",
                                        exception
                                    )
                                )
                            }
                        }
                    })
                    .build()
                    .also { it.start(item, output.absolutePath) }
            } catch (e: Throwable) {
                if (cont.isActive) cont.resumeWithException(e)
                return@post
            }

            // Transformer only exposes progress by polling.
            val holder = ProgressHolder()
            val poll = object : Runnable {
                override fun run() {
                    if (!cont.isActive) return
                    val state = transformer.getProgress(holder)
                    if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                        onProgress(holder.progress / 100f)
                    }
                    if (state != Transformer.PROGRESS_STATE_NOT_STARTED) {
                        main.postDelayed(this, 250)
                    }
                }
            }
            main.postDelayed(poll, 250)

            cont.invokeOnCancellation {
                main.post {
                    main.removeCallbacks(poll)
                    runCatching { transformer.cancel() }
                }
            }
        }
    }
}
