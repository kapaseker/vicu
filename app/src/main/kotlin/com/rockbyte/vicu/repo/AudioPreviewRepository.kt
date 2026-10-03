package com.rockbyte.vicu.repo

import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Owns the selected playback interval independently of export availability. */
internal class AudioPreviewRepository(private val store: AudioPreviewStore) : AudioPreviewRepo {
    override val state: StateFlow<AudioPreviewState>
        field = MutableStateFlow(AudioPreviewState())
    private val scope = CoroutineScope(Dispatchers.Main.immediate)
    private var progress: Job? = null
    private var startMs = 0L
    private var endMs = 1L
    private var session = 0
    private var operation = 0
    private var playRequested = false

    override fun open(uri: Uri, startMs: Long, endMs: Long) {
        release()
        this.startMs = startMs
        this.endMs = endMs
        val currentSession = session
        state.value = AudioPreviewState(AudioPreviewPhase.Preparing, startMs)
        store.open(uri, onPrepared = {
            if (currentSession == session) {
                state.value = AudioPreviewState(AudioPreviewPhase.Ready, this.startMs)
                seek(this.startMs, false)
            }
        }, onStopped = { completed ->
            if (currentSession == session) {
                pause()
                if (completed) state.value = state.value.copy(positionMs = this.endMs)
            }
        }, onFailure = {
            if (currentSession == session) {
                pause()
                state.value = state.value.copy(phase = AudioPreviewPhase.Failed)
            }
        })
    }

    override fun setRange(startMs: Long, endMs: Long) {
        require(startMs >= 0 && startMs < endMs)
        pause()
        this.startMs = startMs
        this.endMs = endMs
        if (state.value.phase == AudioPreviewPhase.Ready) seek(state.value.positionMs.coerceIn(startMs, endMs), false)
        else state.value = state.value.copy(positionMs = startMs)
    }

    override fun seekTo(positionMs: Long) {
        if (state.value.phase != AudioPreviewPhase.Ready) return
        val position = positionMs.coerceIn(startMs, endMs)
        seek(position, (state.value.playing || playRequested) && position < endMs)
    }

    override fun togglePlayPause() {
        if (state.value.phase != AudioPreviewPhase.Ready) return
        if (state.value.playing || playRequested) pause()
        else seek(if (state.value.positionMs >= endMs) startMs else state.value.positionMs, true)
    }

    private fun seek(positionMs: Long, resume: Boolean) {
        progress?.cancel()
        store.pause()
        val currentOperation = ++operation
        playRequested = resume
        state.value = state.value.copy(positionMs = positionMs, playing = false)
        store.seekTo(positionMs) {
            if (currentOperation == operation && state.value.phase == AudioPreviewPhase.Ready) {
                playRequested = false
                // Platform seeking may land at a nearby frame. Never start past the selected endpoint.
                val actual = store.positionMs.coerceIn(startMs, endMs)
                state.value = state.value.copy(positionMs = actual)
                if (resume && actual < endMs && store.play()) {
                    state.value = state.value.copy(playing = true)
                    progress = scope.launch {
                        while (state.value.playing) {
                            val position = store.positionMs
                            state.value = state.value.copy(positionMs = position.coerceIn(startMs, endMs))
                            if (position >= endMs) { pause(); break }
                            delay(minOf(20L, endMs - position).coerceAtLeast(1))
                        }
                    }
                }
            }
        }
    }

    override fun pause() {
        operation++
        playRequested = false
        progress?.cancel()
        progress = null
        if (state.value.phase == AudioPreviewPhase.Ready) {
            store.pause()
            state.value = state.value.copy(positionMs = store.positionMs.coerceIn(startMs, endMs), playing = false)
        } else state.value = state.value.copy(playing = false)
    }

    override fun release() {
        session++
        operation++
        playRequested = false
        progress?.cancel()
        progress = null
        store.release()
        state.value = AudioPreviewState()
    }
}
