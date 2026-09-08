package com.pitchplayer.app.core

import java.util.Locale
import java.util.concurrent.TimeUnit

fun Long.asDuration(): String {
    if (this <= 0L) return "0:00"
    val h = TimeUnit.MILLISECONDS.toHours(this)
    val m = TimeUnit.MILLISECONDS.toMinutes(this) % 60
    val s = TimeUnit.MILLISECONDS.toSeconds(this) % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%d:%02d", m, s)
}

fun Long.asFileSize(): String {
    if (this <= 0L) return "--"
    val mb = this / 1_048_576.0
    return if (mb >= 1024) String.format(Locale.US, "%.2f GB", mb / 1024)
    else String.format(Locale.US, "%.1f MB", mb)
}

/** Strips characters that are illegal in filenames on Android's FAT-derived volumes. */
fun String.asSafeFileName(maxLength: Int = 90): String {
    val cleaned = this
        .replace(Regex("""[\\/:*?"<>|\n\r\t]"""), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .ifEmpty { "audio" }
    return if (cleaned.length <= maxLength) cleaned else cleaned.take(maxLength).trim()
}
