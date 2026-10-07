package com.rockbyte.vicu.repo

import android.net.Uri

/** 时长适配策略：截断（以视频为准）/伸缩视频（以音频为准）/伸缩音频（以视频为准）。声明序即 UI 展示序。 */
enum class AudioReplaceMode { TRUNCATE, STRETCH_VIDEO, STRETCH_AUDIO }

sealed interface AudioReplaceError {
    /** 伸缩模式要求两路媒体时长已知且有效。 */
    data object InvalidMedia : AudioReplaceError
    data object TranscodeFailed : AudioReplaceError
    data object OutputCreationFailed : AudioReplaceError
    data object Unknown : AudioReplaceError
}

data class AudioReplaceRequest(
    val videoUri: Uri,
    val displayName: String,
    val musicUri: Uri,
    val mode: AudioReplaceMode,
)

sealed interface AudioReplaceResult {
    data class Success(val outputUri: Uri) : AudioReplaceResult
    data class Failure(val error: AudioReplaceError, val cause: Throwable? = null) : AudioReplaceResult
}

interface AudioReplaceRepo {
    /** 探测视频时长（毫秒）；失败或未知返回 null。 */
    suspend fun probeVideoDurationMs(uri: Uri): Long?
    /** 探测音乐时长（毫秒）；失败或未知返回 null。 */
    suspend fun probeMusicDurationMs(uri: Uri): Long?
    /** 用音乐替换视频音轨并保存到作品库；取消时回滚并传播取消信号。[onProgress] 在替换期间以 0..1 进度回调（FFmpeg 线程）。 */
    suspend fun replace(request: AudioReplaceRequest, onProgress: (Float) -> Unit = {}): AudioReplaceResult
}
