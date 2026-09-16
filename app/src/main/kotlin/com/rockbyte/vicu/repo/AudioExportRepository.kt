package com.rockbyte.vicu.repo

import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal class AudioExportRepository(
    private val encoder: AudioEncoder,
    private val outputStore: AudioOutputStore,
) : AudioExportRepo {
    override suspend fun export(
        request: AudioExportRequest,
        onProgress: (Float) -> Unit,
    ): AudioExportResult {
        var output: Uri? = null
        var failureType: AudioExportError = AudioExportError.OutputCreationFailed
        try {
            return withContext(Dispatchers.IO) {
                currentCoroutineContext().ensureActive()
                val destination = outputStore.create(request.displayName, request.format)
                output = destination
                failureType = AudioExportError.Unknown
                currentCoroutineContext().ensureActive()
                val source = try {
                    encoder.probe(request.uri)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
                currentCoroutineContext().ensureActive()
                val copy = request.format == AudioExportFormat.ORIGINAL &&
                    request.quality == AudioExportQuality.BEST_QUALITY &&
                    (source?.codec == null || source.codec == "aac")
                val bitrate = when (request.quality) {
                    AudioExportQuality.BEST_QUALITY -> 320
                    AudioExportQuality.BALANCED -> 192
                    AudioExportQuality.SMALLEST -> 128
                    AudioExportQuality.SUITABLE -> source?.bitrateKbps
                        ?.takeIf { it > 0 }?.coerceAtMost(192) ?: 192
                }
                val encoderArgs = if (copy) arrayOf("-c:a", "copy") else arrayOf(
                    "-c:a", if (request.format == AudioExportFormat.MP3) "libmp3lame" else "aac",
                    "-b:a", "${bitrate}k",
                )
                val arguments = arrayOf(
                    "-hide_banner", "-i", encoder.inputUrl(request.uri),
                    "-map", "0:a:0", "-vn", *encoderArgs,
                    "-f", if (request.format == AudioExportFormat.MP3) "mp3" else "ipod",
                    encoder.outputUrl(destination),
                )
                failureType = AudioExportError.TranscodeFailed
                var lastPermille = -1
                check(
                    encoder.execute(arguments) { timeMs ->
                        // 进度 = 已处理时间 / 源时长；按 0.1% 粒度去重，避免统计回调高频触发上层刷新
                        val durationMs = source?.durationMs ?: 0L
                        if (durationMs > 0) {
                            val permille = (timeMs * 1000 / durationMs).toInt().coerceIn(0, 1000)
                            if (permille != lastPermille) {
                                lastPermille = permille
                                onProgress(permille / 1000f)
                            }
                        }
                    }
                ) { "Audio encoding failed" }
                failureType = AudioExportError.Unknown
                currentCoroutineContext().ensureActive()
                outputStore.publish(destination)
                AudioExportResult.Success(destination)
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
            return AudioExportResult.Failure(failureType, error)
        }
    }
}
/** 源音频探测结果：codec 仅用于原声直拷判断，码率用于质量档封顶，采样格式用于 WAV 位深匹配，时长仅用于进度换算。 */
internal data class SourceAudioInfo(
    val codec: String?,
    val bitrateKbps: Int?,
    val durationMs: Long? = null,
    val sampleFmt: String? = null,
)

/** 同步调用返回后不得继续写入输出。[execute] 的 onTimeMs 在 FFmpeg 线程回调（已处理毫秒数）。 */
internal interface AudioEncoder {
    fun probe(uri: Uri): SourceAudioInfo?
    fun inputUrl(uri: Uri): String
    fun outputUrl(uri: Uri): String
    fun execute(arguments: Array<String>, onTimeMs: (Long) -> Unit): Boolean
}
internal interface AudioOutputStore {
    fun create(inputName: String, format: AudioExportFormat): Uri
    fun publish(uri: Uri)
    fun delete(uri: Uri)
}
