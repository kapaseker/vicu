package com.rockbyte.vicu.page

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.rockbyte.vicu.R
import com.rockbyte.vicu.repo.GalleryRepo
import com.rockbyte.vicu.repo.MediaItem
import com.rockbyte.vicu.repo.MediaKind
import com.rockbyte.vicu.repo.MediaRepo
import com.rockbyte.vicu.repo.MediaSource
import com.rockbyte.vicu.repo.SelectedMedia
import com.rockbyte.vicu.repo.WorksLibraryState
import com.rockbyte.vicu.repo.WorksRepo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        // 失败/重命名 Toast 走字符串重载，同样需要打桩，否则返回 null 在协程里抛 NPE
        toastStatic.`when`<Toast> { Toast.makeText(any(), any(CharSequence::class.java), anyInt()) }
            .thenReturn(toast)
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

    private fun viewModel(
        repo: GalleryRepo = FakeGalleryRepo(),
        mediaRepo: MediaRepo = FakeMediaRepo(),
        worksRepo: WorksRepo = FakeWorksRepo(),
    ) = FunctionsViewModel(context, repo, mediaRepo, worksRepo, launched::add)

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

    @Test
    fun renameKeepsExtensionAndRoutesBySource() = runTest {
        val mediaRepo = FakeMediaRepo()
        val worksRepo = FakeWorksRepo()
        val vm = viewModel(mediaRepo = mediaRepo, worksRepo = worksRepo)
        val success = mutableListOf<String>()

        vm.rename(video, " 我的作品 ", { success += it }, {})
        vm.rename(video.copy(source = MediaSource.WORKS), "work", { success += it }, {})

        assertEquals("我的作品.mp4", mediaRepo.renamed.single().second)
        assertEquals(parsedUri, mediaRepo.renamed.single().first)
        assertEquals("work.mp4", worksRepo.renamed.single().second)
        assertEquals(listOf("我的作品.mp4", "work.mp4"), success)
    }

    @Test
    fun renameIgnoresBlankStem() = runTest {
        val mediaRepo = FakeMediaRepo()
        val worksRepo = FakeWorksRepo()

        viewModel(mediaRepo = mediaRepo, worksRepo = worksRepo).rename(video, "   ", {}, {})

        assertTrue(mediaRepo.renamed.isEmpty() && worksRepo.renamed.isEmpty())
    }

    @Test
    fun renameToastsFailureWithMessageOnRepoError() = runTest {
        Mockito.`when`(context.getString(R.string.rename_failed, "boom"))
            .thenReturn("重命名失败：boom")

        viewModel(mediaRepo = FakeMediaRepo(error = IllegalStateException("boom")))
            .rename(video, "new", {}, {})

        toastStatic.verify { Toast.makeText(eq(context), eq("重命名失败：boom"), eq(Toast.LENGTH_SHORT)) }
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

    private class FakeMediaRepo(private val error: Exception? = null) : MediaRepo {
        override val library = MutableSharedFlow<List<MediaItem>>(replay = 1)
        val renamed = mutableListOf<Pair<Uri, String>>()

        override fun refresh() {}

        override suspend fun rename(uri: Uri, displayName: String) {
            error?.let { throw it }
            renamed += uri to displayName
        }
    }

    private class FakeWorksRepo : WorksRepo {
        override val library = MutableStateFlow(WorksLibraryState())
        val renamed = mutableListOf<Pair<Uri, String>>()

        override fun refresh() {}

        override suspend fun rename(uri: Uri, displayName: String) {
            renamed += uri to displayName
        }
    }
}
