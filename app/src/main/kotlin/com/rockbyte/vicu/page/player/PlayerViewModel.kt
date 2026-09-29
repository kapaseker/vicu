package com.rockbyte.vicu.page.player

import android.net.Uri
import android.view.Surface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rockbyte.vicu.player.PlayerEffect
import com.rockbyte.vicu.player.PlayerError
import com.rockbyte.vicu.player.PlayerEvent
import com.rockbyte.vicu.player.PlayerRepo
import com.rockbyte.vicu.repo.SelectedMedia
import com.rockbyte.vicu.player.normalized
import kotlin.math.abs
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
    val crop: PlayerEffect.Crop? = null,
    val trim: PlayerEffect.Trim? = null,
) {
    /** 当前效果列表（crop 在前，滤镜链顺序稳定）。 */
    val effects: List<PlayerEffect>
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

    /** 最近一次跳转目标；到达该目标前丢弃旧进度回报（见 [acceptPosition]）。 */
    private var pendingSeekMs: Long? = null

    /** 进度条拖拽中：画面随手指刷帧、音频静音；松手才恢复播放。 */
    private var scrubbing = false

    /** 拖拽前是否在播放（松手据此恢复）。 */
    private var resumeAfterScrub = false

    /** 拖拽中最近一次下发的 seek 目标（按 [SCRUB_SEEK_STEP_MS] 步长节流）。 */
    private var lastScrubSeekMs: Long? = null

    init {
        viewModelScope.launch {
            playerRepo.events.collect { event ->
                // 跳转生效前丢弃旧位置，避免 seekbar 先回退再跳
                if (event is PlayerEvent.Position) {
                    if (!acceptPosition(event.positionMs)) return@collect
                } else {
                    pendingSeekMs = null
                }
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
        pendingSeekMs = null
        resetScrub()
        selectedMedia = media
        uiState.value = PlayerUiState(videoName = media.name, phase = PlayerPhase.Preparing)
        viewModelScope.launch { playerRepo.open(Uri.parse(media.uri)) }
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
        pendingSeekMs = null
        resetScrub()
        uiState.update { it.copy(playing = false, positionMs = 0L, phase = PlayerPhase.Preparing) }
        viewModelScope.launch { playerRepo.replay() }
    }

    /** 点按进度条/松手后的跳转：Ended 态跳转即恢复播放，暂停态保持暂停。 */
    fun seekTo(positionMs: Long) {
        val clamped = seekTarget(positionMs) ?: return
        uiState.update { s ->
            val phase = if (s.phase == PlayerPhase.Ended) PlayerPhase.Playing else s.phase
            s.copy(
                positionMs = clamped,
                playing = phase == PlayerPhase.Playing,
                phase = phase,
            )
        }
        // 立即下发：native 侧以单个 seek_request + 最新目标合并重复请求，无需上层防抖；
        // 同时门控旧进度回写，避免 seekbar 先回退再跳
        pendingSeekMs = clamped
        playerRepo.seekTo(clamped)
    }

    /** 开始拖拽进度条：暂停引擎并停在当前帧，音频静音；[uiState].playing 保持不变。 */
    fun scrubStart() {
        if (scrubbing) return
        scrubbing = true
        resumeAfterScrub = uiState.value.playing
        if (resumeAfterScrub) playerRepo.pause()
    }

    /** 拖拽中：进度条乐观跟随手指，画面按 [SCRUB_SEEK_STEP_MS] 步长节流刷帧。 */
    fun scrubTo(positionMs: Long) {
        val clamped = seekTarget(positionMs) ?: return
        uiState.update { it.copy(positionMs = clamped) }
        val last = lastScrubSeekMs
        if (last != null && abs(clamped - last) < SCRUB_SEEK_STEP_MS) return
        lastScrubSeekMs = clamped
        pendingSeekMs = clamped
        playerRepo.seekTo(clamped)
    }

    /** 松手：精确跳转到落点，并按拖拽前的播放态恢复播放。 */
    fun scrubEnd(positionMs: Long) {
        val clamped = seekTarget(positionMs)
        if (clamped != null) {
            val shouldPlay = resumeAfterScrub || uiState.value.phase == PlayerPhase.Ended
            pendingSeekMs = clamped
            uiState.update { s ->
                s.copy(
                    positionMs = clamped,
                    playing = shouldPlay,
                    phase = if (shouldPlay) PlayerPhase.Playing else s.phase,
                )
            }
            playerRepo.seekTo(clamped)
            if (shouldPlay) playerRepo.play()
        }
        scrubbing = false
        resumeAfterScrub = false
        lastScrubSeekMs = null
    }

    /** 跳转目标 clamp 到 trim 区间；时长未知时返回 null。 */
    private fun seekTarget(positionMs: Long): Long? {
        val state = uiState.value
        val duration = state.durationMs
        if (duration <= 0) return null
        val lower = state.trim?.startMs ?: 0L
        val upper = state.trim?.endMs ?: duration
        return positionMs.coerceIn(lower, upper)
    }

    /**
     * 进度回报是否可信：跳转到达 [pendingSeekMs] 附近前丢弃旧位置，避免 seekbar 回退；
     * 到达目标附近即清除门控并恢复跟随。
     */
    private fun acceptPosition(positionMs: Long): Boolean {
        val target = pendingSeekMs ?: return true
        if (abs(positionMs - target) > SEEK_SETTLE_TOLERANCE_MS) return false
        pendingSeekMs = null
        return true
    }

    /** 设置画面裁剪（null 清除）；立即生效于预览滤镜链。 */
    fun setCrop(crop: PlayerEffect.Crop?) {
        uiState.update { it.copy(crop = crop?.normalized()) }
        applyEffects()
    }

    /** 设置时间裁剪（null 清除）；立即生效于播放区间。 */
    fun setTrim(trim: PlayerEffect.Trim?) {
        val duration = uiState.value.durationMs
        uiState.update { state ->
            state.copy(
                trim = trim?.let { t ->
                    PlayerEffect.Trim(
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
        pendingSeekMs = null
        resetScrub()
        selectedMedia = null
        playerRepo.release()
        uiState.update { it.copy(playing = false, phase = PlayerPhase.Idle) }
    }

    private fun resetScrub() {
        scrubbing = false
        resumeAfterScrub = false
        lastScrubSeekMs = null
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

/** 跳转后旧进度回报的丢弃窗口：到达目标 ±该范围即认为跳转生效，恢复跟随。 */
private const val SEEK_SETTLE_TOLERANCE_MS = 500L

/** 拖拽刷帧的 seek 步长：手指移动小于该值不再重发，避免高频 seek 拖垮解码。 */
private const val SCRUB_SEEK_STEP_MS = 100L
