package com.rockbyte.vicu.repo

import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal class AudioConvertRepository(
    private val encoder: AudioEncoder,
    private val outputStore: AudioConvertStore,
) : AudioConvertRepo {
    override suspend fun convert(
        request: AudioConvertRequest,
        onProgress: (Float) -> Unit,
    ): AudioConvertResult {
        var output: Uri? = null
        var failureType: AudioConvertError = AudioConvertError.OutputCreationFailed
        try {
            return withContext(Dispatchers.IO) {
                currentCoroutineContext().ensureActive()
                val destination = outputStore.create(request.displayName, request.format)
                output = destination
                failureType = AudioConvertError.Unknown
                currentCoroutineContext().ensureActive()
                val source = try {
                    encoder.probe(request.uri)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
                currentCoroutineContext().ensureActive()
                val arguments = audioConvertArguments(
                    request, source, encoder.inputUrl(request.uri), encoder.outputUrl(destination),
                )
                failureType = AudioConvertError.TranscodeFailed
                var lastPermille = -1
                check(
                    encoder.execute(arguments) { timeMs ->
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
                ) { "Audio conversion failed" }
                failureType = AudioConvertError.Unknown
                currentCoroutineContext().ensureActive()
                outputStore.publish(destination)
                AudioConvertResult.Success(destination)
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
            return AudioConvertResult.Failure(failureType, error)
        }
    }
}

/**
 * 有损格式（MP3/M4A/OGG）固定档：质量最好 320k / 平衡 192k / 体积最小 128k；「最合适」按源码率封顶
 * （不比源更糊，也不为模糊的源浪费体积），探测失败回退纯平衡档 192k。
 * 无损格式（WAV/FLAC）码率档不适用：WAV 用 pcm_s16le，FLAC 用默认压缩级别。
 */
internal fun audioConvertArguments(
    request: AudioConvertRequest,
    source: SourceAudioInfo?,
    input: String,
    output: String,
): Array<String> {
    val codecArgs: Array<String> = when (request.format) {
        AudioConvertFormat.MP3 -> arrayOf(
            "-c:a", "libmp3lame", "-b:a", "${bitrateKbps(request.quality, source)}k",
        )
        AudioConvertFormat.M4A -> arrayOf("-c:a", "aac", "-b:a", "${bitrateKbps(request.quality, source)}k")
        AudioConvertFormat.OGG -> arrayOf("-c:a", "libopus", "-b:a", "${bitrateKbps(request.quality, source)}k")
        AudioConvertFormat.WAV -> arrayOf("-c:a", "pcm_s16le")
        AudioConvertFormat.FLAC -> arrayOf("-c:a", "flac")
    }
    return arrayOf(
        "-hide_banner", "-i", input,
        "-map", "0:a:0", "-vn", *codecArgs,
        "-f", request.format.extension, output,
    )
}

private fun bitrateKbps(quality: AudioConvertQuality, source: SourceAudioInfo?): Int = when (quality) {
    AudioConvertQuality.BEST_QUALITY -> 320
    AudioConvertQuality.BALANCED -> 192
    AudioConvertQuality.SMALLEST -> 128
    AudioConvertQuality.SUITABLE -> source?.bitrateKbps?.takeIf { it > 0 }?.coerceAtMost(192) ?: 192
}

internal interface AudioConvertStore {
    fun create(inputName: String, format: AudioConvertFormat): Uri
    fun publish(uri: Uri)
    fun delete(uri: Uri)
}
