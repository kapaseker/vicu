package com.rockbyte.vicu.repo

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size

internal class MediaLibraryStorage(context: Context) : MediaLibraryStore {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver

    override val permissionsToRequest: List<String> = mediaPermissionsForSdk(Build.VERSION.SDK_INT)

    override fun canRead(kind: MediaKind): Boolean =
        appContext.checkSelfPermission(mediaPermissionFor(kind, Build.VERSION.SDK_INT)) ==
            PackageManager.PERMISSION_GRANTED

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
                    dateAdded = cursor.getLong(dateIndex),
                )
            }
        }
        return items
    }

    override fun loadThumbnail(uri: Uri, width: Int, height: Int): Bitmap =
        resolver.loadThumbnail(uri, Size(width, height), null)
}

internal fun mediaPermissionsForSdk(sdkInt: Int): List<String> =
    if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
        MediaKind.entries.map { mediaPermissionFor(it, sdkInt) }
    } else {
        listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

internal fun mediaPermissionFor(kind: MediaKind, sdkInt: Int): String =
    if (sdkInt < Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_EXTERNAL_STORAGE
    } else {
        when (kind) {
            MediaKind.IMAGE -> Manifest.permission.READ_MEDIA_IMAGES
            MediaKind.VIDEO -> Manifest.permission.READ_MEDIA_VIDEO
            MediaKind.AUDIO -> Manifest.permission.READ_MEDIA_AUDIO
        }
    }

private val MediaKind.collection: Uri
    get() = when (this) {
        MediaKind.IMAGE -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        MediaKind.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        MediaKind.AUDIO -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    }
