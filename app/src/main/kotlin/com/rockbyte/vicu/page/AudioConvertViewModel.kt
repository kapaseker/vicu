package com.rockbyte.vicu.page

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rockbyte.vicu.repo.AudioConvertError
import com.rockbyte.vicu.repo.AudioConvertFormat
import com.rockbyte.vicu.repo.AudioConvertQuality
import com.rockbyte.vicu.repo.AudioConvertRepo
import com.rockbyte.vicu.repo.AudioConvertRequest
import com.rockbyte.vicu.repo.AudioConvertResult
import com.rockbyte.vicu.repo.SelectedMedia
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.core.net.toUri

data class AudioConvertUiState(
    val audioName: String = "",
    val format: AudioConvertFormat = AudioConvertFormat.MP3,
    val quality: AudioConvertQuality = AudioConvertQuality.SUITABLE,
    val phase: AudioConvertPhase = AudioConvertPhase.Idle,
)

sealed interface AudioConvertPhase {
    data object Idle : AudioConvertPhase
    data object Ready : AudioConvertPhase
    /** progress 为 null 表示源时长未知，无法计算百分比。 */
    data class Converting(val progress: Float?) : AudioConvertPhase
    data object Complete : AudioConvertPhase
    data class Failed(val error: AudioConvertError) : AudioConvertPhase
}

/** 音频转换页状态：格式/质量选择 + 转换流程。 */
class AudioConvertViewModel(private val audioConvertRepo: AudioConvertRepo) : ViewModel() {

    val uiState: StateFlow<AudioConvertUiState>
        field = MutableStateFlow(AudioConvertUiState())

    private var selectedMedia: SelectedMedia? = null

    /** 绑定路由传入的音频（幂等）：换源时重置状态。 */
    fun bind(media: SelectedMedia) {
        if (selectedMedia == media) return
        selectedMedia = media
        uiState.value = AudioConvertUiState(audioName = media.name, phase = AudioConvertPhase.Ready)
    }

    fun selectFormat(format: AudioConvertFormat) {
        uiState.update { it.copy(format = format, phase = AudioConvertPhase.Ready) }
    }

    fun selectQuality(quality: AudioConvertQuality) {
        uiState.update { it.copy(quality = quality, phase = AudioConvertPhase.Ready) }
    }

    fun convert() {
        val media = selectedMedia ?: return
        if (uiState.value.phase is AudioConvertPhase.Converting) return
        val state = uiState.value
        viewModelScope.launch {
            uiState.update { it.copy(phase = AudioConvertPhase.Converting(progress = null)) }
            val result = audioConvertRepo.convert(
                AudioConvertRequest(media.uri.toUri(), state.audioName, state.format, state.quality),
            ) { progress ->
                // 回调来自 FFmpeg 线程；StateFlow.update 原子且线程安全
                uiState.update { it.copy(phase = AudioConvertPhase.Converting(progress)) }
            }
            uiState.update {
                it.copy(
                    phase = when (result) {
                        is AudioConvertResult.Success -> AudioConvertPhase.Complete
                        is AudioConvertResult.Failure -> AudioConvertPhase.Failed(result.error)
                    },
                )
            }
        }
    }
}
