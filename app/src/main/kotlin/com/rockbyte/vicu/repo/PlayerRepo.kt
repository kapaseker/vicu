package com.rockbyte.vicu.repo

import android.view.Surface
import kotlinx.coroutines.flow.Flow

/** 播放失败类型。 */
enum class PlayerError {
    /** 打不开文件 / 无法解析（fd 打开或 prepare 失败）。 */
    OpenFailed,

    /** 播放过程中出错（解码 / EGL 渲染失败）。 */
    PlaybackFailed,
}

/** 播放事件（events 热流）。 */
sealed interface PlayerEvent {
    data class Prepared(val width: Int, val height: Int, val durationMs: Long) : PlayerEvent
    data class Position(val positionMs: Long) : PlayerEvent
    data object Ended : PlayerEvent
    data class Failed(val error: PlayerError) : PlayerEvent
}

/** 原生播放器仓库：管理会话生命周期与事件流。 */
interface PlayerRepo {
    /** 打开媒体并自动起播；结果经 [events] 通知（Prepared / Failed）。 */
    suspend fun open(media: SelectedMedia)

    fun setSurface(surface: Surface?)
    fun play()
    fun pause()

    /** 跳转到指定毫秒（异步：demux 线程冲刷后跳转）。 */
    fun seekTo(positionMs: Long)

    /**
     * 应用效果（WYSIWYG 预览）：crop → native 滤镜链；trim → 播放区间
     * （seek clamp + 到终点停播；当前位置在区间前则跳到区间起点）。
     */
    fun applyEffects(effects: List<EffectSpec>)

    /** 播放结束后从头重播当前媒体。 */
    suspend fun replay()

    /** 结束会话并释放引擎（页面离开时调用）。 */
    fun release()

    /** 播放事件热流；同一时刻仅一个会话在发事件。 */
    val events: Flow<PlayerEvent>
}
