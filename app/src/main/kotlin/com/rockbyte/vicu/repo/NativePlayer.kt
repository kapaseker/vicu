package com.rockbyte.vicu.repo

import android.view.Surface

/** native 引擎事件（回调来自 native 工作线程）。 */
internal sealed interface NativePlayerEvent {
    data class Prepared(
        val width: Int,
        val height: Int,
        val durationMs: Long,
        val hasAudio: Boolean,
    ) : NativePlayerEvent

    data class Position(val positionMs: Long) : NativePlayerEvent
    data object Ended : NativePlayerEvent
    data object Failed : NativePlayerEvent

    /** 音频帧（S16 双声道 48kHz）；由仓库直写 AudioTrack，不经事件流。 */
    data class AudioData(val data: ByteArray, val ptsUs: Long) : NativePlayerEvent
}

/**
 * libvicuplayer JNI 绑定；实现见 [create]。
 *
 * [prepare] 传入的 fd 为 dup 所得、所有权移交 native（avformat 关闭时释放）；
 * 一次会话结束用 [release] 释放，释放后可再次 [prepare] 开始新会话。
 */
internal interface NativePlayer {
    fun interface Listener {
        fun onEvent(event: NativePlayerEvent)
    }

    /** 音频主时钟提供者：返回当前音频播放位置（微秒，媒体时间）；无效时返回 -1。 */
    fun interface AudioClockProvider {
        fun audioClockUs(): Long
    }

    fun setListener(listener: Listener?)
    fun prepare(fd: Int): Int
    fun setSurface(surface: Surface?)
    fun start()
    fun pause()
    fun seek(positionMs: Long)
    fun setAudioClockProvider(provider: AudioClockProvider?)
    fun setFilterGraph(chain: String)
    fun setPlayRange(startMs: Long, endMs: Long)
    fun release()

    companion object {
        fun create(): NativePlayer = NativePlayerImpl()
    }
}

private class NativePlayerImpl : NativePlayer {

    @Volatile
    private var listener: NativePlayer.Listener? = null

    @Volatile
    private var audioClockProvider: NativePlayer.AudioClockProvider? = null

    /** PlayerContext*（0 = 无会话）。 */
    private var nativeHandle: Long = 0

    override fun setListener(listener: NativePlayer.Listener?) {
        this.listener = listener
    }

    override fun prepare(fd: Int): Int = nativePrepare(fd)

    override fun setSurface(surface: Surface?) = nativeSetSurface(surface)

    override fun start() = nativeStart()

    override fun pause() = nativePause()

    override fun seek(positionMs: Long) = nativeSeek(positionMs)

    override fun setAudioClockProvider(provider: NativePlayer.AudioClockProvider?) {
        audioClockProvider = provider
    }

    override fun setFilterGraph(chain: String) = nativeSetFilterGraph(chain)

    override fun setPlayRange(startMs: Long, endMs: Long) = nativeSetPlayRange(startMs, endMs)

    override fun release() = nativeRelease()

    private external fun nativePrepare(fd: Int): Int
    private external fun nativeSetSurface(surface: Surface?)
    private external fun nativeStart()
    private external fun nativePause()
    private external fun nativeSeek(positionMs: Long)
    private external fun nativeSetFilterGraph(chain: String)
    private external fun nativeSetPlayRange(startMs: Long, endMs: Long)
    private external fun nativeRelease()

    // —— native 回调入口（jni_bridge 反射调用，勿改名/删改签名）——

    private fun notifyPrepared(width: Int, height: Int, durationMs: Long, hasAudio: Boolean) {
        listener?.onEvent(NativePlayerEvent.Prepared(width, height, durationMs, hasAudio))
    }

    private fun notifyPosition(positionMs: Long) {
        listener?.onEvent(NativePlayerEvent.Position(positionMs))
    }

    private fun notifyEnded() {
        listener?.onEvent(NativePlayerEvent.Ended)
    }

    private fun notifyError(code: Int, message: String?) {
        listener?.onEvent(NativePlayerEvent.Failed)
    }

    private fun notifyAudioData(data: ByteArray, ptsUs: Long) {
        listener?.onEvent(NativePlayerEvent.AudioData(data, ptsUs))
    }

    private fun audioClockUs(): Long = audioClockProvider?.audioClockUs() ?: -1L

    companion object {
        init {
            System.loadLibrary("vicuplayer")
        }
    }
}
