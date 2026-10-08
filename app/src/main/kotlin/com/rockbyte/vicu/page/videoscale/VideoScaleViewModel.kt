package com.rockbyte.vicu.page.videoscale

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

/** 缩放导出阶段。 */
sealed interface ScalePhase {
    data object Idle : ScalePhase

    /** progress 为 null 表示源时长未知，无法计算百分比。 */
    data class Scaling(val progress: Float?) : ScalePhase
    data object Complete : ScalePhase
    data class Failed(val error: VideoConvertError) : ScalePhase
}

data class VideoScaleUiState(val scalePhase: ScalePhase = ScalePhase.Idle)

/**
 * 缩放页状态：预览播放由 [com.rockbyte.vicu.page.player.PlayerViewModel] 承担（源宽高亦来自其
 * playerState），本 VM 只负责按目标尺寸重编码导出（复用 [VideoConvertRepo] 转换管线：
 * 进度 + 失败回滚 + 媒体库发布）。
 */
class VideoScaleViewModel(private val videoConvertRepo: VideoConvertRepo) : ViewModel() {

    val uiState: StateFlow<VideoScaleUiState>
        field = MutableStateFlow(VideoScaleUiState())

    private var selectedMedia: SelectedMedia? = null

    /** 绑定路由传入的视频（幂等）：换源时重置导出状态。 */
    fun bind(media: SelectedMedia) {
        if (selectedMedia == media) return
        selectedMedia = media
        uiState.value = VideoScaleUiState()
    }

    /**
     * 缩放到输出尺寸（宽 [targetWidth] × 高 [targetHeight]，均需为正偶数）导出
     * （保持源格式容器，质量取「最合适」档）；导出中重复调用被忽略。
     */
    fun scale(targetWidth: Int, targetHeight: Int) {
        val media = selectedMedia ?: return
        if (uiState.value.scalePhase is ScalePhase.Scaling) return
        viewModelScope.launch {
            uiState.update { it.copy(scalePhase = ScalePhase.Scaling(progress = null)) }
            val result = videoConvertRepo.convert(
                VideoConvertRequest(
                    uri = Uri.parse(media.uri),
                    displayName = media.name,
                    format = videoOutputFormat(media.name),
                    quality = VideoConvertQuality.SUITABLE,
                    videoFilter = "scale=$targetWidth:$targetHeight",
                ),
            ) { progress ->
                // 回调来自 FFmpeg 线程；StateFlow.update 原子且线程安全
                uiState.update { it.copy(scalePhase = ScalePhase.Scaling(progress)) }
            }
            uiState.update {
                it.copy(
                    scalePhase = when (result) {
                        is VideoConvertResult.Success -> ScalePhase.Complete
                        is VideoConvertResult.Failure -> ScalePhase.Failed(result.error)
                    },
                )
            }
        }
    }
}

/**
 * 计算输出尺寸：高取 [targetHeight]（null 则取源高）；宽按 [ratio]（null 则按源比例）推导。
 * 宽高均向下取偶且下限 2（libx264 + yuv420p 要求偶数维度）。
 */
internal fun scaleOutputSize(
    sourceWidth: Int,
    sourceHeight: Int,
    ratio: Float?,
    targetHeight: Int?,
): Pair<Int, Int> {
    require(sourceWidth > 0 && sourceHeight > 0)
    val effectiveRatio = ratio ?: (sourceWidth.toFloat() / sourceHeight)
    val evenDown = 1.inv() // 0xFFFFFFFE：清最低位即向下取偶
    val height = ((targetHeight ?: sourceHeight) and evenDown).coerceAtLeast(2)
    val width = ((height * effectiveRatio).toInt() and evenDown).coerceAtLeast(2)
    return width to height
}
