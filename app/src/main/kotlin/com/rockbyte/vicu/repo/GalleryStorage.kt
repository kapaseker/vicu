package com.rockbyte.vicu.repo

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import java.io.IOException

/** [GalleryStore] 实现：MediaStore 插入 + 流复制，IS_PENDING 两阶段发布。 */
internal class GalleryStorage(context: Context) : GalleryStore {
    private val resolver = context.applicationContext.contentResolver

    override fun save(name: String, kind: MediaKind, source: Uri) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.RELATIVE_PATH, kind.galleryPath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        // 重名冲突由 MediaStore 自动改名（如 "a (1).mp3"）
        val target = resolver.insert(kind.collection, values)
            ?: throw IOException("Cannot create gallery entry")
        try {
            resolver.openInputStream(source)?.use { input ->
                resolver.openOutputStream(target)?.use { output -> input.copyTo(output) }
                    ?: throw IOException("Cannot open gallery output")
            } ?: throw IOException("Cannot open work source")
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(target, values, null, null)
        } catch (error: Exception) {
            resolver.delete(target, null, null)
            throw error
        }
    }
}

private val MediaKind.galleryPath: String
    get() = when (this) {
        MediaKind.IMAGE -> "Pictures/Vicu"
        MediaKind.VIDEO -> "Movies/Vicu"
        MediaKind.AUDIO -> "Music/Vicu"
    }
