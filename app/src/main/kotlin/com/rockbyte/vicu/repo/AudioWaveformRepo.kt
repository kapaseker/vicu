package com.rockbyte.vicu.repo

import android.net.Uri

/** Fixed full-scale peaks; time is measured from the beginning of decoded audio. */
class AudioWaveform(val peaks: FloatArray, val bucketDurationMs: Double, val durationMs: Double)

interface AudioWaveformRepo {
    /** Loads the first audio stream. Cancellation propagates and releases the decoder. */
    suspend fun load(uri: Uri, durationMs: Long): Result<AudioWaveform>
}

internal data class AudioPcmFormat(val sampleRate: Int, val channels: Int)

internal interface AudioWaveformStore {
    /** Delivers interleaved little-endian float PCM, without downmixing or resampling. */
    suspend fun decode(uri: Uri, onFormat: (AudioPcmFormat) -> Unit, onChunk: (ByteArray) -> Unit)
}
