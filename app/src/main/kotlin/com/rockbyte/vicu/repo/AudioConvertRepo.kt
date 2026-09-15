package com.rockbyte.vicu.repo

import android.net.Uri

/** WAV/FLAC 为无损格式，码率质量档不适用。声明序即 UI 展示序。 */
enum class AudioConvertFormat(val mime: String, val extension: String) {
    MP3("audio/mpeg", "mp3"),
    M4A("audio/mp4", "m4a"),
    WAV("audio/wav", "wav"),
    FLAC("audio/flac", "flac"),
    OGG("audio/ogg", "ogg");

    val lossless: Boolean get() = this == WAV || this == FLAC
}

/** 质量最好/平衡/体积最小为固定码率档（320/192/128 kbps）；最合适按源码率封顶（探测失败回退平衡档 192k）。声明序即 UI 展示序。 */
enum class AudioConvertQuality { SUITABLE, BEST_QUALITY, BALANCED, SMALLEST }

sealed interface AudioConvertError {
    data object TranscodeFailed : AudioConvertError
    data object OutputCreationFailed : AudioConvertError
    data object Unknown : AudioConvertError
}

data class AudioConvertRequest(
    val uri: Uri,
    val displayName: String,
    val format: AudioConvertFormat,
    val quality: AudioConvertQuality,
)

sealed interface AudioConvertResult {
    data class Success(val outputUri: Uri) : AudioConvertResult
    data class Failure(val error: AudioConvertError, val cause: Throwable? = null) : AudioConvertResult
}

interface AudioConvertRepo {
    /** 转码整段音频并发布到媒体库；取消时回滚并传播取消信号。[onProgress] 在转码期间以 0..1 进度回调（FFmpeg 线程）。 */
    suspend fun convert(request: AudioConvertRequest, onProgress: (Float) -> Unit = {}): AudioConvertResult
}
