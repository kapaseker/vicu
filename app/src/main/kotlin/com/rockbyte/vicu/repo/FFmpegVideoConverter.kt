package com.rockbyte.vicu.repo

import android.content.Context
import android.net.Uri
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import java.util.concurrent.CountDownLatch

internal class FFmpegVideoConverter(context: Context) : VideoConverter {
    private val appContext = context.applicationContext
    override fun inputUrl(uri: Uri): String = FFmpegKitConfig.getSafParameterForRead(appContext, uri)
    override fun outputUrl(uri: Uri): String = FFmpegKitConfig.getSafParameterForWrite(appContext, uri)

    override fun probe(uri: Uri): SourceVideoInfo? {
        val session = FFprobeKit.executeWithArguments(probeArguments(inputUrl(uri)))
        if (!ReturnCode.isSuccess(session.getReturnCode())) return null
        return parseProbeOutput(session.getOutput())
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

/**
 * 探测视频流与容器（-select_streams v:0 排除音频流），key=value 输出与 ffprobe 版本无关
 * （不能用 csv 按列解析：ffprobe 按内部字段序输出，duration 实际排在 bit_rate 之前）。
 */
internal fun probeArguments(input: String): Array<String> = arrayOf(
    "-v", "error", "-select_streams", "v:0",
    "-show_entries", "stream=bit_rate:format=bit_rate,duration",
    "-of", "default=nokey=0:noprint_wrappers=1", input,
)

/**
 * 解析 ffprobe 的 key=value 输出（stream 的 bit_rate 在首行，随后是 format 的 duration 与 bit_rate）。
 * 码率取第一个有效值：视频流优先；视频流为 N/A（如 MKV）时回退容器总码率（含音频开销，作封顶略宽松但安全）。
 * 时长取容器 duration（秒 → 毫秒），用于转换进度换算。
 */
internal fun parseProbeOutput(output: String): SourceVideoInfo {
    var bitrateKbps: Int? = null
    var durationMs: Long? = null
    for (line in output.trim().lines()) {
        val parts = line.trim().split('=', limit = 2)
        if (parts.size < 2) continue
        val value = parts[1].trim()
        when (parts[0]) {
            "bit_rate" -> if (bitrateKbps == null) {
                bitrateKbps = value.takeIf { it.isNotBlank() && it != "N/A" }
                    ?.toIntOrNull()?.takeIf { it > 0 }?.let { it / 1000 }
            }
            "duration" -> durationMs = value.toDoubleOrNull()
                ?.takeIf { it > 0 }?.let { (it * 1000).toLong() }
        }
    }
    return SourceVideoInfo(bitrateKbps, durationMs)
}
