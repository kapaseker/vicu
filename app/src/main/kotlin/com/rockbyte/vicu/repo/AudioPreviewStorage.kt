package com.rockbyte.vicu.repo

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri

/** A page-owned player; it does not keep playing or resume automatically after focus loss. */
internal class AudioPreviewStorage(context: Context) : AudioPreviewStore {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    private var player: MediaPlayer? = null
    private var prepared = false
    private var stopped: (Boolean) -> Unit = {}
    private var failed: () -> Unit = {}
    private data class Seek(val positionMs: Long, val complete: () -> Unit)
    private var activeSeek: Seek? = null
    private var pendingSeek: Seek? = null
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener { change ->
            if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT ||
                change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
                pause()
                stopped(false)
            }
        }.build()

    override val positionMs: Long get() = if (prepared) try {
        player?.currentPosition?.toLong() ?: 0
    } catch (_: IllegalStateException) { 0 } else 0

    override fun open(uri: Uri, onPrepared: () -> Unit, onStopped: (completed: Boolean) -> Unit, onFailure: () -> Unit) {
        release()
        stopped = onStopped
        failed = onFailure
        try {
            val instance = MediaPlayer()
            player = instance
            instance.setAudioAttributes(attributes)
            instance.setOnPreparedListener {
                if (player === instance) { prepared = true; onPrepared() }
            }
            instance.setOnCompletionListener { if (player === instance) stopped(true) }
            instance.setOnErrorListener { _, _, _ ->
                if (player === instance) failPlayback()
                true
            }
            instance.setOnSeekCompleteListener {
                if (player === instance) {
                    val completed = activeSeek
                    activeSeek = null
                    val next = pendingSeek
                    pendingSeek = null
                    if (next != null) startSeek(next) else completed?.complete?.invoke()
                }
            }
            instance.setDataSource(appContext, uri)
            instance.prepareAsync()
        } catch (_: Exception) { failPlayback() }
    }

    override fun play(): Boolean {
        if (!prepared) return false
        if (audioManager.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return false
        return try { player!!.start(); true } catch (_: IllegalStateException) { failPlayback(); false }
    }

    override fun pause() {
        // pause() is invalid in Prepared state; only a started player needs pausing.
        if (prepared) try { if (player?.isPlaying == true) player?.pause() } catch (_: IllegalStateException) { }
        audioManager.abandonAudioFocusRequest(focus)
    }

    override fun seekTo(positionMs: Long, onComplete: () -> Unit) {
        if (!prepared) return
        val seek = Seek(positionMs, onComplete)
        if (activeSeek != null) pendingSeek = seek else startSeek(seek)
    }

    private fun startSeek(seek: Seek) {
        try {
            activeSeek = seek
            player!!.seekTo(seek.positionMs, MediaPlayer.SEEK_CLOSEST)
        } catch (_: IllegalStateException) { failPlayback() }
    }

    private fun failPlayback() {
        val callback = failed
        release()
        callback()
    }

    override fun release() {
        prepared = false
        activeSeek = null
        pendingSeek = null
        player?.release()
        player = null
        audioManager.abandonAudioFocusRequest(focus)
        stopped = {}
        failed = {}
    }
}
