package com.pitchplayer.app.core

import kotlin.math.abs
import kotlin.math.pow

/**
 * Pitch shifting is expressed to the user in semitones and to the audio engine
 * as a frequency multiplier.
 *
 * Twelve semitones make an octave, and an octave is exactly a doubling of
 * frequency, so the multiplier for n semitones is 2^(n/12).
 * +1 semitone  -> 1.0595
 * +12 semitones -> 2.0 (one octave up)
 * -12 semitones -> 0.5 (one octave down)
 */
object Pitch {

    const val MIN_SEMITONES = -12
    const val MAX_SEMITONES = 12

    /** Beyond this, time-domain shifting starts to sound noticeably artificial. */
    const val CLEAN_RANGE = 5

    fun factor(semitones: Int): Float = 2.0.pow(semitones / 12.0).toFloat()

    fun factor(semitones: Float): Float = 2.0.pow(semitones / 12.0).toFloat()

    fun label(semitones: Int): String = when {
        semitones > 0 -> "+$semitones"
        else -> semitones.toString()
    }

    fun description(semitones: Int): String = when {
        semitones == 0 -> "Original key"
        abs(semitones) == 12 -> if (semitones > 0) "One octave up" else "One octave down"
        abs(semitones) == 1 -> if (semitones > 0) "1 semitone up" else "1 semitone down"
        else -> "${abs(semitones)} semitones ${if (semitones > 0) "up" else "down"}"
    }

    /** True when the shift is large enough that quality loss becomes audible. */
    fun isStretched(semitones: Int): Boolean = abs(semitones) > CLEAN_RANGE
}
