package com.rockbyte.vicu.repo

import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.math.BigDecimal

internal class AudioTrimRepository(
    private val encoder: AudioEncoder,
    private val outputStore: AudioTrimStore,
) : AudioTrimRepo {
    override suspend fun inspect(uri: Uri): AudioTrimProbeResult = withContext(Dispatchers.IO) {
        try {
            inspectSource(encoder.probe(uri))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            AudioTrimProbeResult.Failure(AudioTrimError.ProbeFailed)
        }
    }

    override suspend fun trim(request: AudioTrimRequest, onProgress: (Float) -> Unit): AudioTrimResult {
        var output: Uri? = null
        var failureType = AudioTrimError.ProbeFailed
        try {
            return withContext(Dispatchers.IO) {
                currentCoroutineContext().ensureActive()
                val probe = inspectSource(encoder.probe(request.uri))
                if (probe is AudioTrimProbeResult.Failure) return@withContext AudioTrimResult.Failure(probe.error)
                val info = (probe as AudioTrimProbeResult.Success).info
                if (request.startMs < 0 || request.startMs >= request.endMs || request.endMs > info.durationMs) {
                    return@withContext AudioTrimResult.Failure(AudioTrimError.InvalidRange)
                }
                currentCoroutineContext().ensureActive()
                failureType = AudioTrimError.OutputCreationFailed
                val destination = outputStore.create(request.displayName, info.format)
                output = destination
                currentCoroutineContext().ensureActive()
                failureType = AudioTrimError.TrimFailed
                var lastPermille = -1
                val durationMs = request.endMs - request.startMs
                check(encoder.execute(audioTrimArguments(request, info.format,
                    encoder.inputUrl(request.uri), encoder.outputUrl(destination))) { timeMs ->
                    val permille = (timeMs.toDouble() / durationMs * 1000).toInt().coerceIn(0, 1000)
                    if (permille != lastPermille) {
                        lastPermille = permille
                        onProgress(permille / 1000f)
                    }
                }) { "Audio trim failed" }
                currentCoroutineContext().ensureActive()
                val packets = encoder.packets(destination, firstOnly = info.format != AudioTrimFormat.FLAC)
                check(packets.isNotEmpty()) { "Empty audio segment" }
                if (info.format == AudioTrimFormat.FLAC) {
                    outputStore.finalizeFlac(destination, packets)
                    currentCoroutineContext().ensureActive()
                }
                failureType = AudioTrimError.OutputPublicationFailed
                outputStore.publish(destination)
                AudioTrimResult.Success(destination)
            }
        } catch (error: Exception) {
            output?.let { destination ->
                withContext(NonCancellable + Dispatchers.IO) {
                    try { outputStore.delete(destination) } catch (cleanup: Exception) {
                        if (cleanup !== error) error.addSuppressed(cleanup)
                    }
                }
            }
            if (error is CancellationException) throw error
            return AudioTrimResult.Failure(failureType, error)
        }
    }
}

private fun inspectSource(source: SourceAudioInfo?): AudioTrimProbeResult {
    val codec = source?.codec?.takeIf { it.isNotBlank() }
    val duration = source?.durationMs?.takeIf { it > 0 }
    if (codec == null || duration == null) return AudioTrimProbeResult.Failure(AudioTrimError.ProbeFailed)
    // MOV/3GP share the MP4 demuxer name; their brand prevents silently changing containers.
    val brand = source.containerBrand?.trim()?.lowercase().orEmpty()
    if (brand == "qt" || brand.startsWith("3g") || brand == "mjp2" || brand == "mj2s") {
        return AudioTrimProbeResult.Failure(AudioTrimError.UnsupportedFormat)
    }
    val formats = source.containerName?.split(',').orEmpty()
    val format = when {
        "mp3" in formats -> AudioTrimFormat.MP3
        "mp4" in formats || "m4a" in formats -> AudioTrimFormat.M4A
        "wav" in formats -> AudioTrimFormat.WAV
        "flac" in formats -> AudioTrimFormat.FLAC
        "ogg" in formats -> AudioTrimFormat.OGG
        else -> return AudioTrimProbeResult.Failure(AudioTrimError.UnsupportedFormat)
    }
    return AudioTrimProbeResult.Success(AudioTrimInfo(duration, format, codec))
}

internal fun audioTrimArguments(request: AudioTrimRequest, format: AudioTrimFormat,
    input: String, output: String): Array<String> = arrayOf(
    "-hide_banner", "-i", input,
    "-ss", BigDecimal.valueOf(request.startMs, 3).toPlainString(),
    "-t", BigDecimal.valueOf(request.endMs - request.startMs, 3).toPlainString(),
    "-map", "0:a:0", "-vn", "-c:a", "copy", "-map_chapters", "-1",
    "-f", format.muxer, output,
)
