package com.rockbyte.vicu.page.audiotrim

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rockbyte.vicu.player.PlayerEffect
import com.rockbyte.vicu.repo.*
import com.rockbyte.vicu.ui.component.trim.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface AudioTrimPhase {
    data object Idle : AudioTrimPhase
    data class Trimming(val progress: Float?) : AudioTrimPhase
    data object Complete : AudioTrimPhase
    data class Failed(val error: AudioTrimError) : AudioTrimPhase
}

data class AudioTrimUiState(
    val audioName: String = "",
    val loading: Boolean = true,
    val durationMs: Long = 0,
    val range: PlayerEffect.Trim? = null,
    val sourceError: AudioTrimError? = null,
    val phase: AudioTrimPhase = AudioTrimPhase.Idle,
) {
    val editable: Boolean get() = !loading && sourceError == null && durationMs > 0 && phase !is AudioTrimPhase.Trimming
}

sealed interface AudioWaveformState {
    data object Idle : AudioWaveformState
    data object Loading : AudioWaveformState
    data class Ready(val waveform: AudioWaveform) : AudioWaveformState
    data object Failed : AudioWaveformState
}

class AudioTrimViewModel(
    private val audioTrimRepo: AudioTrimRepo,
    private val previewRepo: AudioPreviewRepo,
    private val waveformRepo: AudioWaveformRepo,
) : ViewModel() {
    val uiState: StateFlow<AudioTrimUiState>
        field = MutableStateFlow(AudioTrimUiState())
    val previewState: StateFlow<AudioPreviewState> = previewRepo.state
    val waveformState: StateFlow<AudioWaveformState>
        field = MutableStateFlow<AudioWaveformState>(AudioWaveformState.Idle)
    private var selectedMedia: SelectedMedia? = null
    private var inspection: Job? = null
    private var waveformLoading: Job? = null
    private var session = 0
    private var waveformRequest = 0

    fun bind(media: SelectedMedia) {
        if (selectedMedia == media) return
        selectedMedia = media
        val currentSession = ++session
        inspection?.cancel()
        waveformLoading?.cancel()
        waveformState.value = AudioWaveformState.Idle
        previewRepo.release()
        uiState.value = AudioTrimUiState(audioName = media.name)
        inspection = viewModelScope.launch {
            val result = audioTrimRepo.inspect(Uri.parse(media.uri))
            if (session != currentSession) return@launch
            when (result) {
                is AudioTrimProbeResult.Success -> {
                    uiState.value = AudioTrimUiState(media.name, false, result.info.durationMs,
                        PlayerEffect.Trim(0, result.info.durationMs))
                    previewRepo.open(Uri.parse(media.uri), 0, result.info.durationMs)
                    retryWaveform()
                }
                is AudioTrimProbeResult.Failure -> uiState.update { it.copy(loading = false, sourceError = result.error) }
            }
        }
    }

    fun retryWaveform() {
        val media = selectedMedia ?: return
        val duration = uiState.value.durationMs
        if (duration <= 0 || uiState.value.sourceError != null) return
        waveformLoading?.cancel()
        val currentSession = session
        val currentRequest = ++waveformRequest
        waveformState.value = AudioWaveformState.Loading
        waveformLoading = viewModelScope.launch {
            val result = waveformRepo.load(Uri.parse(media.uri), duration)
            if (session != currentSession || waveformRequest != currentRequest) return@launch
            waveformState.value = result.fold({ AudioWaveformState.Ready(it) }, { AudioWaveformState.Failed })
        }
    }

    fun selectRange(startMs: Long, endMs: Long): Boolean {
        val state = uiState.value
        if (!state.editable || startMs < 0 || startMs >= endMs || endMs > state.durationMs) return false
        previewRepo.pause()
        previewRepo.setRange(startMs, endMs)
        uiState.update { it.copy(range = PlayerEffect.Trim(startMs, endMs), phase = AudioTrimPhase.Idle) }
        return true
    }

    fun seekTo(positionMs: Long) { if (uiState.value.editable) previewRepo.seekTo(positionMs) }
    fun togglePlayPause() { if (uiState.value.editable) previewRepo.togglePlayPause() }
    fun pause() = previewRepo.pause()

    internal fun confirm(start: String, end: String): TrimInputError? {
        val state = uiState.value
        if (state.phase is AudioTrimPhase.Trimming) return null
        val error = validateTrimInput(start, end, state.durationMs)
        if (error != null) return error
        val media = selectedMedia ?: return TrimInputError.OUT_OF_BOUNDS
        if (!state.editable) return TrimInputError.OUT_OF_BOUNDS
        val startMs = parseTrimTime(start)!!
        val endMs = parseTrimTime(end)!!
        previewRepo.pause()
        previewRepo.setRange(startMs, endMs)
        uiState.update { it.copy(range = PlayerEffect.Trim(startMs, endMs), phase = AudioTrimPhase.Trimming(null)) }
        viewModelScope.launch {
            val result = audioTrimRepo.trim(AudioTrimRequest(Uri.parse(media.uri), media.name, startMs, endMs)) { progress ->
                uiState.update { it.copy(phase = AudioTrimPhase.Trimming(progress)) }
            }
            uiState.update { it.copy(phase = when (result) {
                is AudioTrimResult.Success -> AudioTrimPhase.Complete
                is AudioTrimResult.Failure -> AudioTrimPhase.Failed(result.error)
            }) }
        }
        return null
    }

    fun release() {
        session++
        inspection?.cancel()
        inspection = null
        selectedMedia = null // A retained ViewModel must prepare again when the same file is reopened.
        previewRepo.release()
        waveformLoading?.cancel()
        waveformLoading = null
        waveformState.value = AudioWaveformState.Idle
    }
    override fun onCleared() { release() }
}
