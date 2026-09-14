package com.rockbyte.vicu.repo

import android.net.Uri

enum class AudioExportFormat(val mime: String, val extension: String) {
    ORIGINAL("audio/mp4", "m4a"),
    MP3("audio/mpeg", "mp3"),
    M4A("audio/mp4", "m4a"),
}

enum class AudioExportQuality { BEST, HIGH, MEDIUM, LOW }

sealed interface AudioExportError {
    data object TranscodeFailed : AudioExportError
    data object OutputCreationFailed : AudioExportError
    data object Unknown : AudioExportError
}

data class AudioExportRequest(
    val uri: Uri,
    val displayName: String,
    val format: AudioExportFormat,
    val quality: AudioExportQuality,
)

sealed interface AudioExportResult {
    data class Success(val outputUri: Uri) : AudioExportResult
    data class Failure(val error: AudioExportError, val cause: Throwable? = null) : AudioExportResult
}

interface AudioExportRepo {
    /** 导出首个音频流并发布到媒体库；取消时回滚并传播取消信号。 */
    suspend fun export(request: AudioExportRequest): AudioExportResult
}
