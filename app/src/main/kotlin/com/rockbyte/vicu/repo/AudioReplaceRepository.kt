package com.rockbyte.vicu.repo

import android.net.Uri
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal class AudioReplaceRepository(
    private val videoConverter: VideoConverter,
    private val audioEncoder: AudioEncoder,
    private val outputStore: VideoOutputStore,
) : AudioReplaceRepo {

    override suspend fun probeVideoDurationMs(uri: Uri): Long? = withContext(Dispatchers.IO) {
        try {
            videoConverter.probe(uri)?.durationMs
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun probeMusicDurationMs(uri: Uri): Long? = withContext(Dispatchers.IO) {
        try {
            audioEncoder.probe(uri)?.durationMs
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun replace(
        request: AudioReplaceRequest,
        onProgress: (Float) -> Unit,
    ): AudioReplaceResult {
        var output: Uri? = null
        var failureType: AudioReplaceError = AudioReplaceError.Unknown
        try {
            return withContext(Dispatchers.IO) {
                currentCoroutineContext().ensureActive()
                val videoDurationMs = probeVideoDurationMs(request.videoUri)
                val musicDurationMs = probeMusicDurationMs(request.musicUri)
                if (request.mode != AudioReplaceMode.TRUNCATE) {
                    failureType = AudioReplaceError.InvalidMedia
                    check(
                        videoDurationMs != null && videoDurationMs > 0 &&
                            musicDurationMs != null && musicDurationMs > 0
                    ) { "Stretch modes require both media durations" }
                }
                currentCoroutineContext().ensureActive()
                failureType = AudioReplaceError.OutputCreationFailed
                val destination = outputStore.create(request.displayName, VideoConvertFormat.MP4)
                output = destination
                failureType = AudioReplaceError.TranscodeFailed
                currentCoroutineContext().ensureActive()
                val arguments = audioReplaceArguments(
                    request, videoDurationMs, musicDurationMs,
                    videoConverter.inputUrl(request.videoUri),
                    audioEncoder.inputUrl(request.musicUri),
                    videoConverter.outputUrl(destination),
                )
                // 进度 = 已处理时间 / 期望输出时长；伸缩视频的输出时长以音乐为准，其余以视频为准。
                val expectedDurationMs = when (request.mode) {
                    AudioReplaceMode.STRETCH_VIDEO -> musicDurationMs
                    else -> videoDurationMs
                }
                var lastPermille = -1
                check(
                    videoConverter.execute(arguments) { timeMs ->
                        // 按 0.1% 粒度去重，避免统计回调高频触发上层刷新
                        if (expectedDurationMs != null && expectedDurationMs > 0) {
                            val permille = (timeMs * 1000 / expectedDurationMs).toInt().coerceIn(0, 1000)
                            if (permille != lastPermille) {
                                lastPermille = permille
                                onProgress(permille / 1000f)
                            }
                        }
                    }
                ) { "Audio replacement failed" }
                failureType = AudioReplaceError.Unknown
                currentCoroutineContext().ensureActive()
                outputStore.publish(destination)
                AudioReplaceResult.Success(destination)
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
            return AudioReplaceResult.Failure(failureType, error)
        }
    }
}

/**
 * 替换音轨固定输出 MP4：截断/伸缩音频时视频流复制（快、无损），仅伸缩视频需重编码；
 * 音频恒重编码 AAC（音乐源可能是任意编码，FLAC 等进 MP4 不可靠）。
 */
internal fun audioReplaceArguments(
    request: AudioReplaceRequest,
    videoDurationMs: Long?,
    musicDurationMs: Long?,
    videoInput: String,
    musicInput: String,
    output: String,
): Array<String> {
    val videoArgs: Array<String>
    val filterArgs: Array<String>
    val limitArgs: Array<String>
    when (request.mode) {
        AudioReplaceMode.TRUNCATE -> {
            videoArgs = arrayOf("-c:v", "copy")
            filterArgs = emptyArray()
            // 视频时长已知则以 -t 精确截到视频长度（音乐短于视频时音频流自然提前结束）；未知回退 -shortest
            limitArgs = if (videoDurationMs != null && videoDurationMs > 0) {
                arrayOf("-t", "${videoDurationMs}ms")
            } else {
                arrayOf("-shortest")
            }
        }
        AudioReplaceMode.STRETCH_AUDIO -> {
            videoArgs = arrayOf("-c:v", "copy")
            val tempo = musicDurationMs!!.toDouble() / videoDurationMs!!
            filterArgs = arrayOf("-af", atempoFilterChain(tempo))
            limitArgs = emptyArray()
        }
        AudioReplaceMode.STRETCH_VIDEO -> {
            val ratio = musicDurationMs!!.toDouble() / videoDurationMs!!
            videoArgs = arrayOf("-c:v", "libx264", "-preset", "veryfast", "-crf", "23")
            filterArgs = arrayOf("-vf", "setpts=PTS*${"%.4f".format(Locale.US, ratio)}")
            // setpts 舍入可能让视频比音乐长出几毫秒，-shortest 消除多余尾巴
            limitArgs = arrayOf("-shortest")
        }
    }
    return arrayOf(
        "-hide_banner", "-i", videoInput, "-i", musicInput,
        "-map", "0:v:0", "-map", "1:a:0",
        *videoArgs, *filterArgs,
        "-c:a", "aac", "-b:a", "128k",
        *limitArgs,
        "-f", "mp4", output,
    )
}

/**
 * 保音调变速滤镜链：单级 atempo 支持 0.5–2.0，超出时按 2.0/0.5 拆分级联覆盖更宽范围
 * （如 3.5 → `atempo=2.0,atempo=1.75`）；比率保留 4 位小数。
 */
internal fun atempoFilterChain(tempo: Double): String {
    require(tempo > 0) { "Tempo must be positive" }
    val parts = mutableListOf<String>()
    var remaining = tempo
    while (remaining > 2.0) {
        parts += "atempo=2.0"
        remaining /= 2.0
    }
    while (remaining < 0.5) {
        parts += "atempo=0.5"
        remaining *= 2.0
    }
    parts += "atempo=" + "%.4f".format(Locale.US, remaining)
    return parts.joinToString(",")
}
