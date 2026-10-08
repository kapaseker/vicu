package com.rockbyte.vicu.page.videoscale

import android.net.Uri
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
class VideoScaleViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repo = FakeVideoConvertRepo()
    private val parsedUri: Uri = mock(Uri::class.java)
    private val media = SelectedMedia("content://media/video/1", "clip.mkv", MediaKind.VIDEO)
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
    fun bindIsIdempotent() {
        val viewModel = VideoScaleViewModel(repo)
        viewModel.bind(media)

        assertEquals(ScalePhase.Idle, viewModel.uiState.value.scalePhase)
    }

    @Test
    fun scaleBeforeBindIsIgnored() {
        val viewModel = VideoScaleViewModel(repo)
        viewModel.scale(640, 480)
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(repo.requests.isEmpty())
        assertEquals(ScalePhase.Idle, viewModel.uiState.value.scalePhase)
    }

    @Test
    fun scaleSendsFilterWithSourceFormatAndSuitableQuality() {
        val viewModel = VideoScaleViewModel(repo)
        viewModel.bind(media)
        viewModel.scale(406, 720)
        dispatcher.scheduler.advanceUntilIdle()

        val request = repo.requests.single()
        assertSame(parsedUri, request.uri)
        assertEquals("clip.mkv", request.displayName)
        assertEquals("scale=406:720", request.videoFilter)
        assertEquals(VideoConvertFormat.MKV, request.format)
        assertEquals(VideoConvertQuality.SUITABLE, request.quality)
    }

    @Test
    fun progressUpdatesScalingPhaseThenCompletes() {
        val viewModel = VideoScaleViewModel(repo)
        viewModel.bind(media)
        repo.progress = listOf(0.25f, 0.5f)
        val gate = CompletableDeferred<Unit>()
        repo.gate = gate

        viewModel.scale(1280, 720)
        dispatcher.scheduler.advanceUntilIdle()

        // 转码进行中：进度透传到 Scaling 阶段
        assertEquals(ScalePhase.Scaling(progress = 0.5f), viewModel.uiState.value.scalePhase)

        gate.complete(Unit)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(ScalePhase.Complete, viewModel.uiState.value.scalePhase)
    }

    @Test
    fun failureMapsToFailedPhase() {
        val viewModel = VideoScaleViewModel(repo)
        viewModel.bind(media)
        repo.result = VideoConvertResult.Failure(VideoConvertError.TranscodeFailed)

        viewModel.scale(1280, 720)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            ScalePhase.Failed(VideoConvertError.TranscodeFailed),
            viewModel.uiState.value.scalePhase,
        )
    }

    @Test
    fun repeatedScaleWhileScalingIsIgnored() {
        val viewModel = VideoScaleViewModel(repo)
        viewModel.bind(media)
        val gate = CompletableDeferred<Unit>()
        repo.gate = gate

        viewModel.scale(1280, 720)
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.scalePhase is ScalePhase.Scaling)

        // 导出中重复点击「确认」应被忽略，不产生第二次转换
        viewModel.scale(640, 360)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, repo.requests.size)

        gate.complete(Unit)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(ScalePhase.Complete, viewModel.uiState.value.scalePhase)
    }

    @Test
    fun scaleOutputSizeFollowsSourceAspectWhenRatioNull() {
        // 原始高度 + 原图比例 = 原样输出
        assertEquals(1920 to 1080, scaleOutputSize(1920, 1080, null, null))
        // 指定高度、比例跟随源画面
        assertEquals(1280 to 720, scaleOutputSize(1920, 1080, null, 720))
    }

    @Test
    fun scaleOutputSizeAppliesTargetHeightAndRatio() {
        // 16:9 源 → 9:16 比例 + 480p 高度：宽 = 480 * 9/16 = 270
        assertEquals(270 to 480, scaleOutputSize(1920, 1080, 9f / 16f, 480))
        // 源比例与所选比例一致时输出不变
        assertEquals(1280 to 720, scaleOutputSize(1920, 1080, 16f / 9f, 720))
    }

    @Test
    fun scaleOutputSizeRoundsDownToEvenWithFloorTwo() {
        // 奇数源高取偶（719→718）；宽按比例推导后同样取偶（1918.32→1918）
        assertEquals(1918 to 718, scaleOutputSize(1921, 719, null, null))
        // 极小高度下限 2
        assertEquals(2 to 2, scaleOutputSize(1920, 1080, 1f, 2))
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
