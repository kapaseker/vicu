package com.rockbyte.vicu.page.crop

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.rockbyte.vicu.player.PlayerEffect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 剪切框几何：拖拽单边只改自身轴 + 边界/最小尺寸钳制；归一化选区 → 视频像素（YUV420 偶数化）。 */
class CropMarqueeTest {

    @Test
    fun draggingLeftChangesOnlyLeftAxis() {
        val rect = CropRectF(0.2f, 0.1f, 0.8f, 0.9f)

        val moved = rect.dragEdge(CropEdge.LEFT, x = 0.35f, y = 0.5f)

        assertEquals(0.35f, moved.left, 1e-6f)
        // 只改自身轴：其余三边（含另一轴入参 y）保持不变
        assertEquals(rect.top, moved.top, 1e-6f)
        assertEquals(rect.right, moved.right, 1e-6f)
        assertEquals(rect.bottom, moved.bottom, 1e-6f)
    }

    @Test
    fun draggingRightChangesOnlyRightAxis() {
        val rect = CropRectF(0.2f, 0.1f, 0.8f, 0.9f)

        val moved = rect.dragEdge(CropEdge.RIGHT, x = 0.6f, y = 0.5f)

        assertEquals(0.6f, moved.right, 1e-6f)
        assertEquals(rect.left, moved.left, 1e-6f)
        assertEquals(rect.top, moved.top, 1e-6f)
        assertEquals(rect.bottom, moved.bottom, 1e-6f)
    }

    @Test
    fun draggingTopAndBottomAffectOnlyYAxis() {
        val rect = CropRectF(0.2f, 0.1f, 0.8f, 0.9f)

        val top = rect.dragEdge(CropEdge.TOP, x = 0.5f, y = 0.3f)
        assertEquals(0.3f, top.top, 1e-6f)
        assertEquals(rect.left, top.left, 1e-6f)
        assertEquals(rect.right, top.right, 1e-6f)
        assertEquals(rect.bottom, top.bottom, 1e-6f)

        val bottom = rect.dragEdge(CropEdge.BOTTOM, x = 0.5f, y = 0.7f)
        assertEquals(0.7f, bottom.bottom, 1e-6f)
        assertEquals(rect.left, bottom.left, 1e-6f)
        assertEquals(rect.right, bottom.right, 1e-6f)
        assertEquals(rect.top, bottom.top, 1e-6f)
    }

    @Test
    fun leftEdgeCannotCrossRightEdge() {
        val rect = CropRectF(0.2f, 0f, 0.8f, 1f)

        val moved = rect.dragEdge(CropEdge.LEFT, x = 0.99f, y = 0f)

        // 与对边保留最小间距 0.05
        assertEquals(0.75f, moved.left, 1e-6f)
    }

    @Test
    fun rightEdgeCannotCrossLeftEdge() {
        val rect = CropRectF(0.2f, 0f, 0.8f, 1f)

        val moved = rect.dragEdge(CropEdge.RIGHT, x = 0f, y = 0f)

        assertEquals(0.25f, moved.right, 1e-6f)
    }

    @Test
    fun edgesClampToUnitBounds() {
        val rect = CropRectF(0.2f, 0.2f, 0.8f, 0.8f)

        assertEquals(0f, rect.dragEdge(CropEdge.LEFT, x = -0.5f, y = 0f).left, 1e-6f)
        assertEquals(0f, rect.dragEdge(CropEdge.TOP, x = 0f, y = -0.5f).top, 1e-6f)
        assertEquals(1f, rect.dragEdge(CropEdge.RIGHT, x = 1.5f, y = 0f).right, 1e-6f)
        assertEquals(1f, rect.dragEdge(CropEdge.BOTTOM, x = 0f, y = 1.5f).bottom, 1e-6f)
    }

    @Test
    fun degenerateRectDoesNotThrow() {
        // 退化区间（min > max）不应因 coerceIn 抛异常
        val rect = CropRectF(0.02f, 0.02f, 0.03f, 0.03f)

        assertEquals(0f, rect.dragEdge(CropEdge.LEFT, x = 0f, y = 0f).left, 1e-6f)
    }

    @Test
    fun hitEdgeMatchesHandleWithinTouchRadius() {
        val rect = CropRectF(0f, 0f, 1f, 1f)
        val size = IntSize(200, 100)

        // 四条边中点各命中对应边
        assertEquals(CropEdge.TOP, hitEdge(rect, size, Offset(100f, 4f), 24f))
        assertEquals(CropEdge.BOTTOM, hitEdge(rect, size, Offset(100f, 96f), 24f))
        assertEquals(CropEdge.LEFT, hitEdge(rect, size, Offset(4f, 50f), 24f))
        assertEquals(CropEdge.RIGHT, hitEdge(rect, size, Offset(196f, 50f), 24f))
    }

    @Test
    fun hitEdgePicksNearestWhenHandlesAreClose() {
        val rect = CropRectF(0.25f, 0.25f, 0.75f, 0.75f)
        val size = IntSize(400, 400)

        // LEFT 中心 (100,200)、TOP 中心 (200,100)：靠左者胜出
        assertEquals(CropEdge.LEFT, hitEdge(rect, size, Offset(100f, 205f), 24f))
    }

    @Test
    fun hitEdgeMissesOutsideTouchRadiusOrWithoutSize() {
        val rect = CropRectF(0f, 0f, 1f, 1f)

        // 框中心距任何圆点均超过命中半径
        assertNull(hitEdge(rect, IntSize(200, 100), Offset(100f, 50f), 24f))
        // 尚未测量到尺寸时不可命中（否则会误判为拖拽）
        assertNull(hitEdge(rect, IntSize.Zero, Offset(0f, 0f), 24f))
    }

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
