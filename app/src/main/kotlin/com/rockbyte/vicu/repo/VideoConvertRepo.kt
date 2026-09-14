package com.rockbyte.vicu.repo

import android.net.Uri

enum class VideoConvertFormat(val mime: String, val extension: String) {
    MP4("video/mp4", "mp4"),
    MKV("video/x-matroska", "mkv"),
    WEBM("video/webm", "webm"),
    AVI("video/x-msvideo", "avi"),
    MOV("video/quicktime", "mov"),
}

/** 质量最好/平衡/体积最小为固定 crf 档；最合适依据源码率封顶（探测失败回退平衡档）。声明序即 UI 展示序。 */
enum class VideoConvertQuality { SUITABLE, BEST_QUALITY, BALANCED, SMALLEST }

sealed interface VideoConvertError {
    data object TranscodeFailed : VideoConvertError
    data object OutputCreationFailed : VideoConvertError
    data object Unknown : VideoConvertError
}

data class VideoConvertRequest(
    val uri: Uri,
    val displayName: String,
    val format: VideoConvertFormat,
    val quality: VideoConvertQuality,
)

sealed interface VideoConvertResult {
    data class Success(val outputUri: Uri) : VideoConvertResult
    data class Failure(val error: VideoConvertError, val cause: Throwable? = null) : VideoConvertResult
}

interface VideoConvertRepo {
    /** 转码整段视频并发布到媒体库；取消时回滚并传播取消信号。[onProgress] 在转码期间以 0..1 进度回调（FFmpeg 线程）。 */
    suspend fun convert(request: VideoConvertRequest, onProgress: (Float) -> Unit = {}): VideoConvertResult
}
