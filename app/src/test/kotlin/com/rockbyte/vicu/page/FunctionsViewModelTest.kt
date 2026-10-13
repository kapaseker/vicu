package com.rockbyte.vicu.page

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.rockbyte.vicu.R
import com.rockbyte.vicu.repo.GalleryRepo
import com.rockbyte.vicu.repo.MediaKind
import com.rockbyte.vicu.repo.SelectedMedia
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.MockedConstruction
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

@OptIn(ExperimentalCoroutinesApi::class)
class FunctionsViewModelTest {

    private val context = mock(Context::class.java)
    private val parsedUri: Uri = mock(Uri::class.java)
    private val launched = mutableListOf<Intent>()
    private val chooser: Intent = mock(Intent::class.java)
    private val toast = mock(Toast::class.java)
    private lateinit var uriStatic: MockedStatic<Uri>
    private lateinit var intentStatic: MockedStatic<Intent>
    private lateinit var toastStatic: MockedStatic<Toast>
    private lateinit var intents: MockedConstruction<Intent>

    @Before
    fun setUp() {
        // 单测的 android.jar 是桩实现：Uri.parse 与 Intent.createChooser 需替换，Intent 构造需拦截。
        uriStatic = Mockito.mockStatic(Uri::class.java)
        uriStatic.`when`<Uri> { Uri.parse(anyString()) }.thenReturn(parsedUri)
        intentStatic = Mockito.mockStatic(Intent::class.java)
        intentStatic.`when`<Intent> { Intent.createChooser(any(Intent::class.java), anyString()) }
            .thenReturn(chooser)
        toastStatic = Mockito.mockStatic(Toast::class.java)
        toastStatic.`when`<Toast> { Toast.makeText(any(), anyInt(), anyInt()) }.thenReturn(toast)
        intents = Mockito.mockConstruction(Intent::class.java) { _, _ -> }
        // 桩方法返回 null，链式 addFlags 需要回传自身
        Mockito.`when`(chooser.addFlags(Mockito.anyInt())).thenReturn(chooser)
        Mockito.`when`(context.getString(R.string.share)).thenReturn("分享")
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        intents.close()
        intentStatic.close()
        toastStatic.close()
        uriStatic.close()
        Dispatchers.resetMain()
    }

    private fun viewModel(repo: GalleryRepo = FakeGalleryRepo()) =
        FunctionsViewModel(context, repo, launched::add)

    private val video = SelectedMedia("content://media/video/1", "clip.mp4", MediaKind.VIDEO)

    @Test
    fun shareSendsVideoThroughChooserWithReadGrant() {
        viewModel().share(video)

        val send = intents.constructed().single()
        verify(send).setType("video/*")
        verify(send).putExtra(eq(Intent.EXTRA_STREAM), eq(parsedUri))
        verify(send).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intentStatic.verify { Intent.createChooser(eq(send), eq("分享")) }
        verify(chooser).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        assertEquals(listOf(chooser), launched)
    }

    @Test
    fun shareMapsKindToMimeType() {
        viewModel().share(SelectedMedia("content://media/image/2", "photo.png", MediaKind.IMAGE))
        viewModel().share(SelectedMedia("content://media/audio/3", "song.mp3", MediaKind.AUDIO))

        val (image, audio) = intents.constructed()
        verify(image).setType("image/*")
        verify(audio).setType("audio/*")
    }

    @Test
    fun saveToAlbumSavesThroughRepoAndToastsSuccess() {
        val repo = FakeGalleryRepo()
        viewModel(repo).saveToAlbum(video)

        assertEquals(video, repo.saved)
        toastStatic.verify { Toast.makeText(context, R.string.saved_to_album, Toast.LENGTH_SHORT) }
    }

    @Test
    fun saveToAlbumToastsFailureWithMessageOnRepoError() {
        Mockito.`when`(context.getString(R.string.save_failed, "boom")).thenReturn("保存失败：boom")
        viewModel(FakeGalleryRepo(error = IllegalStateException("boom"))).saveToAlbum(video)

        toastStatic.verify { Toast.makeText(eq(context), eq("保存失败：boom"), eq(Toast.LENGTH_SHORT)) }
    }

    @Test
    fun saveToAlbumIgnoresRepeatWhileSaving() = runTest {
        val gate = CompletableDeferred<Unit>()
        val repo = FakeGalleryRepo(gate = gate)
        val vm = viewModel(repo)

        vm.saveToAlbum(video)
        vm.saveToAlbum(video)
        gate.complete(Unit)

        assertEquals(1, repo.calls)
    }

    private class FakeGalleryRepo(
        private val error: Exception? = null,
        private val gate: CompletableDeferred<Unit>? = null,
    ) : GalleryRepo {
        var calls = 0
        var saved: SelectedMedia? = null

        override suspend fun saveToGallery(media: SelectedMedia) {
            calls++
            gate?.await()
            error?.let { throw it }
            saved = media
        }
    }
}
