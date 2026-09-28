package com.rockbyte.vicu.page.player

import android.view.Surface
import com.rockbyte.vicu.repo.EffectSpec
import com.rockbyte.vicu.repo.MediaKind
import com.rockbyte.vicu.repo.PlayerEvent
import com.rockbyte.vicu.repo.PlayerRepo
import com.rockbyte.vicu.repo.SelectedMedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {

    private val repo = FakePlayerRepo()
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        // viewModelScope 依赖 Dispatchers.Main；单测注入 Unconfined 使 init 收集立即生效
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun setCropNormalizesAndAppliesEffects() {
        val viewModel = PlayerViewModel(repo)
        viewModel.setCrop(EffectSpec.Crop(left = 11, top = 21, width = 101, height = 51))

        assertEquals(EffectSpec.Crop(10, 20, 100, 50), viewModel.uiState.value.crop)
        assertEquals(
            listOf(listOf(EffectSpec.Crop(10, 20, 100, 50))),
            repo.appliedEffects,
        )
    }

    @Test
    fun setCropNullClearsCropButKeepsTrim() {
        val viewModel = PlayerViewModel(repo)
        viewModel.onEventForTest(PlayerEvent.Prepared(1920, 1080, 10000))
        viewModel.setTrim(EffectSpec.Trim(1000, 2000))
        repo.appliedEffects.clear()

        viewModel.setCrop(EffectSpec.Crop(0, 0, 640, 480))
        viewModel.setCrop(null)

        assertEquals(null, viewModel.uiState.value.crop)
        assertEquals(
            listOf(
                // effects 固定 crop 在前（滤镜链顺序稳定）
                listOf(EffectSpec.Crop(0, 0, 640, 480), EffectSpec.Trim(1000, 2000)),
                listOf(EffectSpec.Trim(1000, 2000)),
            ),
            repo.appliedEffects,
        )
    }

    @Test
    fun setTrimClampsToDurationAndAppliesEffects() {
        val viewModel = PlayerViewModel(repo)
        viewModel.onEventForTest(PlayerEvent.Prepared(1920, 1080, 10000))

        viewModel.setTrim(EffectSpec.Trim(-500, 20000))

        assertEquals(EffectSpec.Trim(0, 10000), viewModel.uiState.value.trim)
        assertEquals(
            listOf(EffectSpec.Trim(0, 10000)),
            repo.appliedEffects.last(),
        )
    }

    @Test
    fun seekToClampsToTrimRange() {
        val viewModel = PlayerViewModel(repo)
        viewModel.onEventForTest(PlayerEvent.Prepared(1920, 1080, 10000))
        viewModel.setTrim(EffectSpec.Trim(2000, 8000))

        viewModel.seekTo(1000)
        viewModel.seekTo(9000)

        assertEquals(listOf(2000L, 8000L), repo.seekCalls)
        // UI 进度立即反映 clamp 后的值
        assertEquals(8000L, viewModel.uiState.value.positionMs)
    }

    @Test
    fun preparedEventReappliesCurrentEffects() {
        val viewModel = PlayerViewModel(repo)
        viewModel.setCrop(EffectSpec.Crop(10, 20, 100, 50))
        repo.appliedEffects.clear()

        // replay/open 后的新会话：Prepared 到达时重放效果
        viewModel.onEventForTest(PlayerEvent.Prepared(1920, 1080, 10000))

        assertEquals(
            listOf(listOf(EffectSpec.Crop(10, 20, 100, 50))),
            repo.appliedEffects,
        )
    }

    /** 经 fake 事件流注入事件（模拟 native 回调链路）。 */
    private fun PlayerViewModel.onEventForTest(event: PlayerEvent) {
        repo.emit(event)
    }

    private class FakePlayerRepo : PlayerRepo {
        private val _events = MutableSharedFlow<PlayerEvent>(extraBufferCapacity = 64)
        override val events: Flow<PlayerEvent> = _events

        val appliedEffects = mutableListOf<List<EffectSpec>>()
        val seekCalls = mutableListOf<Long>()

        fun emit(event: PlayerEvent) {
            check(_events.tryEmit(event))
        }

        override suspend fun open(media: SelectedMedia) = Unit
        override fun setSurface(surface: Surface?) = Unit
        override fun play() = Unit
        override fun pause() = Unit
        override fun seekTo(positionMs: Long) {
            seekCalls += positionMs
        }

        override fun applyEffects(effects: List<EffectSpec>) {
            appliedEffects += effects
        }

        override suspend fun replay() = Unit
        override fun release() = Unit
    }
}
