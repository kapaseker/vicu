package com.rockbyte.vicu.repo

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock

@OptIn(ExperimentalCoroutinesApi::class)
class AudioPreviewRepositoryTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = FakePreviewStore()
    private lateinit var repo: AudioPreviewRepository
    @Before fun setUp() { Dispatchers.setMain(dispatcher); repo = AudioPreviewRepository(store) }
    @After fun tearDown() { repo.release(); Dispatchers.resetMain() }
    private fun open() { repo.open(mock(Uri::class.java), 1000, 5000); store.prepared() }

    @Test fun preparesPausedStopsAtEndAndReplaysFromStart() = runTest(dispatcher) {
        open()
        assertEquals(AudioPreviewPhase.Ready, repo.state.value.phase)
        assertFalse(repo.state.value.playing)
        repo.togglePlayPause(); runCurrent()
        assertTrue(repo.state.value.playing)
        store.positionMs = 5005
        advanceTimeBy(25); runCurrent()
        assertFalse(repo.state.value.playing)
        assertEquals(5000L, repo.state.value.positionMs)
        repo.togglePlayPause(); runCurrent()
        assertTrue(repo.state.value.playing)
        assertEquals(1000L, store.positionMs)
        repo.pause()
    }

    @Test fun rangeEditsPauseAndAllSeeksStayInsideSelection() {
        open(); repo.togglePlayPause()
        repo.setRange(2000, 4000)
        assertFalse(repo.state.value.playing)
        assertEquals(2000L, store.positionMs)
        repo.seekTo(-100); assertEquals(2000L, store.positionMs)
        repo.seekTo(9000); assertEquals(4000L, store.positionMs)
    }

    @Test fun focusLossAndPlaybackFailureStopProgress() = runTest(dispatcher) {
        open(); repo.togglePlayPause(); runCurrent()
        store.positionMs = 1500; store.stopped(false)
        assertFalse(repo.state.value.playing)
        assertEquals(1500L, repo.state.value.positionMs)
        repo.togglePlayPause(); store.failed(); advanceTimeBy(100); runCurrent()
        assertEquals(AudioPreviewPhase.Failed, repo.state.value.phase)
        assertFalse(repo.state.value.playing)
    }

    @Test fun naturalCompletionReplaysEvenWhenPlatformDurationDiffersFromProbe() {
        open(); repo.togglePlayPause()
        store.positionMs = 4980; store.stopped(true)
        assertEquals(5000L, repo.state.value.positionMs)
        repo.togglePlayPause()
        assertEquals(1000L, store.positionMs)
        repo.pause()
    }

    @Test fun pendingSeekCannotStartPlaybackAfterPauseOrRelease() {
        store.delayedSeeks = true
        open(); repo.togglePlayPause(); repo.pause(); store.finishSeek()
        assertFalse(store.playing)
        repo.togglePlayPause(); repo.release(); store.finishSeek()
        assertFalse(store.playing)
        assertEquals(AudioPreviewPhase.Idle, repo.state.value.phase)
    }

    @Test fun editingWhilePreparingUsesLatestSelectionWhenPrepared() {
        repo.open(mock(Uri::class.java), 0, 10000)
        repo.setRange(2000, 4000)
        store.prepared()
        assertEquals(2000L, store.positionMs)
        repo.togglePlayPause()
        assertTrue(store.playing)
        assertEquals(2000L, store.positionMs)
        repo.pause()
    }

    @Test fun stalePreparationCannotReopenReleasedSession() {
        repo.open(mock(Uri::class.java), 1000, 5000)
        val stale = store.prepared
        repo.release(); stale()
        assertEquals(AudioPreviewPhase.Idle, repo.state.value.phase)
    }
}

private class FakePreviewStore : AudioPreviewStore {
    override var positionMs = 0L
    var playing = false
    var delayedSeeks = false
    var pendingSeek: (() -> Unit)? = null
    lateinit var prepared: () -> Unit
    lateinit var stopped: (Boolean) -> Unit
    lateinit var failed: () -> Unit
    override fun open(uri: Uri, onPrepared: () -> Unit, onStopped: (Boolean) -> Unit, onFailure: () -> Unit) {
        prepared = onPrepared; stopped = onStopped; failed = onFailure
    }
    override fun play(): Boolean { playing = true; return true }
    override fun pause() { playing = false }
    override fun seekTo(positionMs: Long, onComplete: () -> Unit) {
        this.positionMs = positionMs
        if (delayedSeeks) pendingSeek = onComplete else onComplete()
    }
    fun finishSeek() { pendingSeek?.invoke(); pendingSeek = null }
    override fun release() { playing = false }
}
