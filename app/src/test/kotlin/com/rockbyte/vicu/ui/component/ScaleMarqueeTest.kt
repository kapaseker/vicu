package com.rockbyte.vicu.ui.component

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Test

/** 缩放框几何：等比角点缩放（对角固定）、模式切换收敛与输出尺寸换算。 */
class ScaleMarqueeTest {

    @Test
    fun bottomRightCornerAnchorsTopLeft() {
        val rect = CropRectF(0.2f, 0.1f, 0.8f, 0.7f)

        // 斜向内拖：增量沿锚点→角点对角线投影，Δs = (dx+dy)/2 = -0.25，左上角不动
        val moved = rect.moveCornerUniform(ScaleCorner.BOTTOM_RIGHT, dx = -0.2f, dy = -0.3f)

        assertRect(0.2f, 0.1f, 0.55f, 0.45f, moved)
    }

    @Test
    fun topLeftCornerAnchorsBottomRight() {
        val rect = CropRectF(0.2f, 0.1f, 0.8f, 0.7f)

        // 左上角向内拖：Δs = -(dx+dy)/2 = -0.25，对角 (0.8, 0.7) 固定
        val moved = rect.moveCornerUniform(ScaleCorner.TOP_LEFT, dx = 0.2f, dy = 0.3f)

        assertRect(0.45f, 0.35f, 0.8f, 0.7f, moved)
    }

    @Test
    fun topRightAndBottomLeftCornersAnchorOppositeCorner() {
        val rect = CropRectF(0.2f, 0.1f, 0.8f, 0.7f)

        // 右上角向内拖（dx<0, dy>0）：Δs = (dx-dy)/2 = -0.1，左下角固定
        val topRight = rect.moveCornerUniform(ScaleCorner.TOP_RIGHT, dx = -0.1f, dy = 0.1f)
        assertRect(0.2f, 0.2f, 0.7f, 0.7f, topRight)

        // 左下角向内拖（dx>0, dy<0）：Δs = (dy-dx)/2 = -0.1，右上角固定
        val bottomLeft = rect.moveCornerUniform(ScaleCorner.BOTTOM_LEFT, dx = 0.1f, dy = -0.1f)
        assertRect(0.3f, 0.1f, 0.8f, 0.6f, bottomLeft)
    }

    @Test
    fun singleAxisDragRespondsInstantly() {
        // 回归：只沿一个轴拖（最常见的抓取方式）也必须立即响应，不得出现 min 死区
        val rect = CropRectF(0.2f, 0.1f, 0.8f, 0.7f)

        val inward = rect.moveCornerUniform(ScaleCorner.BOTTOM_RIGHT, dx = -0.2f, dy = 0f)
        assertRect(0.2f, 0.1f, 0.7f, 0.6f, inward)

        val outward = rect.moveCornerUniform(ScaleCorner.BOTTOM_RIGHT, dx = 0.1f, dy = 0f)
        assertRect(0.2f, 0.1f, 0.85f, 0.75f, outward)
    }

    @Test
    fun perpendicularDragKeepsSideLength() {
        // 垂直于对角线方向的拖拽不改变边长（角点绕对角点做圆周运动的等价语义）
        val rect = CropRectF(0.2f, 0.1f, 0.8f, 0.7f)

        val moved = rect.moveCornerUniform(ScaleCorner.BOTTOM_RIGHT, dx = -0.2f, dy = 0.2f)

        assertRect(0.2f, 0.1f, 0.8f, 0.7f, moved)
    }

    @Test
    fun cornerDragClampsToPictureBounds() {
        val rect = CropRectF(0.2f, 0.1f, 0.8f, 0.7f)

        // 向外拖得太猛：Δs = 1 > 1 - left，钳到 0.8
        val grown = rect.moveCornerUniform(ScaleCorner.BOTTOM_RIGHT, dx = 1f, dy = 1f)
        assertRect(0.2f, 0.1f, 1f, 0.9f, grown)

        // 向内拖得太猛：钳到 minSize
        val shrunk = rect.moveCornerUniform(ScaleCorner.BOTTOM_RIGHT, dx = -2f, dy = -2f)
        assertRect(0.2f, 0.1f, 0.25f, 0.15f, shrunk)

        // 框贴右下：上界 = 1 - left / 1 - top，不会越出画面
        val pinned = CropRectF(0.9f, 0.85f, 1f, 1f)
            .moveCornerUniform(ScaleCorner.BOTTOM_RIGHT, dx = 1f, dy = 1f)
        assertRect(0.9f, 0.85f, 1f, 0.95f, pinned)
    }

    @Test
    fun snapToUniformTakesSmallerSideAnchoredTopLeft() {
        // 取较小的高：s = 0.3，左上角锚定
        assertRect(0.1f, 0.2f, 0.4f, 0.5f, CropRectF(0.1f, 0.2f, 0.4f, 0.6f).snapToUniform())
        // 已等比时不变
        assertRect(0.2f, 0.1f, 0.8f, 0.7f, CropRectF(0.2f, 0.1f, 0.8f, 0.7f).snapToUniform())
    }

    @Test
    fun snapToUniformDoesNotExceedPictureBounds() {
        // 左上锚定 + 边长不超过 1，框始终在画面内
        val snapped = CropRectF(0f, 0f, 1f, 1f).snapToUniform()
        assertRect(0f, 0f, 1f, 1f, snapped)
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
