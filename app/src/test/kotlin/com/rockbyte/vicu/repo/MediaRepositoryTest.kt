package com.rockbyte.vicu.repo

import android.graphics.Bitmap
import android.net.Uri
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

class MediaRepositoryTest {
    private class FakeMediaLibraryStore : MediaLibraryStore {
        override val permissionsToRequest = listOf("images", "video", "audio")
        val readableKinds = mutableSetOf<MediaKind>()
        val queriedKinds = mutableListOf<MediaKind>()
        val items = mutableMapOf<MediaKind, List<MediaItem>>()
        var queryFailure: Pair<MediaKind, Exception>? = null
        var thumbnail: Bitmap = mock(Bitmap::class.java)
        var thumbnailFailure: Exception? = null

        override fun canRead(kind: MediaKind): Boolean = kind in readableKinds

        override fun query(kind: MediaKind): List<MediaItem> {
            queriedKinds += kind
            queryFailure?.takeIf { it.first == kind }?.second?.let { throw it }
            return items[kind].orEmpty()
        }

        override fun loadThumbnail(uri: Uri, width: Int, height: Int): Bitmap {
            thumbnailFailure?.let { throw it }
            return thumbnail
        }
    }

    @Test
    fun partialAccessQueriesOnlyReadableCollectionsAndSortsAllResults() = runBlocking {
        val access = FakeMediaLibraryStore()
        val imageUri = mock(Uri::class.java)
        val audioUri = mock(Uri::class.java)
        access.readableKinds += listOf(MediaKind.IMAGE, MediaKind.AUDIO)
        access.items[MediaKind.IMAGE] = listOf(MediaItem(imageUri, "old.jpg", MediaKind.IMAGE, 10))
        access.items[MediaKind.AUDIO] = listOf(MediaItem(audioUri, "new.mp3", MediaKind.AUDIO, 20))

        val library = MediaRepository(access).loadLibrary()

        assertTrue(library.hasAccess)
        assertEquals(listOf(audioUri, imageUri), library.items.map(MediaItem::uri))
        assertEquals(listOf(MediaKind.IMAGE, MediaKind.AUDIO), access.queriedKinds)
        assertEquals(access.permissionsToRequest, library.permissionsToRequest)
    }

    @Test
    fun noAccessReturnsRequestablePermissionsWithoutQuerying() = runBlocking {
        val access = FakeMediaLibraryStore()

        val library = MediaRepository(access).loadLibrary()

        assertFalse(library.hasAccess)
        assertTrue(library.items.isEmpty())
        assertTrue(access.queriedKinds.isEmpty())
        assertEquals(access.permissionsToRequest, library.permissionsToRequest)
    }

    @Test
    fun revokedCollectionDoesNotHideOtherReadableCollections() = runBlocking {
        val access = FakeMediaLibraryStore()
        val audioUri = mock(Uri::class.java)
        access.readableKinds += listOf(MediaKind.IMAGE, MediaKind.AUDIO)
        access.queryFailure = MediaKind.IMAGE to SecurityException("revoked")
        access.items[MediaKind.AUDIO] = listOf(MediaItem(audioUri, "song.mp3", MediaKind.AUDIO, 1))

        val library = MediaRepository(access).loadLibrary()

        assertTrue(library.hasAccess)
        assertEquals(listOf(audioUri), library.items.map(MediaItem::uri))
        assertEquals(listOf(MediaKind.IMAGE, MediaKind.AUDIO), access.queriedKinds)
    }

    @Test
    fun thumbnailFailureFallsBackToNull() = runBlocking {
        val access = FakeMediaLibraryStore()
        val uri = mock(Uri::class.java)
        assertSame(access.thumbnail, MediaRepository(access).loadThumbnail(uri, 256, 256))

        access.thumbnailFailure = IllegalStateException("decode failed")
        assertNull(MediaRepository(access).loadThumbnail(uri, 256, 256))
    }
}
