package com.rockbyte.vicu.page.trim

import com.rockbyte.vicu.ui.component.trim.*
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rockbyte.vicu.player.PlayerEffect
import com.rockbyte.vicu.repo.SelectedMedia
import com.rockbyte.vicu.repo.VideoConvertError
import com.rockbyte.vicu.repo.VideoConvertFormat
import com.rockbyte.vicu.repo.VideoConvertQuality
import com.rockbyte.vicu.repo.VideoConvertRepo
import com.rockbyte.vicu.repo.VideoConvertRequest
import com.rockbyte.vicu.repo.VideoConvertResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface TrimPhase {
    data object Idle : TrimPhase
    data class Trimming(val progress: Float?) : TrimPhase
    data object Complete : TrimPhase
    data class Failed(val error: VideoConvertError) : TrimPhase
}

data class TrimUiState(
    val durationMs: Long = 0,
    val range: PlayerEffect.Trim? = null,
    val phase: TrimPhase = TrimPhase.Idle,
)

class TrimViewModel(private val videoConvertRepo: VideoConvertRepo) : ViewModel() {
    val uiState: StateFlow<TrimUiState>
        field = MutableStateFlow(TrimUiState())
    private var selectedMedia: SelectedMedia? = null

    fun bind(media: SelectedMedia) {
        if (selectedMedia == media) return
        selectedMedia = media
        uiState.value = TrimUiState()
    }

    fun setDuration(durationMs: Long) {
        if (durationMs <= 0 || uiState.value.durationMs == durationMs) return
        uiState.value = TrimUiState(durationMs, PlayerEffect.Trim(0, durationMs))
    }

    fun selectRange(startMs: Long, endMs: Long): Boolean {
        val state = uiState.value
        if (state.phase is TrimPhase.Trimming || startMs < 0 || startMs >= endMs || endMs > state.durationMs) return false
        uiState.update { it.copy(range = PlayerEffect.Trim(startMs, endMs), phase = TrimPhase.Idle) }
        return true
    }

    internal fun confirm(start: String, end: String): TrimInputError? {
        if (uiState.value.phase is TrimPhase.Trimming) return null
        val error = validateTrimInput(start, end, uiState.value.durationMs)
        if (error != null) return error
        val media = selectedMedia ?: return TrimInputError.OUT_OF_BOUNDS
        val range = PlayerEffect.Trim(parseTrimTime(start)!!, parseTrimTime(end)!!)
        // 立即锁定，避免协程启动前的重复确认。
        uiState.update { it.copy(range = range, phase = TrimPhase.Trimming(null)) }
        viewModelScope.launch {
            val extension = media.name.substringAfterLast('.', "").lowercase()
            val format = VideoConvertFormat.entries.firstOrNull { it.extension == extension } ?: VideoConvertFormat.MP4
            val result = videoConvertRepo.convert(
                VideoConvertRequest(Uri.parse(media.uri), media.name, format, VideoConvertQuality.SUITABLE, trim = range),
            ) { progress -> uiState.update { it.copy(phase = TrimPhase.Trimming(progress)) } }
            uiState.update {
                it.copy(phase = when (result) {
                    is VideoConvertResult.Success -> TrimPhase.Complete
                    is VideoConvertResult.Failure -> TrimPhase.Failed(result.error)
                })
            }
        }
        return null
    }
}
