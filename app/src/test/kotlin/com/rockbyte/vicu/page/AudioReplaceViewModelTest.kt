package com.rockbyte.vicu.page

import android.net.Uri
import com.rockbyte.vicu.repo.AudioReplaceError
import com.rockbyte.vicu.repo.AudioReplaceMode
import com.rockbyte.vicu.repo.AudioReplaceRepo
import com.rockbyte.vicu.repo.AudioReplaceRequest
import com.rockbyte.vicu.repo.AudioReplaceResult
import com.rockbyte.vicu.repo.MediaKind
import com.rockbyte.vicu.repo.SelectedMedia
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.Mockito.mock

@OptIn(ExperimentalCoroutinesApi::class)
class AudioReplaceViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repo = FakeAudioReplaceRepo()
    private val parsedUri: Uri = mock(Uri::class.java)
    private val media = SelectedMedia("content://media/video/1", "clip.mp4", MediaKind.VIDEO)
    private lateinit var uriStatic: MockedStatic<Uri>

    @Before
    fun setUp() {
        // viewModelScope 依赖 Dispatchers.Main；可控调度器用于驱动探测/替换协程。
        Dispatchers.setMain(dispatcher)
        // 单元测试的 android.jar 是桩实现，Uri.parse 会抛异常；此处替换为 mock。
        uriStatic = Mockito.mockStatic(Uri::class.java)
        uriStatic.`when`<Uri> { Uri.parse(anyString()) }.thenReturn(parsedUri)
    }

    @After
    fun tearDown() {
        uriStatic.close()
        Dispatchers.resetMain()
    }

    @Test
    fun bindProbesVideoDurationAndStaysReady() {
        val viewModel = AudioReplaceViewModel(repo)
        viewModel.bind(media)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("clip.mp4", viewModel.uiState.value.videoName)
        assertEquals(10_000L, viewModel.uiState.value.videoDurationMs)
        assertEquals(ReplacePhase.Ready, viewModel.uiState.value.phase)
    }

    @Test
    fun bindIsIdempotentAndResetsOnSourceChange() {
        val viewModel = AudioReplaceViewModel(repo)
        viewModel.bind(media)
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.selectMusic(parsedUri, "bgm.mp3")
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.selectMode(AudioReplaceMode.STRETCH_AUDIO)

        viewModel.bind(
            SelectedMedia("content://media/video/1", "renamed.mp4", MediaKind.VIDEO),
        )
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("renamed.mp4", viewModel.uiState.value.videoName)
        assertNull(viewModel.uiState.value.musicName)
        assertEquals(AudioReplaceMode.TRUNCATE, viewModel.uiState.value.mode)
        assertEquals(ReplacePhase.Ready, viewModel.uiState.value.phase)
    }

    @Test
    fun selectMusicProbesDurationBeforeBecomingReady() {
        val viewModel = AudioReplaceViewModel(repo)
        viewModel.bind(media)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.selectMusic(parsedUri, "bgm.mp3")
        assertEquals(ReplacePhase.ProbingMusic, viewModel.uiState.value.phase)
        assertEquals("bgm.mp3", viewModel.uiState.value.musicName)

        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(30_000L, viewModel.uiState.value.musicDurationMs)
        assertEquals(ReplacePhase.Ready, viewModel.uiState.value.phase)
    }

    @Test
    fun failedMusicProbeFallsBackToTruncateMode() {
        val viewModel = AudioReplaceViewModel(repo)
        viewModel.bind(media)
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.selectMusic(parsedUri, "a.mp3")
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.selectMode(AudioReplaceMode.STRETCH_AUDIO)

        repo.musicDurationMs = null
        viewModel.selectMusic(parsedUri, "b.mp3")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(AudioReplaceMode.TRUNCATE, viewModel.uiState.value.mode)
        assertNull(viewModel.uiState.value.musicDurationMs)
    }

    @Test
    fun replaceSendsRequestAndPublishesProgress() {
        val viewModel = AudioReplaceViewModel(repo)
        viewModel.bind(media)
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.selectMusic(parsedUri, "bgm.mp3")
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.selectMode(AudioReplaceMode.STRETCH_VIDEO)

        viewModel.replace()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(ReplacePhase.Complete, viewModel.uiState.value.phase)
        val request = repo.requests.single()
        assertEquals(parsedUri, request.videoUri)
        assertEquals(parsedUri, request.musicUri)
        assertEquals("clip.mp4", request.displayName)
        assertEquals(AudioReplaceMode.STRETCH_VIDEO, request.mode)
    }

    @Test
    fun replaceFailureSetsFailedPhase() {
        repo.replaceResult = AudioReplaceResult.Failure(
            AudioReplaceError.TranscodeFailed, IllegalStateException("boom")
        )
        val viewModel = AudioReplaceViewModel(repo)
        viewModel.bind(media)
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.selectMusic(parsedUri, "bgm.mp3")
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.replace()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            ReplacePhase.Failed(AudioReplaceError.TranscodeFailed),
            viewModel.uiState.value.phase,
        )
    }

    @Test
    fun replaceWithoutMusicIsIgnored() {
        val viewModel = AudioReplaceViewModel(repo)
        viewModel.bind(media)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.replace()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(repo.requests.isEmpty())
        assertEquals(ReplacePhase.Ready, viewModel.uiState.value.phase)
    }

    @Test
    fun replaceWhileProbingMusicIsIgnored() {
        val viewModel = AudioReplaceViewModel(repo)
        viewModel.bind(media)
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.selectMusic(parsedUri, "bgm.mp3")

        viewModel.replace()
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(repo.requests.isEmpty())
        assertEquals(ReplacePhase.Ready, viewModel.uiState.value.phase)
    }

    @Test
    fun replaceWhileConvertingIsIgnored() {
        val viewModel = AudioReplaceViewModel(repo)
        viewModel.bind(media)
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.selectMusic(parsedUri, "bgm.mp3")
        dispatcher.scheduler.advanceUntilIdle()

        repo.gate = CompletableDeferred()
        viewModel.replace()
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.replace()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, repo.requests.size)
        assertEquals(
            ReplacePhase.Converting(progress = 0.5f),
            viewModel.uiState.value.phase,
        )

        repo.gate?.complete(Unit)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(ReplacePhase.Complete, viewModel.uiState.value.phase)
    }

    private class FakeAudioReplaceRepo : AudioReplaceRepo {
        var videoDurationMs: Long? = 10_000
        var musicDurationMs: Long? = 30_000
        var replaceResult: AudioReplaceResult = AudioReplaceResult.Success(mock(Uri::class.java))
        var gate: CompletableDeferred<Unit>? = null
        val requests = mutableListOf<AudioReplaceRequest>()

        override suspend fun probeVideoDurationMs(uri: Uri): Long? = videoDurationMs
        override suspend fun probeMusicDurationMs(uri: Uri): Long? = musicDurationMs

        override suspend fun replace(
            request: AudioReplaceRequest,
            onProgress: (Float) -> Unit,
        ): AudioReplaceResult {
            requests += request
            onProgress(0.5f)
            gate?.await()
            return replaceResult
        }
    }
}
