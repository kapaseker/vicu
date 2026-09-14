package com.rockbyte.vicu.repo

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore

internal class VideoOutputStorage(
    private val resolver: ContentResolver,
    private val currentTimeMillis: () -> Long,
) : VideoOutputStore {
    override fun create(inputName: String, format: VideoConvertFormat): Uri {
        val baseName = inputName.substringBeforeLast('.', inputName)
            .replace(Regex("[^\\p{L}\\p{N}._-]"), "_").trim('_').ifBlank { "video" }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "${baseName}_${currentTimeMillis()}.${format.extension}")
            put(MediaStore.MediaColumns.MIME_TYPE, format.mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/FFmpegKitNext")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        return checkNotNull(resolver.insert(
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values,
        )) { "Output creation failed" }
    }
    override fun publish(uri: Uri) {
        check(resolver.update(uri, ContentValues().apply {
            put(MediaStore.MediaColumns.IS_PENDING, 0)
        }, null, null) > 0) { "Output publication failed" }
    }
    override fun delete(uri: Uri) {
        resolver.delete(uri, null, null)
    }
}
