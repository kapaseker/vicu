package com.rockbyte.vicu.repo

import android.content.ContentResolver
import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [MediaRepo] 的 MediaStore 实现：聚合图片/视频/音频三个集合并按添加时间倒序。
 * 未授权的集合查询结果为空（33+ 细分权限下支持部分授权）。
 */
class MediaRepository(private val resolver: ContentResolver) : MediaRepo {

    override suspend fun queryAllMedia(): List<MediaItem> = withContext(Dispatchers.IO) {
        (
            queryCollection(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, MediaKind.IMAGE) +
                queryCollection(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, MediaKind.VIDEO) +
                queryCollection(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, MediaKind.AUDIO)
            ).sortedByDescending(MediaItem::dateAdded)
    }

    private fun queryCollection(collection: Uri, kind: MediaKind): List<MediaItem> {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_ADDED,
        )
        val items = mutableListOf<MediaItem>()
        resolver.query(collection, projection, null, null, null)?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val dateIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            while (cursor.moveToNext()) {
                items += MediaItem(
                    uri = ContentUris.withAppendedId(collection, cursor.getLong(idIndex)),
                    name = cursor.getString(nameIndex).orEmpty(),
                    kind = kind,
                    dateAdded = cursor.getLong(dateIndex),
                )
            }
        }
        return items
    }
}
