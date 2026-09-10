package com.rockbyte.vicu.page

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rockbyte.vicu.repo.AudioExportFormat
import com.rockbyte.vicu.repo.AudioExportQuality
import com.rockbyte.vicu.repo.AudioExportRepo
import com.rockbyte.vicu.repo.SourceAudioInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AudioExportUiState(
    val videoName: String = "",
    val format: AudioExportFormat = AudioExportFormat.ORIGINAL,
    val quality: AudioExportQuality = AudioExportQuality.BEST,
    val sourceInfo: SourceAudioInfo? = null,
    val phase: ExportPhase = ExportPhase.Idle,
)

sealed interface ExportPhase {
    data object Idle : ExportPhase
    data object Ready : ExportPhase
    data object Exporting : ExportPhase
    data class Complete(val outputUri: Uri) : ExportPhase
    data class Failed(val reason: String) : ExportPhase
}

/** 导出音频页状态：格式/质量选择 + 源音频探测 + 导出流程。 */
class AudioExportViewModel(private val audioExportRepo: AudioExportRepo) : ViewModel() {

    val uiState: StateFlow<AudioExportUiState>
        field = MutableStateFlow(AudioExportUiState())

    private var videoUri: Uri? = null

    /** 绑定路由传入的视频（幂等）：换源时重置状态并探测源音频。 */
    fun bind(uri: Uri, name: String) {
        if (videoUri == uri) return
        videoUri = uri
        uiState.value = AudioExportUiState(videoName = name, phase = ExportPhase.Ready)
        viewModelScope.launch {
            val info = audioExportRepo.probeAudio(uri)
            uiState.update { if (videoUri == uri) it.copy(sourceInfo = info) else it }
        }
    }

    fun selectFormat(format: AudioExportFormat) {
        uiState.update { it.copy(format = format, phase = ExportPhase.Ready) }
    }

    fun selectQuality(quality: AudioExportQuality) {
        uiState.update { it.copy(quality = quality, phase = ExportPhase.Ready) }
    }

    fun export() {
        val uri = videoUri ?: return
        if (uiState.value.phase == ExportPhase.Exporting) return
        val state = uiState.value
        viewModelScope.launch {
            uiState.update { it.copy(phase = ExportPhase.Exporting) }
            try {
                val outputUri = audioExportRepo.export(uri, state.videoName, state.format, state.quality)
                uiState.update { it.copy(phase = ExportPhase.Complete(outputUri)) }
            } catch (error: Exception) {
                uiState.update {
                    it.copy(phase = ExportPhase.Failed(error.message ?: "导出失败"))
                }
            }
        }
    }
}
