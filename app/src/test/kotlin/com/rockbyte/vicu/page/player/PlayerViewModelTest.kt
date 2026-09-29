package com.rockbyte.vicu.page.player

import android.view.Surface
import com.rockbyte.vicu.player.PlayerEffect
import com.rockbyte.vicu.repo.MediaKind
import com.rockbyte.vicu.player.PlayerEvent
import com.rockbyte.vicu.player.PlayerRepo
import com.rockbyte.vicu.repo.SelectedMedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {

    private val repo = FakePlayerRepo()
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        // viewModelScope 依赖 Dispatchers.Main；可控调度器用于驱动事件流收集。
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun setCropNormalizesAndAppliesEffects() {
        val viewModel = PlayerViewModel(repo)
        viewModel.setCrop(PlayerEffect.Crop(left = 11, top = 21, width = 101, height = 51))

        assertEquals(PlayerEffect.Crop(10, 20, 100, 50), viewModel.uiState.value.crop)
        assertEquals(
            listOf(listOf(PlayerEffect.Crop(10, 20, 100, 50))),
            repo.appliedEffects,
        )
    }

    @Test
    fun setCropNullClearsCropButKeepsTrim() {
        val viewModel = PlayerViewModel(repo)
        viewModel.onEventForTest(PlayerEvent.Prepared(1920, 1080, 10000))
        viewModel.setTrim(PlayerEffect.Trim(1000, 2000))
        repo.appliedEffects.clear()

        viewModel.setCrop(PlayerEffect.Crop(0, 0, 640, 480))
        viewModel.setCrop(null)

        assertEquals(null, viewModel.uiState.value.crop)
        assertEquals(
            listOf(
                // effects 固定 crop 在前（滤镜链顺序稳定）
                listOf(PlayerEffect.Crop(0, 0, 640, 480), PlayerEffect.Trim(1000, 2000)),
                listOf(PlayerEffect.Trim(1000, 2000)),
            ),
            repo.appliedEffects,
        )
    }

    @Test
    fun setTrimClampsToDurationAndAppliesEffects() {
        val viewModel = PlayerViewModel(repo)
        viewModel.onEventForTest(PlayerEvent.Prepared(1920, 1080, 10000))

        viewModel.setTrim(PlayerEffect.Trim(-500, 20000))

        assertEquals(PlayerEffect.Trim(0, 10000), viewModel.uiState.value.trim)
        assertEquals(
            listOf(PlayerEffect.Trim(0, 10000)),
            repo.appliedEffects.last(),
        )
    }

    @Test
    fun seekToClampsToTrimRange() {
        val viewModel = PlayerViewModel(repo)
        viewModel.onEventForTest(PlayerEvent.Prepared(1920, 1080, 10000))
        viewModel.setTrim(PlayerEffect.Trim(2000, 8000))

        viewModel.seekTo(1000)
        viewModel.seekTo(9000)

        // 立即下发（无防抖）：native 侧以单个请求 + 最新目标合并重复 seek
        assertEquals(listOf(2000L, 8000L), repo.seekCalls)
        // UI 进度立即反映 clamp 后的值
        assertEquals(8000L, viewModel.uiState.value.positionMs)
    }

    @Test
    fun stalePositionsIgnoredUntilSeekSettles() {
        val viewModel = PlayerViewModel(repo)
        viewModel.onEventForTest(PlayerEvent.Prepared(1920, 1080, 60000))
        viewModel.onEventForTest(PlayerEvent.Position(10000))

        viewModel.seekTo(50000)
        assertEquals(listOf(50000L), repo.seekCalls)
        assertEquals(50000L, viewModel.uiState.value.positionMs)

        // 跳转生效前，旧进度回报不得回写（否则 seekbar 先回退再跳）
        viewModel.onEventForTest(PlayerEvent.Position(10200))
        assertEquals(50000L, viewModel.uiState.value.positionMs)

        // 到达目标附近后恢复跟随
        viewModel.onEventForTest(PlayerEvent.Position(50400))
        assertEquals(50400L, viewModel.uiState.value.positionMs)
        viewModel.onEventForTest(PlayerEvent.Position(50800))
        assertEquals(50800L, viewModel.uiState.value.positionMs)
    }

    @Test
    fun endedClearsPendingSeekGate() {
        val viewModel = PlayerViewModel(repo)
        viewModel.onEventForTest(PlayerEvent.Prepared(1920, 1080, 60000))

        viewModel.seekTo(60000)
        viewModel.onEventForTest(PlayerEvent.Ended)

        // 目标未回报即结束：门控随 Ended 清除，后续进度正常反映
        viewModel.onEventForTest(PlayerEvent.Position(59900))
        assertEquals(59900L, viewModel.uiState.value.positionMs)
    }

    @Test
    fun preparedEventReappliesCurrentEffects() {
        val viewModel = PlayerViewModel(repo)
        viewModel.setCrop(PlayerEffect.Crop(10, 20, 100, 50))
        repo.appliedEffects.clear()

        // replay/open 后的新会话：Prepared 到达时重放效果
        viewModel.onEventForTest(PlayerEvent.Prepared(1920, 1080, 10000))

        assertEquals(
            listOf(listOf(PlayerEffect.Crop(10, 20, 100, 50))),
            repo.appliedEffects,
        )
    }

    @Test
    fun scrubStartPausesEngineButKeepsPlayingFlag() {
        val viewModel = PlayerViewModel(repo)
        viewModel.onEventForTest(PlayerEvent.Prepared(1920, 1080, 60000))

        viewModel.scrubStart()

        // 引擎暂停（音频静音 + 画面定格），但 UI 播放态不变，避免按钮闪烁
        assertEquals(1, repo.pauseCalls)
        assertEquals(true, viewModel.uiState.value.playing)
        assertEquals(PlayerPhase.Playing, viewModel.uiState.value.phase)
    }

    @Test
    fun scrubToThrottlesSearchesAndTracksFinger() {
        val viewModel = PlayerViewModel(repo)
        viewModel.onEventForTest(PlayerEvent.Prepared(1920, 1080, 60000))
        viewModel.scrubStart()

        viewModel.scrubTo(1000)
        // 步长内的小幅移动只更新进度，不重发 seek（避免高频 seek）
        viewModel.scrubTo(1050)
        viewModel.scrubTo(1200)

        assertEquals(listOf(1000L, 1200L), repo.seekCalls)
        assertEquals(1200L, viewModel.uiState.value.positionMs)
    }

    @Test
    fun scrubEndSeeksAndResumesWhenWasPlaying() {
        val viewModel = PlayerViewModel(repo)
        viewModel.onEventForTest(PlayerEvent.Prepared(1920, 1080, 60000))
        viewModel.scrubStart()
        viewModel.scrubTo(1000)

        viewModel.scrubEnd(2000)

        assertEquals(2000L, repo.seekCalls.last())
        assertEquals(1, repo.playCalls)
        assertEquals(true, viewModel.uiState.value.playing)
        assertEquals(PlayerPhase.Playing, viewModel.uiState.value.phase)
    }

    @Test
    fun scrubEndStaysPausedWhenWasPaused() {
        val viewModel = PlayerViewModel(repo)
        viewModel.onEventForTest(PlayerEvent.Prepared(1920, 1080, 60000))
        viewModel.togglePlayPause()
        repo.pauseCalls = 0

        viewModel.scrubStart()
        viewModel.scrubTo(1000)
        viewModel.scrubEnd(2000)

        assertEquals(0, repo.pauseCalls)
        assertEquals(0, repo.playCalls)
        assertEquals(false, viewModel.uiState.value.playing)
        assertEquals(PlayerPhase.Paused, viewModel.uiState.value.phase)
    }

    @Test
    fun scrubClampsToTrimRange() {
        val viewModel = PlayerViewModel(repo)
        viewModel.onEventForTest(PlayerEvent.Prepared(1920, 1080, 60000))
        viewModel.setTrim(PlayerEffect.Trim(10000, 20000))
        viewModel.scrubStart()

        viewModel.scrubTo(1000)
        assertEquals(10000L, viewModel.uiState.value.positionMs)

        viewModel.scrubTo(30000)
        assertEquals(20000L, viewModel.uiState.value.positionMs)

        assertEquals(listOf(10000L, 20000L), repo.seekCalls)
    }

    @Test
    fun scrubEndFromEndedResumesPlayback() {
        val viewModel = PlayerViewModel(repo)
        viewModel.onEventForTest(PlayerEvent.Prepared(1920, 1080, 60000))
        viewModel.onEventForTest(PlayerEvent.Ended)

        viewModel.scrubStart()
        viewModel.scrubEnd(0)

        // native 在 Ended 态 seek 即转回播放，UI 需同步
        assertEquals(1, repo.playCalls)
        assertEquals(PlayerPhase.Playing, viewModel.uiState.value.phase)
    }

    /** 经 fake 事件流注入事件（模拟 native 回调链路）。 */
    private fun PlayerViewModel.onEventForTest(event: PlayerEvent) {
        dispatcher.scheduler.runCurrent()
        repo.emit(event)
        dispatcher.scheduler.runCurrent()
    }

    private class FakePlayerRepo : PlayerRepo {
        private val _events = MutableSharedFlow<PlayerEvent>(extraBufferCapacity = 64)
        override val events: Flow<PlayerEvent> = _events

        val appliedEffects = mutableListOf<List<PlayerEffect>>()
        val seekCalls = mutableListOf<Long>()
        var playCalls = 0
        var pauseCalls = 0

        fun emit(event: PlayerEvent) {
            check(_events.tryEmit(event))
        }

        override suspend fun open(uri: android.net.Uri) = Unit
        override fun setSurface(surface: Surface?) = Unit
        override fun play() {
            playCalls++
        }

        override fun pause() {
            pauseCalls++
        }

        override fun seekTo(positionMs: Long) {
            seekCalls += positionMs
        }

        override fun applyEffects(effects: List<PlayerEffect>) {
            appliedEffects += effects
        }

        override suspend fun replay() = Unit
        override fun release() = Unit
    }
}
