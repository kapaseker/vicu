package com.rockbyte.vicu.page

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rockbyte.vicu.repo.VideoConvertError
import com.rockbyte.vicu.repo.VideoConvertFormat
import com.rockbyte.vicu.repo.VideoConvertQuality
import com.rockbyte.vicu.repo.VideoConvertRepo
import com.rockbyte.vicu.repo.VideoConvertRequest
import com.rockbyte.vicu.repo.VideoConvertResult
import com.rockbyte.vicu.repo.SelectedMedia
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.core.net.toUri

data class VideoConvertUiState(
    val videoName: String = "",
    val format: VideoConvertFormat = VideoConvertFormat.MP4,
    val quality: VideoConvertQuality = VideoConvertQuality.SUITABLE,
    val phase: ConvertPhase = ConvertPhase.Idle,
)

sealed interface ConvertPhase {
    data object Idle : ConvertPhase
    data object Ready : ConvertPhase
    /** progress 为 null 表示源时长未知，无法计算百分比。 */
    data class Converting(val progress: Float?) : ConvertPhase
    data object Complete : ConvertPhase
    data class Failed(val error: VideoConvertError) : ConvertPhase
}

/** 视频转换页状态：格式/质量选择 + 转换流程。 */
class VideoConvertViewModel(private val videoConvertRepo: VideoConvertRepo) : ViewModel() {

    val uiState: StateFlow<VideoConvertUiState>
        field = MutableStateFlow(VideoConvertUiState())

    private var selectedMedia: SelectedMedia? = null

    /** 绑定路由传入的视频（幂等）：换源时重置状态。 */
    fun bind(media: SelectedMedia) {
        if (selectedMedia == media) return
        selectedMedia = media
        uiState.value = VideoConvertUiState(videoName = media.name, phase = ConvertPhase.Ready)
    }

    fun selectFormat(format: VideoConvertFormat) {
        uiState.update { it.copy(format = format, phase = ConvertPhase.Ready) }
    }

    fun selectQuality(quality: VideoConvertQuality) {
        uiState.update { it.copy(quality = quality, phase = ConvertPhase.Ready) }
    }

    fun convert() {
        val media = selectedMedia ?: return
        if (uiState.value.phase is ConvertPhase.Converting) return
        val state = uiState.value
        viewModelScope.launch {
            uiState.update { it.copy(phase = ConvertPhase.Converting(progress = null)) }
            val result = videoConvertRepo.convert(
                VideoConvertRequest(media.uri.toUri(), state.videoName, state.format, state.quality),
            ) { progress ->
                // 回调来自 FFmpeg 线程；StateFlow.update 原子且线程安全
                uiState.update { it.copy(phase = ConvertPhase.Converting(progress)) }
            }
            uiState.update {
                it.copy(
                    phase = when (result) {
                        is VideoConvertResult.Success -> ConvertPhase.Complete
                        is VideoConvertResult.Failure -> ConvertPhase.Failed(result.error)
                    },
                )
            }
        }
    }
}
