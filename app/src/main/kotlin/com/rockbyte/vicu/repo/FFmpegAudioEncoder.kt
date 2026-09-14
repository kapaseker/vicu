package com.rockbyte.vicu.repo

import android.content.Context
import android.net.Uri
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode

internal class FFmpegAudioEncoder(context: Context) : AudioEncoder {
    private val appContext = context.applicationContext
    override fun inputUrl(uri: Uri): String = FFmpegKitConfig.getSafParameterForRead(appContext, uri)
    override fun outputUrl(uri: Uri): String = FFmpegKitConfig.getSafParameterForWrite(appContext, uri)
    override fun probe(uri: Uri): SourceAudioInfo? {
        val session = FFprobeKit.executeWithArguments(arrayOf(
            "-v", "error", "-select_streams", "a:0",
            "-show_entries", "stream=codec_name,bit_rate", "-of", "csv=p=0", inputUrl(uri),
        ))
        if (!ReturnCode.isSuccess(session.getReturnCode())) return null
        val columns = session.getOutput().trim().lineSequence().firstOrNull()?.split(',') ?: return null
        return SourceAudioInfo(
            columns.getOrNull(0)?.takeIf { it.isNotBlank() && it != "N/A" },
            columns.getOrNull(1)?.trim()?.toIntOrNull()?.takeIf { it > 0 }?.let { it / 1000 },
        )
    }
    override fun execute(arguments: Array<String>): Boolean =
        ReturnCode.isSuccess(FFmpegKit.executeWithArguments(arguments).getReturnCode())
}
