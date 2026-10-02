package com.rockbyte.vicu.page.crop

import com.rockbyte.vicu.ui.component.CropRectF
import com.rockbyte.vicu.player.PlayerEffect
import org.junit.Assert.assertEquals
import org.junit.Test

class CropMarqueeTest {
    @Test
    fun toCropConvertsNormalizedRectToPixels() {
        val crop = CropRectF(0.25f, 0.25f, 0.75f, 0.75f).toCrop(1920, 1080)

        assertEquals(PlayerEffect.Crop(left = 480, top = 270, width = 960, height = 540), crop)
    }

    @Test
    fun toCropNormalizesOddDimensions() {
        val crop = CropRectF(0f, 0f, 1f, 1f).toCrop(1921, 1081)

        // YUV420 偶数化：奇数尺寸向下取整
        assertEquals(PlayerEffect.Crop(left = 0, top = 0, width = 1920, height = 1080), crop)
    }
}
