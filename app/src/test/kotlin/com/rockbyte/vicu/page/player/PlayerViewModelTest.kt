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
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
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
        // viewModelScope 依赖 Dispatchers.Main；可控虚拟时间用于验证 seek 合并。
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

        dispatcher.scheduler.advanceTimeBy(200)
        assertEquals(listOf(8000L), repo.seekCalls)
        // UI 进度立即反映 clamp 后的值
        assertEquals(8000L, viewModel.uiState.value.positionMs)
    }

    @Test
    fun rapidSeeksOnlyDispatchLatestTarget() = runTest(dispatcher.scheduler) {
        val viewModel = PlayerViewModel(repo)
        viewModel.onEventForTest(PlayerEvent.Prepared(1920, 1080, 60000))

        viewModel.seekTo(10000)
        advanceTimeBy(100)
        viewModel.seekTo(50000)

        assertEquals(50000L, viewModel.uiState.value.positionMs)
        advanceTimeBy(149)
        assertEquals(emptyList<Long>(), repo.seekCalls)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(50000L), repo.seekCalls)
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

        fun emit(event: PlayerEvent) {
            check(_events.tryEmit(event))
        }

        override suspend fun open(uri: android.net.Uri) = Unit
        override fun setSurface(surface: Surface?) = Unit
        override fun play() = Unit
        override fun pause() = Unit
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
