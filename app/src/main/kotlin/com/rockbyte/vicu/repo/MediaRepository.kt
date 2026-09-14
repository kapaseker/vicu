package com.rockbyte.vicu.repo

import android.graphics.Bitmap
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * [MediaRepo] 实现：只查询当前可读集合，并按添加时间倒序聚合。
 */
internal class MediaRepository(private val mediaStore: MediaLibraryStore) : MediaRepo {

    override suspend fun loadLibrary(): MediaLibrary = withContext(Dispatchers.IO) {
        val readableKinds = MediaKind.entries.filter(mediaStore::canRead)
        val items = readableKinds.flatMap { kind ->
            try {
                mediaStore.query(kind)
            } catch (_: SecurityException) {
                emptyList()
            }
        }.sortedByDescending(MediaItem::dateAdded)
        MediaLibrary(
            items = items,
            hasAccess = readableKinds.isNotEmpty(),
            permissionsToRequest = mediaStore.permissionsToRequest,
        )
    }

    override suspend fun loadThumbnail(uri: Uri, width: Int, height: Int): Bitmap? =
        withContext(Dispatchers.IO) {
            runCatching { mediaStore.loadThumbnail(uri, width, height) }.getOrNull()
        }

    override fun observeExternalChanges(): Flow<Unit> = mediaStore.observeExternalChanges()
}

internal interface MediaLibraryStore {
    val permissionsToRequest: List<String>
    fun canRead(kind: MediaKind): Boolean
    fun query(kind: MediaKind): List<MediaItem>
    fun loadThumbnail(uri: Uri, width: Int, height: Int): Bitmap
    fun observeExternalChanges(): Flow<Unit>
}
