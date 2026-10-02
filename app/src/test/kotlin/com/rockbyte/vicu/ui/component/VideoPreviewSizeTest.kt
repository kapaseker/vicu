package com.rockbyte.vicu.ui.component

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class VideoPreviewSizeTest {
    @Test
    fun landscapePreviewUsesAvailableWidth() {
        assertEquals(360.dp, videoPreviewWidth(360.dp, 700.dp, 16f / 9f))
    }

    @Test
    fun portraitPreviewPreservesAspectRatioAtHeightLimit() {
        val ratio = 9f / 16f
        val width = videoPreviewWidth(360.dp, 700.dp, ratio)
        assertEquals(216.5625f, width.value, 0.001f)
        assertEquals(385f, (width / ratio).value, 0.001f)
    }
}
