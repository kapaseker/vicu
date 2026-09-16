package com.rockbyte.vicu.repo

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore

/** [MediaLibraryStore] 实现：仅负责 MediaStore 查询。 */
internal class MediaLibraryStorage(context: Context) : MediaLibraryStore {
    private val resolver = context.applicationContext.contentResolver

    override fun query(kind: MediaKind): List<MediaItem> {
        val collection = kind.collection
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
                    dateAdded = cursor.getLong(dateIndex).normalizeDateAdded(),
                )
            }
        }
        return items
    }
}

private val MediaKind.collection: Uri
    get() = when (this) {
        MediaKind.IMAGE -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        MediaKind.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        MediaKind.AUDIO -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    }

// ponytail: 固定阈值 1e10 秒（约公元 2286 年）；部分厂商 App 会向 DATE_ADDED 写毫秒值（~1e12），
// 超过该上限按毫秒归一化为秒。若未来出现新的非法量纲再扩展。
internal const val DATE_ADDED_SECONDS_CEILING: Long = 10_000_000_000L

/** MediaStore 是信任边界：把误写为毫秒的 DATE_ADDED 归一化为秒。 */
internal fun Long.normalizeDateAdded(): Long =
    if (this > DATE_ADDED_SECONDS_CEILING) this / 1000 else this
