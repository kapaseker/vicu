package com.rockbyte.vicu.page.trim

import com.rockbyte.vicu.ui.component.trim.TrimInputError
import android.net.Uri
import com.rockbyte.vicu.player.PlayerEffect
import com.rockbyte.vicu.repo.MediaKind
import com.rockbyte.vicu.repo.SelectedMedia
import com.rockbyte.vicu.repo.VideoConvertError
import com.rockbyte.vicu.repo.VideoConvertFormat
import com.rockbyte.vicu.repo.VideoConvertQuality
import com.rockbyte.vicu.repo.VideoConvertRepo
import com.rockbyte.vicu.repo.VideoConvertRequest
import com.rockbyte.vicu.repo.VideoConvertResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.Mockito.mock

@OptIn(ExperimentalCoroutinesApi::class)
class TrimViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repo = FakeVideoConvertRepo()
    private val parsedUri: Uri = mock(Uri::class.java)
    private val media = SelectedMedia("content://media/video/1", "clip.mp4", MediaKind.VIDEO)
    private lateinit var uriStatic: MockedStatic<Uri>

    @Before
    fun setUp() {
        // viewModelScope 依赖 Dispatchers.Main；可控调度器用于驱动导出协程。
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

    @Test fun invalidInputNeverExportsAndKeepsLastValidRange() {
        val vm = TrimViewModel(repo)
        vm.bind(media)
        vm.setDuration(10000)
        assertTrue(vm.selectRange(1000, 5000))
        assertEquals(TrimInputError.ORDER, vm.confirm("5", "1"))
        assertEquals(PlayerEffect.Trim(1000, 5000), vm.uiState.value.range)
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(repo.requests.isEmpty())
    }

    @Test fun confirmLocksImmediatelyReportsProgressAndCompletes() {
        val vm = TrimViewModel(repo)
        vm.bind(media)
        vm.setDuration(10000)
        repo.progress = listOf(0.25f, 0.5f)
        val gate = CompletableDeferred<Unit>()
        repo.gate = gate
        assertEquals(null, vm.confirm("1.5", "5"))
        vm.confirm("1.5", "5")
        assertTrue(vm.uiState.value.phase is TrimPhase.Trimming)
        assertEquals(false, vm.selectRange(0, 10000))
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, repo.requests.size)
        assertEquals(PlayerEffect.Trim(1500, 5000), repo.requests.single().trim)
        assertEquals(VideoConvertFormat.MP4, repo.requests.single().format)
        assertEquals(VideoConvertQuality.SUITABLE, repo.requests.single().quality)
        assertSame(parsedUri, repo.requests.single().uri)
        assertEquals(TrimPhase.Trimming(0.5f), vm.uiState.value.phase)
        gate.complete(Unit)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(TrimPhase.Complete, vm.uiState.value.phase)
    }

    @Test fun failureCanBeRetriedAndUnknownDurationIsRejected() {
        val vm = TrimViewModel(repo)
        vm.bind(media)
        assertEquals(TrimInputError.OUT_OF_BOUNDS, vm.confirm("0", "1"))
        vm.setDuration(10000)
        repo.result = VideoConvertResult.Failure(VideoConvertError.TranscodeFailed)
        vm.confirm("0", "1")
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(TrimPhase.Failed(VideoConvertError.TranscodeFailed), vm.uiState.value.phase)
        repo.result = VideoConvertResult.Success(parsedUri)
        vm.confirm("0", "1")
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(TrimPhase.Complete, vm.uiState.value.phase)
        assertEquals(2, repo.requests.size)
    }
}

private class FakeVideoConvertRepo : VideoConvertRepo {
    val requests = mutableListOf<VideoConvertRequest>()
    var progress: List<Float> = emptyList()
    var result: VideoConvertResult = VideoConvertResult.Success(mock(Uri::class.java))
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun convert(
        request: VideoConvertRequest,
        onProgress: (Float) -> Unit,
    ): VideoConvertResult {
        requests += request
        progress.forEach(onProgress)
        gate?.await()
        return result
    }
}
