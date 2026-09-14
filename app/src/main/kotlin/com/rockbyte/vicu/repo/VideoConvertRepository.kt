package com.rockbyte.vicu.repo

import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal class VideoConvertRepository(
    private val converter: VideoConverter,
    private val outputStore: VideoOutputStore,
) : VideoConvertRepo {
    override suspend fun convert(
        request: VideoConvertRequest,
        onProgress: (Float) -> Unit,
    ): VideoConvertResult {
        var output: Uri? = null
        var failureType: VideoConvertError = VideoConvertError.OutputCreationFailed
        try {
            return withContext(Dispatchers.IO) {
                currentCoroutineContext().ensureActive()
                val destination = outputStore.create(request.displayName, request.format)
                output = destination
                failureType = VideoConvertError.Unknown
                currentCoroutineContext().ensureActive()
                val source = try {
                    converter.probe(request.uri)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
                currentCoroutineContext().ensureActive()
                val arguments = converterArguments(
                    request, source, converter.inputUrl(request.uri), converter.outputUrl(destination),
                )
                failureType = VideoConvertError.TranscodeFailed
                var lastPermille = -1
                check(
                    converter.execute(arguments) { timeMs ->
                        // 进度 = 已转码时间 / 源时长；按 0.1% 粒度去重，避免统计回调高频触发上层刷新
                        val durationMs = source?.durationMs ?: 0L
                        if (durationMs > 0) {
                            val permille = (timeMs * 1000 / durationMs).toInt().coerceIn(0, 1000)
                            if (permille != lastPermille) {
                                lastPermille = permille
                                onProgress(permille / 1000f)
                            }
                        }
                    }
                ) { "Video conversion failed" }
                failureType = VideoConvertError.Unknown
                currentCoroutineContext().ensureActive()
                outputStore.publish(destination)
                VideoConvertResult.Success(destination)
            }
        } catch (error: Exception) {
            output?.let { destination ->
                withContext(NonCancellable + Dispatchers.IO) {
                    try {
                        outputStore.delete(destination)
                    } catch (cleanupError: Exception) {
                        if (cleanupError !== error) error.addSuppressed(cleanupError)
                    }
                }
            }
            if (error is CancellationException) throw error
            return VideoConvertResult.Failure(failureType, error)
        }
    }
}

/** 源视频探测结果：码率仅用于「最合适」档封顶，时长仅用于转换进度换算。 */
internal data class SourceVideoInfo(val bitrateKbps: Int?, val durationMs: Long? = null)

/**
 * crf 固定档：质量最好 16 / 平衡 23 / 体积最小 34；「最合适」= 平衡档 crf + 源码率封顶
 * （不比源更糊，也不为模糊的源浪费体积），探测失败回退纯平衡档。
 * AVI（mpeg4 无 crf）：固定档用 q:v 2/4/12，「最合适」直接按源码率做目标码率。
 */
internal fun converterArguments(
    request: VideoConvertRequest,
    source: SourceVideoInfo?,
    input: String,
    output: String,
): Array<String> {
    val capKbps = source?.bitrateKbps?.takeIf { it > 0 }
    val videoArgs: Array<String>
    val audioArgs: Array<String>
    val container: String
    when (request.format) {
        VideoConvertFormat.MP4, VideoConvertFormat.MKV, VideoConvertFormat.MOV -> {
            val crf = when (request.quality) {
                VideoConvertQuality.BEST_QUALITY -> "16"
                VideoConvertQuality.BALANCED -> "23"
                VideoConvertQuality.SMALLEST -> "34"
                VideoConvertQuality.SUITABLE -> "23"
            }
            videoArgs = arrayOf("-c:v", "libx264", "-preset", "veryfast", "-crf", crf) +
                (if (request.quality == VideoConvertQuality.SUITABLE && capKbps != null) {
                    arrayOf("-maxrate", "${capKbps}k", "-bufsize", "${capKbps * 2}k")
                } else emptyArray())
            audioArgs = arrayOf("-c:a", "aac", "-b:a", "128k")
            container = if (request.format == VideoConvertFormat.MKV) "matroska" else request.format.extension
        }
        VideoConvertFormat.WEBM -> {
            val crf = when (request.quality) {
                VideoConvertQuality.BEST_QUALITY -> "16"
                VideoConvertQuality.BALANCED -> "23"
                VideoConvertQuality.SMALLEST -> "34"
                VideoConvertQuality.SUITABLE -> "23"
            }
            videoArgs = arrayOf("-c:v", "libvpx-vp9", "-b:v", "0", "-row-mt", "1", "-cpu-used", "4", "-crf", crf) +
                (if (request.quality == VideoConvertQuality.SUITABLE && capKbps != null) {
                    arrayOf("-maxrate", "${capKbps}k", "-bufsize", "${capKbps * 2}k")
                } else emptyArray())
            audioArgs = arrayOf("-c:a", "libopus")
            container = "webm"
        }
        VideoConvertFormat.AVI -> {
            videoArgs = when {
                request.quality != VideoConvertQuality.SUITABLE -> arrayOf(
                    "-c:v", "mpeg4", "-q:v",
                    when (request.quality) {
                        VideoConvertQuality.BEST_QUALITY -> "2"
                        VideoConvertQuality.BALANCED -> "4"
                        else -> "12"
                    },
                )
                capKbps != null -> arrayOf("-c:v", "mpeg4", "-b:v", "${capKbps}k")
                else -> arrayOf("-c:v", "mpeg4", "-q:v", "4")
            }
            audioArgs = arrayOf("-c:a", "libmp3lame", "-b:a", "128k")
            container = "avi"
        }
    }
    return arrayOf(
        "-hide_banner", "-i", input,
        "-map", "0:v:0", "-map", "0:a:0?",
        *videoArgs, *audioArgs,
        "-f", container, output,
    )
}

/** 同步调用返回后不得继续写入输出。[execute] 的 onTimeMs 在 FFmpeg 线程回调（已转码毫秒数）。 */
internal interface VideoConverter {
    fun probe(uri: Uri): SourceVideoInfo?
    fun inputUrl(uri: Uri): String
    fun outputUrl(uri: Uri): String
    fun execute(arguments: Array<String>, onTimeMs: (Long) -> Unit): Boolean
}
internal interface VideoOutputStore {
    fun create(inputName: String, format: VideoConvertFormat): Uri
    fun publish(uri: Uri)
    fun delete(uri: Uri)
}
