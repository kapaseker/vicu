package com.rockbyte.vicu.page.crop

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rockbyte.vicu.player.PlayerEffect
import com.rockbyte.vicu.player.normalized
import com.rockbyte.vicu.player.toVideoFilter
import com.rockbyte.vicu.repo.SelectedMedia
import com.rockbyte.vicu.repo.VideoConvertError
import com.rockbyte.vicu.repo.VideoConvertQuality
import com.rockbyte.vicu.repo.VideoConvertRepo
import com.rockbyte.vicu.repo.VideoConvertRequest
import com.rockbyte.vicu.repo.VideoConvertResult
import com.rockbyte.vicu.repo.videoOutputFormat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 裁剪导出阶段。 */
sealed interface CutPhase {
    data object Idle : CutPhase

    /** progress 为 null 表示源时长未知，无法计算百分比。 */
    data class Cutting(val progress: Float?) : CutPhase
    data object Complete : CutPhase
    data class Failed(val error: VideoConvertError) : CutPhase
}

data class CropUiState(
    val videoName: String = "",
    val cutPhase: CutPhase = CutPhase.Idle,
)

/**
 * 裁剪页状态：预览播放由 [com.rockbyte.vicu.page.player.PlayerViewModel] 承担，
 * 本 VM 只负责按框选区域重编码导出（复用 [VideoConvertRepo] 转换管线：进度 + 失败回滚 + 媒体库发布）。
 */
class CropViewModel(private val videoConvertRepo: VideoConvertRepo) : ViewModel() {

    val uiState: StateFlow<CropUiState>
        field = MutableStateFlow(CropUiState())

    private var selectedMedia: SelectedMedia? = null

    /** 绑定路由传入的视频（幂等）：换源时重置导出状态。 */
    fun bind(media: SelectedMedia) {
        if (selectedMedia == media) return
        selectedMedia = media
        uiState.value = CropUiState(videoName = media.name, cutPhase = CutPhase.Idle)
    }

    /** 按 [crop] 区域剪切导出（保持源格式容器，质量取「最合适」档）；导出中重复调用被忽略。 */
    fun cut(crop: PlayerEffect.Crop) {
        val media = selectedMedia ?: return
        if (uiState.value.cutPhase is CutPhase.Cutting) return
        val filter = crop.normalized().toVideoFilter()
        viewModelScope.launch {
            uiState.update { it.copy(cutPhase = CutPhase.Cutting(progress = null)) }
            val result = videoConvertRepo.convert(
                VideoConvertRequest(
                    uri = Uri.parse(media.uri),
                    displayName = media.name,
                    format = videoOutputFormat(media.name),
                    quality = VideoConvertQuality.SUITABLE,
                    videoFilter = filter,
                ),
            ) { progress ->
                // 回调来自 FFmpeg 线程；StateFlow.update 原子且线程安全
                uiState.update { it.copy(cutPhase = CutPhase.Cutting(progress)) }
            }
            uiState.update {
                it.copy(
                    cutPhase = when (result) {
                        is VideoConvertResult.Success -> CutPhase.Complete
                        is VideoConvertResult.Failure -> CutPhase.Failed(result.error)
                    },
                )
            }
        }
    }
}
