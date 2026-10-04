package com.rockbyte.vicu.repo

import android.net.Uri
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal class AudioWaveformRepository(private val store: AudioWaveformStore) : AudioWaveformRepo {
    override suspend fun load(uri: Uri, durationMs: Long): Result<AudioWaveform> = try {
        withContext(Dispatchers.IO) {
            val context = currentCoroutineContext()
            var peaks: AudioPeakAccumulator? = null
            store.decode(uri, onFormat = { peaks = AudioPeakAccumulator(it, durationMs) }, onChunk = { bytes ->
                context.ensureActive()
                checkNotNull(peaks).append(bytes)
            })
            currentCoroutineContext().ensureActive()
            Result.success(checkNotNull(peaks).finish())
        }
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        Result.failure(error)
    }
}

/** Bounded peak storage, including files whose metadata underestimates their duration. */
private class AudioPeakAccumulator(private val format: AudioPcmFormat, durationMs: Long) {
    private val peaks = FloatArray(360000)
    private var bucketFrames: Long
    private var frameCount = 0L
    private var count = 0
    private var sampleBits = 0
    private var sampleBytes = 0
    private var channel = 0
    private var framePeak = 0f

    init {
        require(format.sampleRate > 0 && format.channels > 0 && durationMs > 0)
        bucketFrames = max(ceil(format.sampleRate / 100.0).toLong(),
            ceil(durationMs.toDouble() * format.sampleRate / 1000 / peaks.size).toLong()).coerceAtLeast(1)
    }

    fun append(bytes: ByteArray) {
        for (byte in bytes) {
            sampleBits = sampleBits or ((byte.toInt() and 0xff) shl (sampleBytes * 8))
            if (++sampleBytes != 4) continue
            val value = Float.fromBits(sampleBits)
            if (value.isFinite()) framePeak = max(framePeak, abs(value).coerceAtMost(1f))
            sampleBits = 0; sampleBytes = 0
            if (++channel != format.channels) continue
            if (frameCount / bucketFrames >= peaks.size) {
                // Pairwise maximum preserves every peak when the decoded tail exceeds metadata.
                for (i in 0 until peaks.size / 2) peaks[i] = max(peaks[i * 2], peaks[i * 2 + 1])
                count = peaks.size / 2
                peaks.fill(0f, count)
                bucketFrames *= 2
            }
            val index = (frameCount / bucketFrames).toInt()
            peaks[index] = max(peaks[index], framePeak)
            count = max(count, index + 1)
            frameCount++; channel = 0; framePeak = 0f
        }
    }

    fun finish(): AudioWaveform {
        check(sampleBytes == 0 && channel == 0 && frameCount > 0) { "Incomplete or empty PCM" }
        return AudioWaveform(peaks.copyOf(count), bucketFrames * 1000.0 / format.sampleRate,
            frameCount * 1000.0 / format.sampleRate)
    }
}
