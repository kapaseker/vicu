package com.rockbyte.vicu.repo

import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.mock

class MediaRepositoryTest {
    private class FakeMediaLibraryStore : MediaLibraryStore {
        val items = mutableMapOf<MediaKind, List<MediaItem>>()
        var queryFailure: Pair<MediaKind, Exception>? = null

        override fun query(kind: MediaKind): List<MediaItem> {
            queryFailure?.takeIf { it.first == kind }?.second?.let { throw it }
            return items[kind].orEmpty()
        }
    }

    @Test
    fun initialEmissionMergesAllKindsSortedByDateAdded() = runBlocking {
        val store = FakeMediaLibraryStore()
        val imageUri = mock(Uri::class.java)
        val audioUri = mock(Uri::class.java)
        store.items[MediaKind.IMAGE] = listOf(MediaItem(imageUri, "old.jpg", MediaKind.IMAGE, 10))
        store.items[MediaKind.AUDIO] = listOf(MediaItem(audioUri, "new.mp3", MediaKind.AUDIO, 20))

        val repo = MediaRepository(mock(ContentResolver::class.java), store)

        val items = withTimeout(5_000) { repo.library.first() }
        assertEquals(listOf(audioUri, imageUri), items.map(MediaItem::uri))
    }

    @Test
    fun securityExceptionOnOneKindDoesNotHideOthers() = runBlocking {
        val store = FakeMediaLibraryStore()
        val imageUri = mock(Uri::class.java)
        val audioUri = mock(Uri::class.java)
        store.queryFailure = MediaKind.VIDEO to SecurityException("revoked")
        store.items[MediaKind.IMAGE] = listOf(MediaItem(imageUri, "photo.jpg", MediaKind.IMAGE, 1))
        store.items[MediaKind.AUDIO] = listOf(MediaItem(audioUri, "song.mp3", MediaKind.AUDIO, 2))

        val repo = MediaRepository(mock(ContentResolver::class.java), store)

        val items = withTimeout(5_000) { repo.library.first() }
        assertEquals(listOf(audioUri, imageUri), items.map(MediaItem::uri))
    }
}
