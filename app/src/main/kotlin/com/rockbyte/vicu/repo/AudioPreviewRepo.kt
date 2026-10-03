package com.rockbyte.vicu.repo

import android.net.Uri
import kotlinx.coroutines.flow.StateFlow

enum class AudioPreviewPhase { Idle, Preparing, Ready, Failed }
data class AudioPreviewState(
    val phase: AudioPreviewPhase = AudioPreviewPhase.Idle,
    val positionMs: Long = 0,
    val playing: Boolean = false,
)

interface AudioPreviewRepo {
    val state: StateFlow<AudioPreviewState>
    fun open(uri: Uri, startMs: Long, endMs: Long)
    fun setRange(startMs: Long, endMs: Long)
    fun seekTo(positionMs: Long)
    fun togglePlayPause()
    fun pause()
    fun release()
}

/** Platform playback and audio focus. All operations and callbacks run on the main thread. */
internal interface AudioPreviewStore {
    val positionMs: Long
    fun open(uri: Uri, onPrepared: () -> Unit, onStopped: (completed: Boolean) -> Unit, onFailure: () -> Unit)
    fun play(): Boolean
    fun pause()
    fun seekTo(positionMs: Long, onComplete: () -> Unit)
    fun release()
}
