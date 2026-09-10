package com.rockbyte.vicu.repo

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** [AudioExportRepo] 的 FFmpeg + MediaStore 实现。 */
class AudioExportRepository(context: Context) : AudioExportRepo {

    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver

    override suspend fun probeAudio(uri: Uri): SourceAudioInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val inputUrl = FFmpegKitConfig.getSafParameterForRead(appContext, uri)
            val session = FFprobeKit.executeWithArguments(
                arrayOf(
                    "-v", "error",
                    "-select_streams", "a:0",
                    "-show_entries", "stream=codec_name,bit_rate",
                    "-of", "csv=p=0",
                    inputUrl,
                )
            )
            check(ReturnCode.isSuccess(session.getReturnCode())) { "FFprobe 探测失败" }
            val columns = session.getOutput().trim().lineSequence().firstOrNull()
                ?.split(',')
                ?: return@runCatching null
            SourceAudioInfo(
                codec = columns.getOrNull(0)?.takeIf { it.isNotBlank() && it != "N/A" },
                bitrateKbps = columns.getOrNull(1)?.trim()?.toIntOrNull()
                    ?.takeIf { it > 0 }?.let { it / 1000 },
            )
        }.getOrNull()
    }

    override suspend fun export(
        uri: Uri,
        displayName: String,
        format: AudioExportFormat,
        quality: AudioExportQuality,
    ): Uri = withContext(Dispatchers.IO) {
        val outputUri = createPendingOutput(displayName, format)
        try {
            val source = probeAudio(uri)
            val target = resolveTarget(format, quality, source)
            val session = FFmpegKit.executeWithArguments(
                AudioExportCommand.build(
                    inputUrl = FFmpegKitConfig.getSafParameterForRead(appContext, uri),
                    outputUrl = FFmpegKitConfig.getSafParameterForWrite(appContext, outputUri),
                    format = format,
                    target = target,
                )
            )
            check(ReturnCode.isSuccess(session.getReturnCode())) {
                session.getFailStackTrace()?.takeIf(String::isNotBlank)
                    ?: "FFmpeg 转码失败，返回码：${session.getReturnCode()}"
            }
            publishOutput(outputUri)
            outputUri
        } catch (error: Exception) {
            deleteOutput(outputUri)
            throw error
        }
    }

    private fun createPendingOutput(inputName: String, format: AudioExportFormat): Uri {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, outputNameFor(inputName, format))
            put(MediaStore.MediaColumns.MIME_TYPE, format.mime)
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                "${Environment.DIRECTORY_MUSIC}/FFmpegKitNext"
            )
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        return checkNotNull(
            resolver.insert(
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                values,
            )
        ) { "无法在媒体库中创建 ${format.extension} 文件" }
    }

    private fun publishOutput(uri: Uri) {
        resolver.update(
            uri,
            ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
            null,
            null,
        )
    }

    private fun deleteOutput(uri: Uri) {
        resolver.delete(uri, null, null)
    }

    private fun outputNameFor(inputName: String, format: AudioExportFormat): String {
        val baseName = inputName.substringBeforeLast('.', inputName)
            .replace(Regex("[^\\p{L}\\p{N}._-]"), "_")
            .trim('_')
            .ifBlank { "audio" }
        return "${baseName}_${System.currentTimeMillis()}.${format.extension}"
    }
}
