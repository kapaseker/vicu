package com.rockbyte.vicu.repo

import android.content.Context
import android.net.Uri
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import java.util.concurrent.CountDownLatch

internal class FFmpegAudioEncoder(context: Context) : AudioEncoder {
    private val appContext = context.applicationContext
    override fun inputUrl(uri: Uri): String = FFmpegKitConfig.getSafParameterForRead(appContext, uri)
    override fun outputUrl(uri: Uri): String = FFmpegKitConfig.getSafParameterForWrite(appContext, uri)
    override fun probe(uri: Uri): SourceAudioInfo? {
        val session = FFprobeKit.executeWithArguments(probeAudioArguments(inputUrl(uri)))
        if (!ReturnCode.isSuccess(session.getReturnCode())) return null
        return parseAudioProbeOutput(session.getOutput())
    }

    override fun packets(uri: Uri, firstOnly: Boolean): List<AudioPacketInfo> {
        val session = FFprobeKit.executeWithArguments(arrayOf("-v", "error", "-select_streams", "a:0",
            *(if (firstOnly) arrayOf("-read_intervals", "%+#1") else emptyArray()),
            "-show_packets", "-show_entries", "packet=pos,size", "-of", "default=nokey=0", inputUrl(uri)))
        check(ReturnCode.isSuccess(session.getReturnCode())) { "Packet inspection failed" }
        return parseAudioPackets(session.getOutput())
    }

    /** aar 仅有单参同步 executeWithArguments；带统计回调的只有 Async 版本，用 latch 还原阻塞语义。 */
    override fun execute(arguments: Array<String>, onTimeMs: (Long) -> Unit): Boolean {
        val done = CountDownLatch(1)
        val session = FFmpegKit.executeWithArgumentsAsync(
            arguments,
            { done.countDown() },
            null,
            { statistics -> onTimeMs(statistics.time.toLong()) },
        )
        done.await()
        return ReturnCode.isSuccess(session.getReturnCode())
    }
}

/** 探测音频流与容器，key=value 输出与 ffprobe 版本无关（按列解析会踩内部字段序陷阱）。 */
internal fun probeAudioArguments(input: String): Array<String> = arrayOf(
    "-v", "error", "-select_streams", "a:0",
    "-show_entries", "stream=codec_name,bit_rate,sample_fmt:format=duration,format_name:format_tags=major_brand",
    "-of", "default=nokey=0:noprint_wrappers=1", input,
)

/**
 * 解析 key=value 输出：音频流的 codec_name/bit_rate/sample_fmt，容器的 duration（秒 → 毫秒，用于导出进度换算）。
 * bit_rate/sample_fmt 为 N/A 或缺失时为 null。
 */
internal fun parseAudioProbeOutput(output: String): SourceAudioInfo? {
    var codec: String? = null
    var bitrateKbps: Int? = null
    var durationMs: Long? = null
    var sampleFmt: String? = null
    var containerName: String? = null
    var containerBrand: String? = null
    for (line in output.trim().lines()) {
        val parts = line.trim().split('=', limit = 2)
        if (parts.size < 2) continue
        val value = parts[1].trim()
        when (parts[0]) {
            "codec_name" -> codec = value.takeIf { it.isNotBlank() && it != "N/A" }
            "bit_rate" -> if (bitrateKbps == null) {
                bitrateKbps = value.takeIf { it.isNotBlank() && it != "N/A" }
                    ?.toIntOrNull()?.takeIf { it > 0 }?.let { it / 1000 }
            }
            "sample_fmt" -> sampleFmt = value.takeIf { it.isNotBlank() && it != "N/A" }
            "TAG:major_brand" -> containerBrand = value.takeIf { it.isNotBlank() }
            "format_name" -> containerName = value.takeIf { it.isNotBlank() && it != "N/A" }
            "duration" -> durationMs = value.toDoubleOrNull()
                ?.takeIf { it.isFinite() && it > 0 && it < Long.MAX_VALUE / 1000.0 }?.let { (it * 1000).toLong() }
        }
    }
    if (codec == null && bitrateKbps == null && durationMs == null && sampleFmt == null) return null
    return SourceAudioInfo(codec, bitrateKbps, durationMs, sampleFmt, containerName, containerBrand)
}

/** Packet offsets let FLAC stream-copy finalization rewrite frame headers without decoding audio. */
internal data class AudioPacketInfo(val position: Long, val size: Int)

internal fun parseAudioPackets(output: String): List<AudioPacketInfo> {
    val result = mutableListOf<AudioPacketInfo>()
    var position: Long? = null
    var size: Int? = null
    for (line in output.lineSequence()) {
        when {
            line == "[PACKET]" -> { position = null; size = null }
            line.startsWith("pos=") -> position = line.substringAfter('=').toLongOrNull()
            line.startsWith("size=") -> size = line.substringAfter('=').toIntOrNull()
            line == "[/PACKET]" -> {
                val offset = checkNotNull(position)
                val length = checkNotNull(size)
                check(offset >= 0 && length > 0)
                result += AudioPacketInfo(offset, length)
            }
        }
    }
    check(result.isNotEmpty()) { "Empty audio segment" }
    return result
}
