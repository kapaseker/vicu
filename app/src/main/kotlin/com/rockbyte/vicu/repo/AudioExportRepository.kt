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
    override suspend fun export(request: AudioExportRequest): AudioExportResult {
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
                    request.quality == AudioExportQuality.BEST &&
                    (source?.codec == null || source.codec == "aac")
                val bitrate = when (request.quality) {
                    AudioExportQuality.BEST, AudioExportQuality.HIGH -> 320
                    AudioExportQuality.MEDIUM -> 192
                    AudioExportQuality.LOW -> 128
                }.let { minOf(it, source?.bitrateKbps ?: it) }
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
                check(encoder.execute(arguments)) { "Audio encoding failed" }
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
internal data class SourceAudioInfo(val codec: String?, val bitrateKbps: Int?)

/** 同步调用返回后不得继续写入输出。 */
internal interface AudioEncoder {
    fun probe(uri: Uri): SourceAudioInfo?
    fun inputUrl(uri: Uri): String
    fun outputUrl(uri: Uri): String
    fun execute(arguments: Array<String>): Boolean
}
internal interface AudioOutputStore {
    fun create(inputName: String, format: AudioExportFormat): Uri
    fun publish(uri: Uri)
    fun delete(uri: Uri)
}
