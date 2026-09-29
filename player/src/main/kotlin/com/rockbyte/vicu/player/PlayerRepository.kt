package com.rockbyte.vicu.player

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.net.Uri
import android.view.Surface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class PlayerRepository internal constructor(
    private val context: Context,
    private val playerFactory: () -> NativePlayer = { NativePlayer.create() },
    private val audioTrackFactory: () -> AudioTrack = { createAudioTrack() },
) : PlayerRepo {

    constructor(context: Context) : this(
        context = context.applicationContext,
        playerFactory = { NativePlayer.create() },
        audioTrackFactory = { createAudioTrack() },
    )

    private val _events = MutableSharedFlow<PlayerEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val events: Flow<PlayerEvent> = _events.asSharedFlow()

    // player 与 current 只在 open/replay（串行调用）中变更
    private var player: NativePlayer? = null
    private var current: Uri? = null
    private var surface: Surface? = null
    private var playing = false // 引擎播放态：seek 后是否恢复音频输出
    private var lastPositionMs = 0L // 最近进度（applyEffects 判断是否需跳到区间起点）
    private val playbackFailed = AtomicBoolean(false)
    private val seekTargetUs = AtomicLong(0)
    private val audioEpoch = AtomicInteger(0) // seek 代际：与 native perform_seek 逐一对应

    // 音频输出（S16 双声道 48kHz，与 native 重采样输出一致）
    private val audioLock = Any()
    private var track: AudioTrack? = null
    private var audioStartPtsUs = 0L   // flush 后首个写入音频的媒体 PTS（时钟基准）
    private var awaitingAudioStart = false

    private val audioClock = NativePlayer.AudioClockProvider {
        synchronized(audioLock) {
            val t = track ?: return@AudioClockProvider -1L
            if (awaitingAudioStart || t.playState != AudioTrack.PLAYSTATE_PLAYING) {
                return@AudioClockProvider -1L
            }
            audioStartPtsUs + t.playbackHeadPosition * 1_000_000L / AUDIO_SAMPLE_RATE
        }
    }

    private val listener = NativePlayer.Listener { event ->
        when (event) {
            is NativePlayerEvent.AudioData -> writeAudio(event.data, event.ptsUs, event.epoch)
            is NativePlayerEvent.Prepared -> {
                if (event.hasAudio && !ensureAudioTrack()) {
                    failPlayback()
                    return@Listener
                }
                _events.tryEmit(
                    PlayerEvent.Prepared(event.width, event.height, event.durationMs),
                )
            }
            is NativePlayerEvent.Position -> {
                lastPositionMs = event.positionMs
                _events.tryEmit(PlayerEvent.Position(event.positionMs))
            }
            NativePlayerEvent.Ended -> {
                stopAudioPlayback() // 播完即停，截掉缓冲尾音
                _events.tryEmit(PlayerEvent.Ended)
            }
            NativePlayerEvent.Failed -> failPlayback()
        }
    }

    override suspend fun open(uri: Uri) {
        if (current == uri) return
        release()
        playbackFailed.set(false)
        seekTargetUs.set(0)
        audioEpoch.set(0)
        withContext(Dispatchers.IO) {
            val opened = try {
                openSession(uri)
            } catch (e: Exception) {
                null
            }
            if (opened == null && !playbackFailed.get()) {
                _events.tryEmit(PlayerEvent.Failed(PlayerError.OpenFailed))
            }
        }
    }

    override fun setSurface(surface: Surface?) {
        this.surface = surface
        player?.setSurface(surface)
    }

    override fun play() {
        playing = true
        synchronized(audioLock) { track?.play() }
        player?.start()
    }

    override fun pause() {
        playing = false
        player?.pause()
        synchronized(audioLock) { track?.pause() }
    }

    override fun seekTo(positionMs: Long) {
        if (positionMs < 0) return
        seekTargetUs.set(positionMs * 1000)
        // 先递增代际再冲音频输出：滞留的 seek 前音频帧（旧代际）一律丢弃，
        // 消除向后 seek 时旧帧 pts ≥ 新目标污染时钟基准的可能
        audioEpoch.incrementAndGet()
        // 先冲音频输出再让引擎跳转：新音频到达即重建时钟基准；
        // 暂停态 seek 只冲不播，与视频暂停态保持一致
        synchronized(audioLock) {
            track?.let {
                it.pause()
                it.flush()
                if (playing) it.play()
            }
            awaitingAudioStart = true
        }
        player?.seek(positionMs)
    }

    override suspend fun replay() {
        val uri = current ?: return
        release()
        open(uri)
    }

    override fun applyEffects(effects: List<PlayerEffect>) {
        val p = player ?: return // 会话未就绪；Prepared 后由上层重新应用
        p.setFilterGraph(effects.toPreviewFilterChain())
        val trim = effects.filterIsInstance<PlayerEffect.Trim>().firstOrNull()
        if (trim != null) {
            p.setPlayRange(trim.startMs, trim.endMs)
            // 当前位置不在新区间内 → 跳到区间起点
            if (lastPositionMs < trim.startMs || lastPositionMs >= trim.endMs) {
                seekTo(trim.startMs)
            }
        } else {
            p.setPlayRange(0L, -1L)
        }
    }

    override fun release() {
        stopAudio()
        player?.release()
        player = null
        current = null
        playing = false
        lastPositionMs = 0L
    }

    /** 打开 fd、prepare 并起播；失败返回 null（引擎已清理）。 */
    private fun openSession(uri: Uri): NativePlayer? {
        val fd = openFd(uri)
        val newPlayer = playerFactory()
        newPlayer.setListener(listener)
        newPlayer.setAudioClockProvider(audioClock)
        val rc = try {
            newPlayer.prepare(fd)
        } catch (e: Throwable) {
            -1
        }
        if (rc != 0 || playbackFailed.get()) {
            newPlayer.release()
            return null
        }
        newPlayer.setSurface(surface)
        newPlayer.start()
        player = newPlayer
        current = uri
        playing = true
        return newPlayer
    }

    /** SAF 打开并 dup：分离出的 fd 所有权移交 native（avformat 关闭时释放）。 */
    private fun openFd(uri: Uri): Int {
        context.contentResolver.openFileDescriptor(uri, "r").use { pfd ->
            requireNotNull(pfd) { "无法打开 $uri" }
            return pfd.dup().detachFd()
        }
    }

    private fun ensureAudioTrack(): Boolean = synchronized(audioLock) {
        if (track != null) return true
        val audioTrack = runCatching(audioTrackFactory).getOrNull() ?: return false
        if (runCatching { audioTrack.play() }.isFailure) {
            audioTrack.release()
            return false
        }
        track = audioTrack
        awaitingAudioStart = true
        true
    }

    /** 音频帧直写 AudioTrack（native 音频线程回调；写满阻塞即自然背压）。 */
    private fun writeAudio(data: ByteArray, ptsUs: Long, epoch: Int) {
        // 代际不符：解码期间发生 seek 的滞留旧帧（pts 守卫对向后 seek 拦不住）
        if (epoch != audioEpoch.get()) return
        if (ptsUs < seekTargetUs.get()) return
        val audioTrack = synchronized(audioLock) {
            val t = track ?: return
            if (awaitingAudioStart) {
                audioStartPtsUs = ptsUs
                awaitingAudioStart = false
            }
            t
        }
        if (audioTrack.write(data, 0, data.size) < 0) failPlayback()
    }

    private fun failPlayback() {
        if (!playbackFailed.compareAndSet(false, true)) return
        stopAudio()
        _events.tryEmit(PlayerEvent.Failed(PlayerError.PlaybackFailed))
    }

    /** 停止播放但保留输出（Ended 截尾音）；恢复需 play()。 */
    private fun stopAudioPlayback() {
        synchronized(audioLock) { track?.pause() }
    }

    /** 释放音频输出（换源/会话结束）；先于引擎释放，解除写阻塞。 */
    private fun stopAudio() {
        synchronized(audioLock) {
            track?.let {
                runCatching { it.stop() }
                runCatching { it.release() }
            }
            track = null
            awaitingAudioStart = false
        }
        player?.setAudioClockProvider(null)
    }
}

private const val AUDIO_SAMPLE_RATE = 48000

private fun createAudioTrack(): AudioTrack {
    val minBuffer = AudioTrack.getMinBufferSize(
        AUDIO_SAMPLE_RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT,
    )
    require(minBuffer > 0) { "Unsupported player audio format" }
    val audioTrack = AudioTrack(
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
            .build(),
        AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(AUDIO_SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
            .build(),
        minBuffer * 4,
        AudioTrack.MODE_STREAM,
        AudioManager.AUDIO_SESSION_ID_GENERATE,
    )
    if (audioTrack.state != AudioTrack.STATE_INITIALIZED) {
        audioTrack.release()
        error("AudioTrack initialization failed")
    }
    return audioTrack
}
