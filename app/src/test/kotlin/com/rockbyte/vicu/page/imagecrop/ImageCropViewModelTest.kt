package com.rockbyte.vicu.page.imagecrop

import android.graphics.Bitmap
import android.net.Uri
import com.rockbyte.vicu.repo.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class ImageCropViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val uri = mock(Uri::class.java)
    private val preview = ImageCropPreview(mock(Bitmap::class.java), ImageCropInfo(7, 5, "image/png"))
    private val media = SelectedMedia("content://images/1", "odd.png", MediaKind.IMAGE)
    private lateinit var uriStatic: MockedStatic<Uri>
    private var loads = 0
    private var saves = 0
    private var gate: CompletableDeferred<Unit>? = null
    private var result: Result<Uri> = Result.success(uri)
    private var loadResult: Result<ImageCropPreview> = Result.success(preview)
    private val repo = object : ImageCropRepo {
        override suspend fun load(uri: Uri): Result<ImageCropPreview> { loads++; return loadResult }
        override suspend fun crop(uri: Uri, displayName: String, region: ImageCropRegion, previewBytes: Long): Result<Uri> {
            saves++; gate?.await(); return result
        }
    }

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        uriStatic = Mockito.mockStatic(Uri::class.java)
        uriStatic.`when`<Uri> { Uri.parse(anyString()) }.thenReturn(uri)
    }
    @After fun tearDown() { uriStatic.close(); Dispatchers.resetMain() }

    @Test
    fun bindingLoadsOnceAndSavingBeforeReadyIsIgnored() {
        val vm = ImageCropViewModel(repo)
        vm.bind(media)
        vm.save(ImageCropRegion(0, 0, 7, 5))
        vm.bind(media)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, loads)
        assertEquals(0, saves)
        assertEquals(ImageCropLoadState.Ready(preview), vm.uiState.value.loadState)
    }

    @Test
    fun doubleClickBeforeCoroutineRunsCannotSaveTwice() {
        val vm = ImageCropViewModel(repo); vm.bind(media); dispatcher.scheduler.advanceUntilIdle()
        gate = CompletableDeferred()
        vm.save(ImageCropRegion(0, 0, 7, 5)); vm.save(ImageCropRegion(0, 0, 7, 5))
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, saves)
        assertEquals(ImageCropSaveState.Saving, vm.uiState.value.saveState)
        vm.selectionChanged()
        assertEquals(ImageCropSaveState.Saving, vm.uiState.value.saveState)
        gate!!.complete(Unit); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(ImageCropSaveState.Complete, vm.uiState.value.saveState)
        vm.selectionChanged()
        assertEquals(ImageCropSaveState.Idle, vm.uiState.value.saveState)
        vm.save(ImageCropRegion(0, 0, 3, 3)); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(2, saves)
    }

    @Test
    fun failedSaveCanRetryAndFailedLoadCannotSave() {
        val vm = ImageCropViewModel(repo); vm.bind(media); dispatcher.scheduler.advanceUntilIdle()
        result = Result.failure(ImageCropException(ImageCropError.SaveFailed))
        vm.save(ImageCropRegion(0, 0, 7, 5)); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(ImageCropSaveState.Failed(ImageCropError.SaveFailed), vm.uiState.value.saveState)
        result = Result.success(uri)
        vm.save(ImageCropRegion(0, 0, 7, 5)); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(ImageCropSaveState.Complete, vm.uiState.value.saveState)
        loadResult = Result.failure(ImageCropException(ImageCropError.AnimatedUnsupported))
        vm.bind(media.copy(uri = "content://images/2")); dispatcher.scheduler.advanceUntilIdle()
        vm.save(ImageCropRegion(0, 0, 7, 5)); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(ImageCropLoadState.Failed(ImageCropError.AnimatedUnsupported), vm.uiState.value.loadState)
        assertEquals(2, saves)
    }
}
