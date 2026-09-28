package com.rockbyte.vicu.page.player

import android.view.Surface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rockbyte.vicu.repo.EffectSpec
import com.rockbyte.vicu.repo.PlayerError
import com.rockbyte.vicu.repo.PlayerEvent
import com.rockbyte.vicu.repo.PlayerRepo
import com.rockbyte.vicu.repo.SelectedMedia
import com.rockbyte.vicu.repo.normalized
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlayerUiState(
    val videoName: String = "",
    val playing: Boolean = false,
    val phase: PlayerPhase = PlayerPhase.Idle,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val crop: EffectSpec.Crop? = null,
    val trim: EffectSpec.Trim? = null,
) {
    /** 当前效果列表（crop 在前，滤镜链顺序稳定）。 */
    val effects: List<EffectSpec>
        get() = listOfNotNull(crop, trim)
}

sealed interface PlayerPhase {
    data object Idle : PlayerPhase
    data object Preparing : PlayerPhase
    data object Playing : PlayerPhase
    data object Paused : PlayerPhase
    data object Ended : PlayerPhase
    data class Failed(val error: PlayerError) : PlayerPhase
}

/** 播放页状态：无声预览 + 播放/暂停控制（阶段 2 补进度与 seek）。 */
class PlayerViewModel(private val playerRepo: PlayerRepo) : ViewModel() {

    val uiState: StateFlow<PlayerUiState>
        field = MutableStateFlow(PlayerUiState())

    private var selectedMedia: SelectedMedia? = null

    init {
        viewModelScope.launch {
            playerRepo.events.collect { event ->
                uiState.update { it.onEvent(event) }
                // 新会话就绪（open/replay 后）重放当前效果（引擎侧状态随会话销毁）
                if (event is PlayerEvent.Prepared && uiState.value.effects.isNotEmpty()) {
                    playerRepo.applyEffects(uiState.value.effects)
                }
            }
        }
    }

    /** 绑定路由传入的视频（幂等）：换源或释放后重新打开。 */
    fun bind(media: SelectedMedia) {
        if (selectedMedia == media) return
        selectedMedia = media
        uiState.value = PlayerUiState(videoName = media.name, phase = PlayerPhase.Preparing)
        viewModelScope.launch { playerRepo.open(media) }
    }

    fun setSurface(surface: Surface?) {
        playerRepo.setSurface(surface)
    }

    fun togglePlayPause() {
        when (uiState.value.phase) {
            PlayerPhase.Playing -> {
                playerRepo.pause()
                uiState.update { it.copy(playing = false, phase = PlayerPhase.Paused) }
            }
            PlayerPhase.Paused -> {
                playerRepo.play()
                uiState.update { it.copy(playing = true, phase = PlayerPhase.Playing) }
            }
            PlayerPhase.Ended -> replay()
            else -> Unit
        }
    }

    fun replay() {
        if (uiState.value.phase == PlayerPhase.Preparing) return
        uiState.update { it.copy(playing = false, positionMs = 0L, phase = PlayerPhase.Preparing) }
        viewModelScope.launch { playerRepo.replay() }
    }

    /** 拖拽松手后的跳转：Ended 态跳转即恢复播放，暂停态保持暂停。 */
    fun seekTo(positionMs: Long) {
        val state = uiState.value
        val duration = state.durationMs
        if (duration <= 0) return
        val trim = state.trim
        val lower = trim?.startMs ?: 0L
        val upper = trim?.endMs ?: duration
        val clamped = positionMs.coerceIn(lower, upper)
        uiState.update { s ->
            val phase = if (s.phase == PlayerPhase.Ended) PlayerPhase.Playing else s.phase
            s.copy(
                positionMs = clamped,
                playing = phase == PlayerPhase.Playing,
                phase = phase,
            )
        }
        playerRepo.seekTo(clamped)
    }

    /** 设置画面裁剪（null 清除）；立即生效于预览滤镜链。 */
    fun setCrop(crop: EffectSpec.Crop?) {
        uiState.update { it.copy(crop = crop?.normalized()) }
        applyEffects()
    }

    /** 设置时间裁剪（null 清除）；立即生效于播放区间。 */
    fun setTrim(trim: EffectSpec.Trim?) {
        val duration = uiState.value.durationMs
        uiState.update { state ->
            state.copy(
                trim = trim?.let { t ->
                    EffectSpec.Trim(
                        startMs = t.startMs.coerceIn(0L, duration),
                        endMs = t.endMs.coerceIn(0L, duration),
                    )
                }
            )
        }
        applyEffects()
    }

    private fun applyEffects() {
        playerRepo.applyEffects(uiState.value.effects)
    }

    /** 页面离开时释放引擎；重进页面经 [bind] 重新打开。 */
    fun release() {
        selectedMedia = null
        playerRepo.release()
        uiState.update { it.copy(playing = false, phase = PlayerPhase.Idle) }
    }

    override fun onCleared() {
        playerRepo.release()
    }

    private fun PlayerUiState.onEvent(event: PlayerEvent): PlayerUiState =
        when (event) {
            is PlayerEvent.Prepared -> copy(
                videoWidth = event.width,
                videoHeight = event.height,
                durationMs = event.durationMs,
                playing = true,
                phase = PlayerPhase.Playing,
            )
            is PlayerEvent.Position -> copy(positionMs = event.positionMs)
            PlayerEvent.Ended -> copy(playing = false, phase = PlayerPhase.Ended)
            is PlayerEvent.Failed -> copy(playing = false, phase = PlayerPhase.Failed(event.error))
        }
}
