package com.rockbyte.vicu.page.audiotrim

import com.rockbyte.vicu.repo.AudioWaveform
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

/** Decoded audio can outlast format metadata; selection validation still uses the source duration. */
internal fun waveformExtentMs(waveform: AudioWaveform, sourceDurationMs: Long): Long =
    max(sourceDurationMs, ceil(waveform.durationMs).toLong()).coerceAtLeast(1)

/** Page-local viewing state; changing it never changes the selected interval. */
internal data class WaveformViewport(val startMs: Double, val spanMs: Double, val following: Boolean = true) {
    val endMs: Double get() = startMs + spanMs
    fun timeAt(fraction: Double): Double = startMs + fraction.coerceIn(0.0, 1.0) * spanMs
    fun fractionAt(timeMs: Long): Double = (timeMs - startMs) / spanMs

    fun zoom(factor: Double, anchor: Double, durationMs: Long): WaveformViewport {
        val span = (spanMs / factor.coerceAtLeast(0.01)).coerceIn(minOf(1000.0, durationMs.toDouble()), durationMs.toDouble())
        val fraction = anchor.coerceIn(0.0, 1.0)
        return WaveformViewport((timeAt(fraction) - fraction * span).coerceIn(0.0, durationMs - span), span, false)
    }

    fun pan(fraction: Double, durationMs: Long): WaveformViewport =
        copy(startMs = (startMs - fraction * spanMs).coerceIn(0.0, durationMs - spanMs), following = false)

    fun locate(positionMs: Long, durationMs: Long): WaveformViewport =
        copy(startMs = (positionMs - spanMs / 2).coerceIn(0.0, durationMs - spanMs), following = true)

    fun follow(positionMs: Long, playing: Boolean, durationMs: Long): WaveformViewport {
        if (!following || !playing || (positionMs >= startMs && positionMs < endMs)) return this
        return copy(startMs = (floor(positionMs / spanMs) * spanMs).coerceIn(0.0, durationMs - spanMs))
    }

    companion object {
        fun full(durationMs: Long): WaveformViewport = WaveformViewport(0.0, durationMs.coerceAtLeast(1).toDouble())
    }
}

/** null means a background drag. Ties wait until horizontal direction is known. */
internal fun waveformHandleAt(x: Float, startX: Float, endX: Float, radius: Float, direction: Float, width: Float): Boolean? {
    val startDistance = if (startX in 0f..width) abs(x - startX) else Float.POSITIVE_INFINITY
    val endDistance = if (endX in 0f..width) abs(x - endX) else Float.POSITIVE_INFINITY
    if (minOf(startDistance, endDistance) > radius) return null
    return when {
        startDistance < endDistance -> true
        endDistance < startDistance -> false
        direction < 0 -> true
        direction > 0 -> false
        else -> null
    }
}

/** Reduce visible buckets to display columns without averaging away short peaks. */
internal fun waveformBars(waveform: AudioWaveform, viewport: WaveformViewport, columns: Int): FloatArray =
    FloatArray(columns.coerceAtLeast(0)) { column ->
        val from = floor(viewport.timeAt(column.toDouble() / columns) / waveform.bucketDurationMs).toInt().coerceAtLeast(0)
        val to = ceil(viewport.timeAt((column + 1.0) / columns) / waveform.bucketDurationMs).toInt().coerceAtMost(waveform.peaks.size)
        var peak = 0f
        for (i in from until to) peak = max(peak, waveform.peaks[i])
        peak
    }
