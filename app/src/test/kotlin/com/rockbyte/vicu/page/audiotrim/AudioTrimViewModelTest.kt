package com.rockbyte.vicu.page.audiotrim

import android.net.Uri
import com.rockbyte.vicu.player.PlayerEffect
import com.rockbyte.vicu.repo.*
import com.rockbyte.vicu.ui.component.trim.TrimInputError
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.Mockito.mock

@OptIn(ExperimentalCoroutinesApi::class)
class AudioTrimViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val uri: Uri = mock(Uri::class.java)
    private lateinit var uriMock: MockedStatic<Uri>
    private val media = SelectedMedia("content://media/audio/1", "audio.mp3", MediaKind.AUDIO)
    private val repo = FakeTrimRepo()
    private val preview = FakePreviewRepo()
    private val waveform = FakeWaveformRepo()
    private lateinit var vm: AudioTrimViewModel
    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        uriMock = Mockito.mockStatic(Uri::class.java)
        uriMock.`when`<Uri> { Uri.parse(anyString()) }.thenReturn(uri)
        vm = AudioTrimViewModel(repo, preview, waveform)
    }
    @After fun tearDown() { vm.release(); uriMock.close(); Dispatchers.resetMain() }
    private fun bind() { vm.bind(media); dispatcher.scheduler.advanceUntilIdle() }

    @Test fun probeSelectsWholeFileAndPreviewFailureDoesNotBlockExport() {
        preview.state.value = AudioPreviewState(phase = AudioPreviewPhase.Failed)
        bind()
        assertEquals(10000L, vm.uiState.value.durationMs)
        assertEquals(PlayerEffect.Trim(0, 10000), vm.uiState.value.range)
        assertTrue(vm.uiState.value.editable)
        assertNull(vm.confirm("1.5", "5")); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(AudioTrimPhase.Complete, vm.uiState.value.phase)
        assertSame(uri, repo.requests.single().uri)
        assertEquals(1500L, repo.requests.single().startMs)
    }

    @Test fun invalidInputKeepsRangeAndRepeatedConfirmIsLockedImmediately() {
        bind(); assertTrue(vm.selectRange(1000, 5000))
        assertEquals(TrimInputError.ORDER, vm.confirm("5", "1"))
        assertEquals(PlayerEffect.Trim(1000, 5000), vm.uiState.value.range)
        repo.gate = CompletableDeferred()
        assertNull(vm.confirm("1", "5")); vm.confirm("1", "5")
        assertFalse(vm.selectRange(0, 10000)); assertFalse(vm.uiState.value.editable)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, repo.requests.size)
        assertEquals(AudioTrimPhase.Trimming(0.5f), vm.uiState.value.phase)
        repo.gate!!.complete(Unit); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(AudioTrimPhase.Complete, vm.uiState.value.phase)
    }

    @Test fun failedExportCanBeRetried() {
        bind(); repo.result = AudioTrimResult.Failure(AudioTrimError.TrimFailed)
        vm.confirm("0", "1"); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(AudioTrimPhase.Failed(AudioTrimError.TrimFailed), vm.uiState.value.phase)
        repo.result = AudioTrimResult.Success(uri)
        vm.confirm("0", "1"); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(AudioTrimPhase.Complete, vm.uiState.value.phase)
        assertEquals(2, repo.requests.size)
    }

    @Test fun probeFailureDisablesEditingAndNeverExports() {
        repo.probeResult = AudioTrimProbeResult.Failure(AudioTrimError.UnsupportedFormat)
        bind()
        assertFalse(vm.uiState.value.editable)
        assertEquals(AudioTrimError.UnsupportedFormat, vm.uiState.value.sourceError)
        assertEquals(TrimInputError.OUT_OF_BOUNDS, vm.confirm("0", "1"))
        assertTrue(repo.requests.isEmpty())
    }

    @Test fun reopeningSameFileAfterReleasePreparesANewSession() {
        bind(); vm.release(); vm.bind(media); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(2, repo.inspections)
        assertEquals(2, preview.opens)
        assertTrue(vm.uiState.value.editable)
    }

    @Test fun bindIsIdempotentAndRangeEditsPausePreview() {
        bind(); vm.bind(media); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, repo.inspections)
        vm.selectRange(2000, 4000)
        assertEquals(PlayerEffect.Trim(2000, 4000), preview.range)
        assertTrue(preview.pauses > 0)
    }

    @Test fun waveformFailureDoesNotBlockExportAndRetryKeepsSelection() {
        waveform.result = Result.failure(IllegalStateException("decode"))
        bind()
        assertEquals(AudioWaveformState.Failed, vm.waveformState.value)
        assertTrue(vm.uiState.value.editable)
        vm.selectRange(1000, 3000)
        waveform.result = Result.success(AudioWaveform(floatArrayOf(0.5f), 10.0, 10.0))
        vm.retryWaveform(); dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.waveformState.value is AudioWaveformState.Ready)
        assertEquals(PlayerEffect.Trim(1000, 3000), vm.uiState.value.range)
        assertNull(vm.confirm("1", "3")); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(AudioTrimPhase.Complete, vm.uiState.value.phase)
    }

    @Test fun switchingFileIgnoresLateWaveformAndReleaseDropsPeaks() {
        val old = CompletableDeferred<Result<AudioWaveform>>()
        waveform.gate = old
        bind()
        assertEquals(AudioWaveformState.Loading, vm.waveformState.value)
        waveform.gate = null
        vm.bind(media.copy(uri = "content://media/audio/2", name = "second.mp3"))
        dispatcher.scheduler.advanceUntilIdle()
        val ready = vm.waveformState.value
        old.complete(Result.failure(IllegalStateException("old failure")))
        dispatcher.scheduler.advanceUntilIdle()
        assertSame(ready, vm.waveformState.value)
        vm.release()
        assertEquals(AudioWaveformState.Idle, vm.waveformState.value)
    }

    @Test fun releaseIgnoresLateWaveformCompletion() {
        val late = CompletableDeferred<Result<AudioWaveform>>()
        waveform.gate = late
        bind(); vm.release()
        late.complete(waveform.result); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(AudioWaveformState.Idle, vm.waveformState.value)
    }
}

