package com.rockbyte.vicu.repo

import android.net.Uri

/** Source container determines the output; stream copying preserves the original audio codec. */
enum class AudioTrimFormat(val mime: String, val extension: String, val muxer: String) {
    MP3("audio/mpeg", "mp3", "mp3"),
    M4A("audio/mp4", "m4a", "ipod"),
    WAV("audio/wav", "wav", "wav"),
    FLAC("audio/flac", "flac", "flac"),
    OGG("audio/ogg", "ogg", "ogg"),
}

data class AudioTrimInfo(val durationMs: Long, val format: AudioTrimFormat, val codec: String)

enum class AudioTrimError {
    ProbeFailed, UnsupportedFormat, InvalidRange, OutputCreationFailed, TrimFailed, OutputPublicationFailed,
}

sealed interface AudioTrimProbeResult {
    data class Success(val info: AudioTrimInfo) : AudioTrimProbeResult
    data class Failure(val error: AudioTrimError) : AudioTrimProbeResult
}

data class AudioTrimRequest(val uri: Uri, val displayName: String, val startMs: Long, val endMs: Long)

sealed interface AudioTrimResult {
    data class Success(val outputUri: Uri) : AudioTrimResult
    data class Failure(val error: AudioTrimError, val cause: Throwable? = null) : AudioTrimResult
}

interface AudioTrimRepo {
    suspend fun inspect(uri: Uri): AudioTrimProbeResult
    /** Copies the first audio stream. Cut points follow packet boundaries, not millisecond precision. */
    suspend fun trim(request: AudioTrimRequest, onProgress: (Float) -> Unit = {}): AudioTrimResult
}

internal interface AudioTrimStore {
    fun create(inputName: String, format: AudioTrimFormat): Uri
    fun finalizeFlac(uri: Uri, packets: List<AudioPacketInfo>)
    fun publish(uri: Uri)
    fun delete(uri: Uri)
}
