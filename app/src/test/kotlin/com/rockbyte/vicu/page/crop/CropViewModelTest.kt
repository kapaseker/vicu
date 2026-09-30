package com.rockbyte.vicu.page.crop

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
class CropViewModelTest {

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

    @Test
    fun bindSetsVideoNameAndIsIdempotent() {
        val viewModel = CropViewModel(repo)
        viewModel.bind(media)

        assertEquals("clip.mp4", viewModel.uiState.value.videoName)
        assertEquals(CutPhase.Idle, viewModel.uiState.value.cutPhase)
    }

    @Test
    fun cutBeforeBindIsIgnored() {
        val viewModel = CropViewModel(repo)
        viewModel.cut(PlayerEffect.Crop(0, 0, 100, 100))
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(repo.requests.isEmpty())
        assertEquals(CutPhase.Idle, viewModel.uiState.value.cutPhase)
    }

    @Test
    fun cutSendsNormalizedFilterWithSourceFormat() {
        val viewModel = CropViewModel(repo)
        viewModel.bind(media)
        viewModel.cut(PlayerEffect.Crop(11, 21, 101, 51))
        dispatcher.scheduler.advanceUntilIdle()

        val request = repo.requests.single()
        assertSame(parsedUri, request.uri)
        assertEquals("clip.mp4", request.displayName)
        assertEquals("crop=w=100:h=50:x=10:y=20", request.videoFilter)
        assertEquals(VideoConvertFormat.MP4, request.format)
        assertEquals(VideoConvertQuality.SUITABLE, request.quality)
    }

    @Test
    fun progressUpdatesCuttingPhaseThenCompletes() {
        val viewModel = CropViewModel(repo)
        viewModel.bind(media)
        repo.progress = listOf(0.25f, 0.5f)
        val gate = CompletableDeferred<Unit>()
        repo.gate = gate

        viewModel.cut(PlayerEffect.Crop(0, 0, 100, 100))
        dispatcher.scheduler.advanceUntilIdle()

        // 转码进行中：进度透传到 Cutting 阶段
        assertEquals(CutPhase.Cutting(progress = 0.5f), viewModel.uiState.value.cutPhase)

        gate.complete(Unit)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(CutPhase.Complete, viewModel.uiState.value.cutPhase)
    }

    @Test
    fun failureMapsToFailedPhase() {
        val viewModel = CropViewModel(repo)
        viewModel.bind(media)
        repo.result = VideoConvertResult.Failure(VideoConvertError.TranscodeFailed)

        viewModel.cut(PlayerEffect.Crop(0, 0, 100, 100))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            CutPhase.Failed(VideoConvertError.TranscodeFailed),
            viewModel.uiState.value.cutPhase,
        )
    }

    @Test
    fun repeatedCutWhileCuttingIsIgnored() {
        val viewModel = CropViewModel(repo)
        viewModel.bind(media)
        val gate = CompletableDeferred<Unit>()
        repo.gate = gate

        viewModel.cut(PlayerEffect.Crop(0, 0, 100, 100))
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.cutPhase is CutPhase.Cutting)

        // 导出中重复点击「剪切」应被忽略，不产生第二次转换
        viewModel.cut(PlayerEffect.Crop(0, 0, 50, 50))
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, repo.requests.size)

        gate.complete(Unit)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(CutPhase.Complete, viewModel.uiState.value.cutPhase)
    }

    @Test
    fun cropOutputFormatFollowsSourceExtensionAndFallsBackToMp4() {
        assertEquals(VideoConvertFormat.MKV, cropOutputFormat("clip.mkv"))
        assertEquals(VideoConvertFormat.WEBM, cropOutputFormat("CLIP.WebM"))
        assertEquals(VideoConvertFormat.MP4, cropOutputFormat("clip"))
        assertEquals(VideoConvertFormat.MP4, cropOutputFormat("clip.txt"))
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
