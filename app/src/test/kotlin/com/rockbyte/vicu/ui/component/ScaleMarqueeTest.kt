package com.rockbyte.vicu.ui.component

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Test

/** 居中缩放、模式切换与输出尺寸换算。 */
class ScaleMarqueeTest {
    @Test
    fun allCornersResizeAroundCenter() {
        val rect = CropRectF(0.2f, 0.2f, 0.8f, 0.8f)
        val drags = listOf(
            Triple(ScaleCorner.TOP_LEFT, 0.1f, 0.1f),
            Triple(ScaleCorner.TOP_RIGHT, -0.1f, 0.1f),
            Triple(ScaleCorner.BOTTOM_LEFT, 0.1f, -0.1f),
            Triple(ScaleCorner.BOTTOM_RIGHT, -0.1f, -0.1f),
        )
        drags.forEach { (corner, dx, dy) ->
            assertRect(0.3f, 0.3f, 0.7f, 0.7f, rect.moveCornerUniform(corner, dx, dy))
        }
    }

    @Test
    fun singleAxisDragRespondsAndPerpendicularDragKeepsSize() {
        val rect = CropRectF(0.2f, 0.2f, 0.8f, 0.8f)
        assertRect(0.3f, 0.3f, 0.7f, 0.7f,
            rect.moveCornerUniform(ScaleCorner.BOTTOM_RIGHT, -0.2f, 0f))
        assertRect(0.2f, 0.2f, 0.8f, 0.8f,
            rect.moveCornerUniform(ScaleCorner.BOTTOM_RIGHT, -0.2f, 0.2f))
    }

    @Test
    fun everyCornerClampsToCenteredLimits() {
        ScaleCorner.entries.forEach { corner ->
            val x = if (corner == ScaleCorner.TOP_LEFT || corner == ScaleCorner.BOTTOM_LEFT) -1f else 1f
            val y = if (corner == ScaleCorner.TOP_LEFT || corner == ScaleCorner.TOP_RIGHT) -1f else 1f
            val rect = CropRectF(0.2f, 0.2f, 0.8f, 0.8f)
            assertRect(0f, 0f, 1f, 1f, rect.moveCornerUniform(corner, x, y))
            assertRect(0.475f, 0.475f, 0.525f, 0.525f, rect.moveCornerUniform(corner, -x, -y))
        }
    }

    @Test
    fun snapToUniformUsesSmallerScaleAndCenters() {
        assertRect(0.35f, 0.35f, 0.65f, 0.65f,
            CropRectF(0.35f, 0.2f, 0.65f, 0.8f).snapToUniform())
        assertRect(0f, 0f, 1f, 1f, CropRectF(0f, 0f, 1f, 1f).snapToUniform())
    }

    @Test
    fun freeEdgesMirrorOppositeEdgeAndKeepOtherAxis() {
        val rect = CropRectF(0.2f, 0.3f, 0.8f, 0.7f)
        assertRect(0.3f, 0.3f, 0.7f, 0.7f, rect.moveScaleEdge(CropEdge.LEFT, 0.1f, 0.5f))
        assertRect(0.3f, 0.3f, 0.7f, 0.7f, rect.moveScaleEdge(CropEdge.RIGHT, -0.1f, 0.5f))
        assertRect(0.2f, 0.4f, 0.8f, 0.6f, rect.moveScaleEdge(CropEdge.TOP, 0.5f, 0.1f))
        assertRect(0.2f, 0.4f, 0.8f, 0.6f, rect.moveScaleEdge(CropEdge.BOTTOM, 0.5f, -0.1f))
    }

    @Test
    fun freeEdgesClampToCenteredLimits() {
        val rect = CropRectF(0.2f, 0.3f, 0.8f, 0.7f)
        CropEdge.entries.forEach { edge ->
            val direction = if (edge == CropEdge.LEFT || edge == CropEdge.TOP) -1f else 1f
            val grown = rect.moveScaleEdge(edge, direction, direction)
            val shrunk = rect.moveScaleEdge(edge, -direction, -direction)
            if (edge.isHorizontal) {
                assertRect(0f, 0.3f, 1f, 0.7f, grown)
                assertRect(0.475f, 0.3f, 0.525f, 0.7f, shrunk)
            } else {
                assertRect(0.2f, 0f, 0.8f, 1f, grown)
                assertRect(0.2f, 0.475f, 0.8f, 0.525f, shrunk)
            }
        }
    }

    @Test
    fun landscapeAndPortraitOutputKeepOriginalAspectRatio() {
        val rect = CropRectF(0f, 0f, 1f, 1f)
            .moveCornerUniform(ScaleCorner.BOTTOM_RIGHT, -0.25f, -0.25f)
        assertEquals(ScaleOutputSize(800, 450), scaleOutputSize(rect, 1600, 900))
        assertEquals(ScaleOutputSize(450, 800), scaleOutputSize(rect, 900, 1600))
        assertRect(0.25f, 0.25f, 0.75f, 0.75f, rect)
    }

    @Test
    fun scaleOutputSizeRoundsAndClampsToPicture() {
        assertEquals(ScaleOutputSize(360, 150), scaleOutputSize(CropRectF(0.1f, 0.2f, 0.46f, 0.5f), 1000, 500))
        // 满框 = 原图；空框/退化框钳到至少 1
        assertEquals(ScaleOutputSize(1000, 500), scaleOutputSize(CropRectF(0f, 0f, 1f, 1f), 1000, 500))
        assertEquals(ScaleOutputSize(1, 1), scaleOutputSize(CropRectF(0.4f, 0.4f, 0.4001f, 0.4001f), 1000, 500))
    }

    @Test
    fun cornerCenterPxMatchesRectCorners() {
        val rect = CropRectF(0.25f, 0.25f, 0.75f, 0.75f)
        val size = IntSize(400, 200)

        assertEquals(Offset(100f, 50f), cornerCenterPx(ScaleCorner.TOP_LEFT, rect, size))
        assertEquals(Offset(300f, 50f), cornerCenterPx(ScaleCorner.TOP_RIGHT, rect, size))
        assertEquals(Offset(100f, 150f), cornerCenterPx(ScaleCorner.BOTTOM_LEFT, rect, size))
        assertEquals(Offset(300f, 150f), cornerCenterPx(ScaleCorner.BOTTOM_RIGHT, rect, size))
    }

    /** 四边逐项按容差比较：浮点累加不保证与字面量逐位相等。 */
    private fun assertRect(left: Float, top: Float, right: Float, bottom: Float, actual: CropRectF) {
        assertEquals(left, actual.left, 1e-6f)
        assertEquals(top, actual.top, 1e-6f)
        assertEquals(right, actual.right, 1e-6f)
        assertEquals(bottom, actual.bottom, 1e-6f)
    }
}
