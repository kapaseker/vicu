package com.rockbyte.vicu.page

import android.content.Context
import android.content.pm.PackageManager
import android.Manifest
import android.net.Uri
import com.rockbyte.vicu.repo.MediaItem
import com.rockbyte.vicu.repo.MediaKind
import com.rockbyte.vicu.repo.WorksRepo
import com.rockbyte.vicu.repo.WorksLibraryState
import kotlinx.coroutines.flow.MutableStateFlow
import com.rockbyte.vicu.repo.MediaRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

@OptIn(ExperimentalCoroutinesApi::class)
class MediaPickerViewModelTest {

    private class FakeMediaRepo : MediaRepo {
        val flow = MutableSharedFlow<List<MediaItem>>(replay = 1)
        var refreshCount = 0
        override val library: Flow<List<MediaItem>> = flow
        override fun refresh() {
            refreshCount++
        }
    }

    private val works = object : WorksRepo {
        override val library = MutableStateFlow(WorksLibraryState())
        var refreshCount = 0
        override fun refresh() { refreshCount++ }
    }

    private val dispatcher = StandardTestDispatcher()
    private val repo = FakeMediaRepo()
    private val context = mock(Context::class.java)
    private val audio = MediaItem(mock(Uri::class.java), "song.mp3", MediaKind.AUDIO, 100L, durationMs = 60_000)
    private val video = MediaItem(mock(Uri::class.java), "clip.mp4", MediaKind.VIDEO, 200L, durationMs = 5_000)

    @Before
    fun setUp() {
        // viewModelScope 依赖 Dispatchers.Main；可控调度器用于驱动 collect 协程
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(granted: Boolean = true): MediaPickerViewModel {
        val viewModel = MediaPickerViewModel(context, repo, works)
        `when`(context.checkSelfPermission(org.mockito.ArgumentMatchers.anyString()))
            .thenReturn(if (granted) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED)
        return viewModel
    }

    @Test
    fun bindFiltersLibraryByKinds() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.bind(setOf(MediaKind.AUDIO))
        repo.flow.tryEmit(listOf(audio, video))
        advanceUntilIdle()

        assertEquals(listOf(audio), viewModel.uiState.value.items)
        assertTrue(viewModel.uiState.value.hasAccess == true)
    }

    @Test
    fun bindKeepsWithoutAccessWhenPermissionDenied() = runTest(dispatcher) {
        val viewModel = viewModel(granted = false)
        viewModel.bind(setOf(MediaKind.AUDIO))
        repo.flow.tryEmit(listOf(audio))
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.hasAccess!!)
        // 单测 JVM 的 SDK_INT 低于 TIRAMISU 时回退旧版存储权限
        assertEquals(
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE),
            viewModel.uiState.value.permissionsToRequest,
        )
    }

    @Test
    fun confirmMapsItemToSelectedMedia() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.confirm(audio)
        advanceUntilIdle()

        val selected = viewModel.selected.value
        assertEquals(audio.uri.toString(), selected?.uri)
        assertEquals("song.mp3", selected?.name)
        assertEquals(MediaKind.AUDIO, selected?.kind)
    }

    @Test
    fun consumeClearsSelected() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.confirm(audio)

        viewModel.consume()
        advanceUntilIdle()

        assertNull(viewModel.selected.value)
    }

    @Test
    fun refreshTriggersRepoRequery() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(1, repo.refreshCount)
        assertEquals(1, works.refreshCount)
    }
    @Test
    fun worksRemainAccessibleAndFilterAudioWhileSystemPermissionDenied() = runTest(dispatcher) {
        val viewModel = viewModel(granted = false)
        viewModel.bind(setOf(MediaKind.AUDIO))
        works.library.value = WorksLibraryState(listOf(audio, video), loading = false)
        repo.flow.tryEmit(emptyList())
        advanceUntilIdle()
        assertEquals(listOf(audio), viewModel.worksState.value.items)
        assertFalse(viewModel.worksState.value.loading)
        assertFalse(viewModel.uiState.value.hasAccess!!)
        viewModel.bind(setOf(MediaKind.VIDEO))
        assertEquals(listOf(video), viewModel.worksState.value.items)
    }

    @Test
    fun loadingOfSystemAndWorksIsIndependent() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.bind(setOf(MediaKind.AUDIO))
        works.library.value = WorksLibraryState(listOf(audio), loading = false)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.loading)
        assertFalse(viewModel.worksState.value.loading)
        repo.flow.tryEmit(listOf(audio))
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.loading)
    }

}
