package com.rockbyte.vicu.page.imagescale

import com.rockbyte.vicu.R
import com.rockbyte.vicu.ui.component.ScaleRatioPreset
import com.rockbyte.vicu.ui.component.labelRes
import com.rockbyte.vicu.ui.component.nextRatioSelection
import com.rockbyte.vicu.ui.component.outputRatio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** 预制比例的选择转移与方向映射。 */
class ImageScaleRatioTest {
    @Test
    fun clickingAnotherPresetSelectsItAndKeepsRememberedOrientation() {
        val (next, flipped) = nextRatioSelection(
            ScaleRatioPreset.Original, setOf(ScaleRatioPreset.FourThree), ScaleRatioPreset.FourThree)

        assertEquals(ScaleRatioPreset.FourThree, next)
        assertEquals(setOf(ScaleRatioPreset.FourThree), flipped)
    }

    @Test
    fun clickingSelectedFlippablePresetTogglesAndRemembersPerPreset() {
        val flipped = nextRatioSelection(ScaleRatioPreset.ThreeTwo, emptySet(), ScaleRatioPreset.ThreeTwo)
        assertEquals(ScaleRatioPreset.ThreeTwo, flipped.first)
        assertEquals(setOf(ScaleRatioPreset.ThreeTwo), flipped.second)

        val back = nextRatioSelection(flipped.first, flipped.second, ScaleRatioPreset.ThreeTwo)
        assertEquals(emptySet<ScaleRatioPreset>(), back.second)

        // 切走再切回：保留 3:2 上次翻到 2:3 的方向。
        val away = nextRatioSelection(flipped.first, flipped.second, ScaleRatioPreset.FourThree)
        val returned = nextRatioSelection(away.first, away.second, ScaleRatioPreset.ThreeTwo)
        assertEquals(setOf(ScaleRatioPreset.ThreeTwo), returned.second)
    }

    @Test
    fun clickingSelectedNonFlippablePresetDoesNothing() {
        listOf(ScaleRatioPreset.Original, ScaleRatioPreset.Square).forEach { preset ->
            val (next, flipped) = nextRatioSelection(preset, setOf(ScaleRatioPreset.ThreeTwo), preset)

            assertEquals(preset, next)
            assertEquals(setOf(ScaleRatioPreset.ThreeTwo), flipped)
        }
    }

    @Test
    fun orientationMapsToOutputRatioAndLabel() {
        assertEquals(1.5f, ScaleRatioPreset.ThreeTwo.outputRatio(false, 1.3333f), 1e-6f)
        assertEquals(1f / 1.5f, ScaleRatioPreset.ThreeTwo.outputRatio(true, 1.3333f), 1e-6f)
        assertEquals(R.string.image_scale_ratio_3_2, ScaleRatioPreset.ThreeTwo.labelRes(false))
        assertEquals(R.string.image_scale_ratio_2_3, ScaleRatioPreset.ThreeTwo.labelRes(true))

        // 原图比例跟随图片宽高比，且不参与翻转；1:1 翻转无变化。
        assertEquals(1.3333f, ScaleRatioPreset.Original.outputRatio(true, 1.3333f), 1e-6f)
        assertFalse(ScaleRatioPreset.Original.flippable)
        assertFalse(ScaleRatioPreset.Square.flippable)
        assertEquals(1f, ScaleRatioPreset.Square.outputRatio(true, 1.3333f), 1e-6f)
    }
}