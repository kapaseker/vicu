package com.rockbyte.vicu.repo

import android.net.Uri

/** 导出音频的目标格式。 */
enum class AudioExportFormat(val mime: String, val extension: String) {
    /** 原声：最佳档直拷音频流（手机视频通常为 AAC），容器固定 m4a。 */
    ORIGINAL("audio/mp4", "m4a"),
    MP3("audio/mpeg", "mp3"),
    M4A("audio/mp4", "m4a"),
}

/** 导出质量档位。 */
enum class AudioExportQuality { BEST, HIGH, MEDIUM, LOW }

/** FFprobe 探测到的源音频流信息；字段可能缺失。 */
data class SourceAudioInfo(val codec: String?, val bitrateKbps: Int?)

/** FFmpeg 编码目标：直拷音频流或按码率转码。 */
sealed interface EncodeTarget {
    data object Copy : EncodeTarget
    data class Bitrate(val kbps: Int) : EncodeTarget
}

private const val MAX_TRANSCODE_KBPS = 320

/** 源码率已知时钳制到不超过源；未知则原样返回。 */
private fun Int.clampedTo(source: SourceAudioInfo?): Int =
    source?.bitrateKbps?.let { minOf(this, it) } ?: this

/**
 * 质量算法（用户决策）：
 * - 最佳：原声且源为 AAC（或探测失败）时直拷；否则以 min(源码率, 320) 转码，源未知取 320。
 * - 高/中/低：固定 320/192/128 kb/s，源码率更低时钳制到源码率。
 */
fun resolveTarget(
    format: AudioExportFormat,
    quality: AudioExportQuality,
    source: SourceAudioInfo?,
): EncodeTarget = when {
    format == AudioExportFormat.ORIGINAL && quality == AudioExportQuality.BEST &&
        (source?.codec == null || source.codec == "aac") -> EncodeTarget.Copy

    quality == AudioExportQuality.BEST -> EncodeTarget.Bitrate(
        (source?.bitrateKbps ?: MAX_TRANSCODE_KBPS).coerceAtMost(MAX_TRANSCODE_KBPS)
    )

    else -> EncodeTarget.Bitrate(
        when (quality) {
            AudioExportQuality.HIGH -> 320
            AudioExportQuality.MEDIUM -> 192
            AudioExportQuality.LOW -> 128
            AudioExportQuality.BEST -> error("已在上方分支处理")
        }.clampedTo(source)
    )
}

/** 视频导出音频（domain-facing 数据操作）。 */
interface AudioExportRepo {
    /** 探测视频首个音频流的编码与码率；无法探测时返回 null。 */
    suspend fun probeAudio(uri: Uri): SourceAudioInfo?

    /** 按格式与质量导出音频到媒体库 Music/FFmpegKitNext，返回输出 Uri；失败抛异常。 */
    suspend fun export(
        uri: Uri,
        displayName: String,
        format: AudioExportFormat,
        quality: AudioExportQuality,
    ): Uri
}
