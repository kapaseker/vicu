package com.rockbyte.vicu.page

import android.Manifest
import com.rockbyte.vicu.repo.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaPermissionsTest {
    @Test
    fun preTiramisuUsesLegacyStoragePermission() {
        assertEquals(listOf(Manifest.permission.READ_EXTERNAL_STORAGE), mediaPermissionsForSdk(29))
        assertEquals(listOf(Manifest.permission.READ_EXTERNAL_STORAGE), mediaPermissionsForSdk(32))
        MediaKind.entries.forEach { kind ->
            assertEquals(Manifest.permission.READ_EXTERNAL_STORAGE, mediaPermissionFor(kind, 32))
        }
    }

    @Test
    fun tiramisuUsesPermissionMatchingEachMediaKind() {
        assertEquals(Manifest.permission.READ_MEDIA_IMAGES, mediaPermissionFor(MediaKind.IMAGE, 33))
        assertEquals(Manifest.permission.READ_MEDIA_VIDEO, mediaPermissionFor(MediaKind.VIDEO, 33))
        assertEquals(Manifest.permission.READ_MEDIA_AUDIO, mediaPermissionFor(MediaKind.AUDIO, 33))
        assertEquals(
            listOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO,
            ),
            mediaPermissionsForSdk(33),
        )
    }
}
