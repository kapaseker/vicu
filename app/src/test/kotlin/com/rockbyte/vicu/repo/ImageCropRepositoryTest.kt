package com.rockbyte.vicu.repo

import android.graphics.Bitmap
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.mock

class ImageCropRepositoryTest {
    private class Fixture {
        val input = mock(Uri::class.java)
        val output = mock(Uri::class.java)
        val bitmap = mock(Bitmap::class.java)
        val image = ImageCropPreview(bitmap, ImageCropInfo(7, 5, "image/png"))
        var pending = false
        var published = false
        var decoded = false
        var recycled = false
        var failureAt = ""
        var failure: Exception = IllegalStateException("failed")
        var deleted = false
        var duringWrite: () -> Unit = {}
        val store = object : ImageCropStore {
            private fun fail(step: String) { if (failureAt == step) throw failure }
            override fun load(uri: Uri): ImageCropPreview { fail("load"); return image }
            override fun decodeCrop(uri: Uri, region: ImageCropRegion, previewBytes: Long): ImageCropPreview {
                fail("decode"); decoded = true; return image
            }
            override fun decodeScaled(uri: Uri, width: Int, height: Int, previewBytes: Long): ImageCropPreview {
                fail("decodeScaled"); decoded = true; return image
            }
            override fun create(displayName: String, suffix: String, format: ImageCropFormat): Uri {
                fail("create"); pending = true; return output
            }
            override fun write(uri: Uri, bitmap: Bitmap, format: ImageCropFormat) { duringWrite(); fail("write") }
            override fun publish(uri: Uri) { fail("publish"); pending = false; published = true }
            override fun delete(uri: Uri) { pending = false; published = false; deleted = true }
        }
        val repo = ImageCropRepository(store)
        init { org.mockito.Mockito.doAnswer { recycled = true; null }.`when`(bitmap).recycle() }
        suspend fun crop(region: ImageCropRegion = ImageCropRegion(0, 0, 7, 5)) =
            repo.crop(input, "photo.png", region, 0)
    }

    @Test
    fun successfulSavePublishesAndReleasesDecodedBitmap() = runBlocking {
        val f = Fixture()
        assertSame(f.output, f.crop().getOrThrow())
        assertTrue(f.published)
        assertFalse(f.pending)
        assertTrue(f.recycled)
        assertFalse(f.deleted)
    }

    @Test
    fun writeAndPublicationFailuresRemovePartialOutput() = runBlocking {
        for (step in listOf("write", "publish")) {
            val f = Fixture(); f.failureAt = step
            assertEquals(ImageCropError.SaveFailed, (f.crop().exceptionOrNull() as ImageCropException).error)
            assertTrue(f.deleted)
            assertFalse(f.pending)
            assertFalse(f.published)
            assertTrue(f.recycled)
        }
    }

    @Test
    fun cancellationPropagatesAndRemovesPartialOutput() = runBlocking {
        val f = Fixture(); f.failureAt = "write"; f.failure = CancellationException("cancel")
        try { f.crop(); fail("Cancellation must propagate") } catch (_: CancellationException) { }
        assertTrue(f.deleted)
        assertTrue(f.recycled)
    }

    @Test
    fun cancellationDuringBlockingWriteNeverPublishesAndStillCleansOutput() = runBlocking {
        val f = Fixture()
        val writing = CountDownLatch(1)
        val release = CountDownLatch(1)
        f.duringWrite = { writing.countDown(); check(release.await(5, TimeUnit.SECONDS)) }
        val task = async(kotlinx.coroutines.Dispatchers.Default) { f.crop() }
        try {
            assertTrue(writing.await(5, TimeUnit.SECONDS))
            task.cancel()
        } finally { release.countDown() }
        try { task.await(); fail("Cancellation must propagate") } catch (_: CancellationException) { }
        assertTrue(f.deleted)
        assertFalse(f.published)
        assertFalse(f.pending)
        assertTrue(f.recycled)
    }

    @Test
    fun decodingFailureNeverCreatesOutputAndPreservesTypedError() = runBlocking {
        val f = Fixture(); f.failureAt = "decode"
        f.failure = ImageCropException(ImageCropError.ImageTooLarge)
        assertEquals(ImageCropError.ImageTooLarge, (f.crop().exceptionOrNull() as ImageCropException).error)
        assertFalse(f.pending)
        assertFalse(f.published)
        assertFalse(f.deleted)
    }

    @Test
    fun invalidRegionIsRejectedBeforeDecodeAndLoadErrorsAreDistinct() = runBlocking {
        val f = Fixture()
        assertEquals(ImageCropError.CropFailed,
            (f.crop(ImageCropRegion(-1, 0, 7, 5)).exceptionOrNull() as ImageCropException).error)
        assertFalse(f.decoded)
        f.failureAt = "load"
        assertEquals(ImageCropError.LoadFailed, (f.repo.load(f.input).exceptionOrNull() as ImageCropException).error)
        f.failure = ImageCropException(ImageCropError.AnimatedUnsupported)
        assertEquals(ImageCropError.AnimatedUnsupported, (f.repo.load(f.input).exceptionOrNull() as ImageCropException).error)
    }
}
