package com.rockbyte.vicu.page

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rockbyte.vicu.repo.AudioExportFormat
import com.rockbyte.vicu.repo.AudioExportError
import com.rockbyte.vicu.repo.AudioExportRequest
import com.rockbyte.vicu.repo.AudioExportResult
import com.rockbyte.vicu.repo.AudioExportQuality
import com.rockbyte.vicu.repo.AudioExportRepo
import com.rockbyte.vicu.repo.SelectedMedia
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AudioExportUiState(
    val videoName: String = "",
    val format: AudioExportFormat = AudioExportFormat.ORIGINAL,
    val quality: AudioExportQuality = AudioExportQuality.SUITABLE,
    val phase: ExportPhase = ExportPhase.Idle,
)

sealed interface ExportPhase {
    data object Idle : ExportPhase
    data object Ready : ExportPhase
    /** progress 为 null 表示源时长未知，无法计算百分比。 */
    data class Exporting(val progress: Float?) : ExportPhase
    data object Complete : ExportPhase
    data class Failed(val error: AudioExportError) : ExportPhase
}

/** 导出音频页状态：格式/质量选择 + 导出流程。 */
class AudioExportViewModel(private val audioExportRepo: AudioExportRepo) : ViewModel() {

    val uiState: StateFlow<AudioExportUiState>
        field = MutableStateFlow(AudioExportUiState())

    private var selectedMedia: SelectedMedia? = null

    /** 绑定路由传入的视频（幂等）：换源时重置状态。 */
    fun bind(media: SelectedMedia) {
        if (selectedMedia == media) return
        selectedMedia = media
        uiState.value = AudioExportUiState(videoName = media.name, phase = ExportPhase.Ready)
    }

    fun selectFormat(format: AudioExportFormat) {
        uiState.update { it.copy(format = format, phase = ExportPhase.Ready) }
    }

    fun selectQuality(quality: AudioExportQuality) {
        uiState.update { it.copy(quality = quality, phase = ExportPhase.Ready) }
    }

    fun export() {
        val media = selectedMedia ?: return
        if (uiState.value.phase is ExportPhase.Exporting) return
        val state = uiState.value
        viewModelScope.launch {
            uiState.update { it.copy(phase = ExportPhase.Exporting(progress = null)) }
            val result = audioExportRepo.export(
                AudioExportRequest(Uri.parse(media.uri), state.videoName, state.format, state.quality),
            ) { progress ->
                // 回调来自 FFmpeg 线程；StateFlow.update 原子且线程安全
                uiState.update { it.copy(phase = ExportPhase.Exporting(progress)) }
            }
            uiState.update {
                it.copy(
                    phase = when (result) {
                        is AudioExportResult.Success -> ExportPhase.Complete
                        is AudioExportResult.Failure -> ExportPhase.Failed(result.error)
                    },
                )
            }
        }
    }
}
