package com.rockbyte.vicu.page.audiotrim

import android.net.Uri
import com.rockbyte.vicu.player.PlayerEffect
import com.rockbyte.vicu.repo.*
import com.rockbyte.vicu.ui.component.trim.TrimInputError
import kotlinx.coroutines.CompletableDeferred
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
    private lateinit var vm: AudioTrimViewModel
    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        uriMock = Mockito.mockStatic(Uri::class.java)
        uriMock.`when`<Uri> { Uri.parse(anyString()) }.thenReturn(uri)
        vm = AudioTrimViewModel(repo, preview)
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
