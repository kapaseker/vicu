package com.rockbyte.vicu.page

import android.Manifest
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaPermissionsTest {

    @Test
    fun preTiramisuUsesLegacyStoragePermission() {
        assertEquals(
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE),
            mediaPermissionsForSdk(29),
        )
        assertEquals(
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE),
            mediaPermissionsForSdk(32),
        )
    }

    @Test
    fun tiramisuAndAboveUsesGranularMediaPermissions() {
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
