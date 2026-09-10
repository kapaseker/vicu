package com.rockbyte.vicu.page

import android.app.Application
import android.content.ContentValues
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AudioExportUiState(
    val selectedFileName: String? = null,
    val phase: ExportPhase = ExportPhase.Idle
)

sealed interface ExportPhase {
    data object Idle : ExportPhase
    data object Ready : ExportPhase
    data object Exporting : ExportPhase
    data class Complete(val outputUri: Uri) : ExportPhase
    data class Failed(val reason: String) : ExportPhase
}

class AudioExportViewModel(application: Application) : AndroidViewModel(application) {

    private val resolver = application.contentResolver
    private val appContext = application.applicationContext

    val uiState: StateFlow<AudioExportUiState>
        field = MutableStateFlow(AudioExportUiState())

    private var selectedVideoUri: Uri? = null

    fun selectVideo(uri: Uri) {
        selectedVideoUri = uri
        uiState.value = AudioExportUiState(
            selectedFileName = displayName(uri),
            phase = ExportPhase.Ready
        )
    }

    fun exportSelectedVideo() {
        val inputUri = selectedVideoUri ?: return
        val inputName = uiState.value.selectedFileName ?: "video"

        viewModelScope.launch(Dispatchers.IO) {
            uiState.update { it.copy(phase = ExportPhase.Exporting) }
            var outputUri: Uri? = null

            try {
                outputUri = createPendingOutput(inputName)
                val inputUrl = FFmpegKitConfig.getSafParameterForRead(appContext, inputUri)
                val outputUrl = FFmpegKitConfig.getSafParameterForWrite(appContext, outputUri)
                val session = FFmpegKit.executeWithArguments(Mp3ExportCommand.build(inputUrl, outputUrl))

                check(ReturnCode.isSuccess(session.getReturnCode())) {
                    session.getFailStackTrace()?.takeIf(String::isNotBlank)
                        ?: "FFmpeg 转码失败，返回码：${session.getReturnCode()}"
                }

                publishOutput(outputUri)
                uiState.update { it.copy(phase = ExportPhase.Complete(outputUri)) }
            } catch (error: Exception) {
                outputUri?.let(::deleteOutput)
                uiState.update {
                    it.copy(phase = ExportPhase.Failed(error.message ?: "无法导出 MP3"))
                }
            }
        }
    }

    private fun createPendingOutput(inputName: String): Uri {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, outputNameFor(inputName))
            put(MediaStore.MediaColumns.MIME_TYPE, "audio/mpeg")
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                "${Environment.DIRECTORY_MUSIC}/FFmpegKitNext"
            )
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        return checkNotNull(
            resolver.insert(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
        ) { "无法在媒体库中创建 MP3 文件" }
    }

    private fun publishOutput(uri: Uri) {
        resolver.update(uri, ContentValues().apply {
            put(MediaStore.MediaColumns.IS_PENDING, 0)
        }, null, null)
    }

    private fun deleteOutput(uri: Uri) {
        resolver.delete(uri, null, null)
    }

    private fun displayName(uri: Uri): String =
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
            ?: "video"

    private fun outputNameFor(inputName: String): String {
        val baseName = inputName.substringBeforeLast('.', inputName)
            .replace(Regex("[^\\p{L}\\p{N}._-]"), "_")
            .trim('_')
            .ifBlank { "audio" }
        return "${baseName}_${System.currentTimeMillis()}.mp3"
    }
}
