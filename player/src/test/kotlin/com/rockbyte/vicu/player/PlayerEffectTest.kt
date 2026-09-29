package com.rockbyte.vicu.player

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/** 共用滤镜描述：预览链（crop）与导出参数（-vf / -ss -to）的字符串生成。 */
class PlayerEffectTest {

    @Test
    fun previewChainWithoutEffectsIsEmpty() {
        assertEquals("", emptyList<PlayerEffect>().toPreviewFilterChain())
    }

    @Test
    fun previewChainRendersCrop() {
        val chain = listOf(PlayerEffect.Crop(left = 10, top = 20, width = 100, height = 50))
            .toPreviewFilterChain()
        assertEquals("crop=w=100:h=50:x=10:y=20", chain)
    }

    @Test
    fun previewChainSkipsTrim() {
        val chain = listOf(PlayerEffect.Trim(startMs = 1000, endMs = 2000)).toPreviewFilterChain()
        assertEquals("", chain)
    }

    @Test
    fun previewChainNormalizesOddCropValues() {
        // YUV420 色度对齐：坐标与尺寸偶数化（向下取整），尺寸至少 2
        val chain = listOf(PlayerEffect.Crop(left = 11, top = 21, width = 101, height = 51))
            .toPreviewFilterChain()
        assertEquals("crop=w=100:h=50:x=10:y=20", chain)
    }

    @Test
    fun previewChainClampsNegativeCropOrigin() {
        val chain = listOf(PlayerEffect.Crop(left = -4, top = -8, width = 100, height = 50))
            .toPreviewFilterChain()
        assertEquals("crop=w=100:h=50:x=0:y=0", chain)
    }

    @Test
    fun previewChainJoinsCropsInListOrder() {
        val chain = listOf(
            PlayerEffect.Crop(left = 0, top = 0, width = 100, height = 100),
            PlayerEffect.Crop(left = 10, top = 10, width = 50, height = 50),
        ).toPreviewFilterChain()
        assertEquals("crop=w=100:h=100:x=0:y=0,crop=w=50:h=50:x=10:y=10", chain)
    }

    @Test
    fun exportArgumentsWithoutEffectsAreEmpty() {
        assertArrayEquals(emptyArray<String>(), emptyList<PlayerEffect>().toExportArguments())
    }

    @Test
    fun exportArgumentsRenderCropAsVideoFilter() {
        val args = listOf(PlayerEffect.Crop(left = 10, top = 20, width = 100, height = 50))
            .toExportArguments()
        assertArrayEquals(arrayOf("-vf", "crop=w=100:h=50:x=10:y=20"), args)
    }

    @Test
    fun exportArgumentsRenderTrimAsSeekRange() {
        val args = listOf(PlayerEffect.Trim(startMs = 1500, endMs = 3200))
            .toExportArguments()
        assertArrayEquals(arrayOf("-ss", "1500ms", "-to", "3200ms"), args)
    }

    @Test
    fun exportArgumentsCombineTrimAndCrop() {
        val args = listOf(
            PlayerEffect.Crop(left = 0, top = 0, width = 640, height = 480),
            PlayerEffect.Trim(startMs = 500, endMs = 2500),
        ).toExportArguments()
        assertArrayEquals(
            arrayOf("-ss", "500ms", "-to", "2500ms", "-vf", "crop=w=640:h=480:x=0:y=0"),
            args,
        )
    }
}
