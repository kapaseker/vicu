package com.rockbyte.vicu.repo

import android.net.Uri

enum class AudioExportFormat(val mime: String, val extension: String) {
    ORIGINAL("audio/mp4", "m4a"),
    MP3("audio/mpeg", "mp3"),
    M4A("audio/mp4", "m4a"),
}

/** 质量最好/平衡/体积最小为固定码率档（320/192/128 kbps）；最合适按源码率封顶（探测失败回退平衡档 192k）。声明序即 UI 展示序。 */
enum class AudioExportQuality { SUITABLE, BEST_QUALITY, BALANCED, SMALLEST }

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
    /** 导出首个音频流并发布到媒体库；取消时回滚并传播取消信号。[onProgress] 在导出期间以 0..1 进度回调（FFmpeg 线程）。 */
    suspend fun export(request: AudioExportRequest, onProgress: (Float) -> Unit = {}): AudioExportResult
}
