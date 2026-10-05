package com.rockbyte.vicu.page

import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rockbyte.vicu.repo.AudioReplaceError
import com.rockbyte.vicu.repo.AudioReplaceMode
import com.rockbyte.vicu.repo.AudioReplaceRepo
import com.rockbyte.vicu.repo.AudioReplaceRequest
import com.rockbyte.vicu.repo.AudioReplaceResult
import com.rockbyte.vicu.repo.SelectedMedia
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AudioReplaceUiState(
    val videoName: String = "",
    val videoDurationMs: Long? = null,
    val musicName: String? = null,
    val musicDurationMs: Long? = null,
    val mode: AudioReplaceMode = AudioReplaceMode.TRUNCATE,
    val phase: ReplacePhase = ReplacePhase.Idle,
)

sealed interface ReplacePhase {
    data object Idle : ReplacePhase
    /** 未选音乐。 */
    data object Ready : ReplacePhase
    data object ProbingMusic : ReplacePhase
    /** progress 为 null 表示期望输出时长未知，无法计算百分比。 */
    data class Converting(val progress: Float?) : ReplacePhase
    data object Complete : ReplacePhase
    data class Failed(val error: AudioReplaceError) : ReplacePhase
}

/** 替换音轨页状态：音乐选择 + 时长适配策略 + 替换流程。 */
class AudioReplaceViewModel(private val audioReplaceRepo: AudioReplaceRepo) : ViewModel() {

    val uiState: StateFlow<AudioReplaceUiState>
        field = MutableStateFlow(AudioReplaceUiState())

    private var selectedMedia: SelectedMedia? = null
    private var musicUri: Uri? = null

    /** 绑定路由传入的视频（幂等）：换源时重置状态并探测视频时长。 */
    fun bind(media: SelectedMedia) {
        if (selectedMedia == media) return
        selectedMedia = media
        uiState.value = AudioReplaceUiState(videoName = media.name, phase = ReplacePhase.Ready)
        viewModelScope.launch {
            val durationMs = audioReplaceRepo.probeVideoDurationMs(media.uri.toUri())
            // 只在仍是同一视频时回填，避免换源竞态串数据
            if (selectedMedia == media) {
                uiState.update { state ->
                    withStretchFallback(state.copy(videoDurationMs = durationMs))
                }
            }
        }
    }

    /** 选中音乐并探测其时长；探测失败时伸缩两模式不可用，回退截断。 */
    fun selectMusic(uri: Uri, name: String) {
        if (uiState.value.phase is ReplacePhase.Converting) return
        musicUri = uri
        uiState.update {
            it.copy(musicName = name, musicDurationMs = null, phase = ReplacePhase.ProbingMusic)
        }
        viewModelScope.launch {
            val durationMs = audioReplaceRepo.probeMusicDurationMs(uri)
            if (musicUri == uri) {
                uiState.update { state ->
                    withStretchFallback(
                        state.copy(musicDurationMs = durationMs, phase = ReplacePhase.Ready)
                    )
                }
            }
        }
    }

    fun selectMode(mode: AudioReplaceMode) {
        if (uiState.value.phase is ReplacePhase.Converting) return
        uiState.update { it.copy(mode = mode, phase = ReplacePhase.Ready) }
    }

    fun replace() {
        val media = selectedMedia ?: return
        val uri = musicUri ?: return
        val phase = uiState.value.phase
        if (phase is ReplacePhase.Converting || phase is ReplacePhase.ProbingMusic) return
        val state = uiState.value
        viewModelScope.launch {
            uiState.update { it.copy(phase = ReplacePhase.Converting(progress = null)) }
            val result = audioReplaceRepo.replace(
                AudioReplaceRequest(media.uri.toUri(), media.name, uri, state.mode),
            ) { progress ->
                // 回调来自 FFmpeg 线程；StateFlow.update 原子且线程安全
                uiState.update { it.copy(phase = ReplacePhase.Converting(progress)) }
            }
            uiState.update {
                it.copy(
                    phase = when (result) {
                        is AudioReplaceResult.Success -> ReplacePhase.Complete
                        is AudioReplaceResult.Failure -> ReplacePhase.Failed(result.error)
                    },
                )
            }
        }
    }
}

/** 伸缩两模式依赖两路时长已知；时长缺失时回退截断，避免出现选中的禁用项。 */
private fun withStretchFallback(state: AudioReplaceUiState): AudioReplaceUiState {
    val stretchable = state.videoDurationMs != null && state.musicDurationMs != null
    return if (!stretchable && state.mode != AudioReplaceMode.TRUNCATE) {
        state.copy(mode = AudioReplaceMode.TRUNCATE)
    } else {
        state
    }
}