private class FakeWaveformRepo : AudioWaveformRepo {
    var result = Result.success(AudioWaveform(floatArrayOf(0.5f), 10.0, 10.0))
    var gate: CompletableDeferred<Result<AudioWaveform>>? = null
    override suspend fun load(uri: Uri, durationMs: Long): Result<AudioWaveform> {
        val waiting = gate
        return if (waiting != null) withContext(NonCancellable) { waiting.await() } else result
    }
}

private class FakeTrimRepo : AudioTrimRepo {
    var inspections = 0
    var probeResult: AudioTrimProbeResult = AudioTrimProbeResult.Success(AudioTrimInfo(10000, AudioTrimFormat.MP3, "mp3"))
    val requests = mutableListOf<AudioTrimRequest>()
    var gate: CompletableDeferred<Unit>? = null
    var result: AudioTrimResult = AudioTrimResult.Success(mock(Uri::class.java))
    override suspend fun inspect(uri: Uri): AudioTrimProbeResult { inspections++; return probeResult }
    override suspend fun trim(request: AudioTrimRequest, onProgress: (Float) -> Unit): AudioTrimResult {
        requests += request; onProgress(0.5f); gate?.await(); return result
    }
}
private class FakePreviewRepo : AudioPreviewRepo {
    override val state = MutableStateFlow(AudioPreviewState())
    var range = PlayerEffect.Trim(0, 1)
    var pauses = 0
    var opens = 0
    override fun open(uri: Uri, startMs: Long, endMs: Long) { opens++; range = PlayerEffect.Trim(startMs, endMs) }
    override fun setRange(startMs: Long, endMs: Long) { range = PlayerEffect.Trim(startMs, endMs) }
    override fun seekTo(positionMs: Long) {}
    override fun togglePlayPause() {}
    override fun pause() { pauses++ }
    override fun release() {}
}
