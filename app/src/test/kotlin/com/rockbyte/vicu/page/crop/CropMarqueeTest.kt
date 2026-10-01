package com.rockbyte.vicu.page.crop

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.rockbyte.vicu.player.PlayerEffect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 剪切框几何：拖拽单边只改自身轴 + 边界/最小尺寸钳制；归一化选区 → 视频像素（YUV420 偶数化）。 */
class CropMarqueeTest {

    @Test
    fun movingLeftChangesOnlyLeftAxis() {
        val rect = CropRectF(0.2f, 0.1f, 0.8f, 0.9f)

        val moved = rect.moveEdge(CropEdge.LEFT, dx = 0.15f, dy = 0.4f)

        assertEquals(0.35f, moved.left, 1e-6f)
        // 只改自身轴：其余三边（含另一轴入参 dy）保持不变
        assertEquals(rect.top, moved.top, 1e-6f)
        assertEquals(rect.right, moved.right, 1e-6f)
        assertEquals(rect.bottom, moved.bottom, 1e-6f)
    }

    @Test
    fun movingRightChangesOnlyRightAxis() {
        val rect = CropRectF(0.2f, 0.1f, 0.8f, 0.9f)

        val moved = rect.moveEdge(CropEdge.RIGHT, dx = -0.3f, dy = 0.4f)

        assertEquals(0.5f, moved.right, 1e-6f)
        assertEquals(rect.left, moved.left, 1e-6f)
        assertEquals(rect.top, moved.top, 1e-6f)
        assertEquals(rect.bottom, moved.bottom, 1e-6f)
    }

    @Test
    fun movingTopAndBottomAffectOnlyYAxis() {
        val rect = CropRectF(0.2f, 0.1f, 0.8f, 0.9f)

        val top = rect.moveEdge(CropEdge.TOP, dx = 0.5f, dy = 0.2f)
        assertEquals(0.3f, top.top, 1e-6f)
        assertEquals(rect.left, top.left, 1e-6f)
        assertEquals(rect.right, top.right, 1e-6f)
        assertEquals(rect.bottom, top.bottom, 1e-6f)

        val bottom = rect.moveEdge(CropEdge.BOTTOM, dx = 0.5f, dy = -0.2f)
        assertEquals(0.7f, bottom.bottom, 1e-6f)
        assertEquals(rect.left, bottom.left, 1e-6f)
        assertEquals(rect.right, bottom.right, 1e-6f)
        assertEquals(rect.top, bottom.top, 1e-6f)
    }

    @Test
    fun movingBySmallDeltaDoesNotSnap() {
        // 抓取点偏离圆点时按增量移动：只走 dx，不会跳到手指绝对位置
        val rect = CropRectF(0.2f, 0f, 0.8f, 1f)

        assertEquals(0.21f, rect.moveEdge(CropEdge.LEFT, dx = 0.01f, dy = 0f).left, 1e-6f)
    }

    @Test
    fun leftEdgeCannotCrossRightEdge() {
        val rect = CropRectF(0.2f, 0f, 0.8f, 1f)

        // 与对边保留最小间距 0.05
        assertEquals(0.75f, rect.moveEdge(CropEdge.LEFT, dx = 1f, dy = 0f).left, 1e-6f)
    }

    @Test
    fun rightEdgeCannotCrossLeftEdge() {
        val rect = CropRectF(0.2f, 0f, 0.8f, 1f)

        assertEquals(0.25f, rect.moveEdge(CropEdge.RIGHT, dx = -1f, dy = 0f).right, 1e-6f)
    }

    @Test
    fun edgesClampToUnitBounds() {
        val rect = CropRectF(0.2f, 0.2f, 0.8f, 0.8f)

        assertEquals(0f, rect.moveEdge(CropEdge.LEFT, dx = -1f, dy = 0f).left, 1e-6f)
        assertEquals(0f, rect.moveEdge(CropEdge.TOP, dx = 0f, dy = -1f).top, 1e-6f)
        assertEquals(1f, rect.moveEdge(CropEdge.RIGHT, dx = 1f, dy = 0f).right, 1e-6f)
        assertEquals(1f, rect.moveEdge(CropEdge.BOTTOM, dx = 0f, dy = 1f).bottom, 1e-6f)
    }

    @Test
    fun degenerateRectDoesNotThrow() {
        // 退化区间（min > max）不应因 coerceIn 抛异常
        val rect = CropRectF(0.02f, 0.02f, 0.03f, 0.03f)

        assertEquals(0f, rect.moveEdge(CropEdge.LEFT, dx = -1f, dy = 0f).left, 1e-6f)
    }

    @Test
    fun leftAndRightEdgesAreDraggedHorizontally() {
        assertTrue(CropEdge.LEFT.isHorizontal)
        assertTrue(CropEdge.RIGHT.isHorizontal)
        assertFalse(CropEdge.TOP.isHorizontal)
        assertFalse(CropEdge.BOTTOM.isHorizontal)
    }

    @Test
    fun moveByTranslatesAllEdgesAndKeepsSize() {
        val rect = CropRectF(0.2f, 0.1f, 0.5f, 0.4f)

        val moved = rect.moveBy(dx = 0.25f, dy = -0.05f)

        assertRect(0.45f, 0.05f, 0.75f, 0.35f, moved)
        // 宽高不变：只平移不缩放
        assertEquals(0.3f, moved.right - moved.left, 1e-6f)
        assertEquals(0.3f, moved.bottom - moved.top, 1e-6f)
    }

    @Test
    fun moveByClampsToLeftAndTopBounds() {
        val rect = CropRectF(0.2f, 0.3f, 0.6f, 0.7f)

        // 左上越界后贴到 0，尺寸保持 0.4
        assertRect(0f, 0f, 0.4f, 0.4f, rect.moveBy(dx = -1f, dy = -1f))
    }

    @Test
    fun moveByClampsToRightAndBottomBounds() {
        val rect = CropRectF(0.2f, 0.3f, 0.6f, 0.7f)

        // 右下越界后贴到 1，左上角停在 1-尺寸
        assertRect(0.6f, 0.6f, 1f, 1f, rect.moveBy(dx = 1f, dy = 1f))
    }

    @Test
    fun moveByKeepsDegenerateRectsInBounds() {
        // 整幅画面（尺寸为 1）无处可移
        assertRect(0f, 0f, 1f, 1f, CropRectF(0f, 0f, 1f, 1f).moveBy(0.5f, 0.5f))
        // 超尺寸矩形（正常交互不会出现）：左上仍被夹回 0、尺寸原样保留，不因 coerceIn 抛异常
        assertRect(0f, 0f, 1.4f, 1.4f, CropRectF(-0.2f, -0.2f, 1.2f, 1.2f).moveBy(1f, 1f))
    }

    /** 四边逐项按容差比较：浮点累加不保证与字面量逐位相等。 */
    private fun assertRect(left: Float, top: Float, right: Float, bottom: Float, actual: CropRectF) {
        assertEquals(left, actual.left, 1e-6f)
        assertEquals(top, actual.top, 1e-6f)
        assertEquals(right, actual.right, 1e-6f)
        assertEquals(bottom, actual.bottom, 1e-6f)
    }

    @Test
    fun edgeCenterPxMatchesEdgeMidpoints() {
        val rect = CropRectF(0.25f, 0.25f, 0.75f, 0.75f)
        val size = IntSize(400, 400)

        assertEquals(Offset(100f, 200f), edgeCenterPx(CropEdge.LEFT, rect, size))
        assertEquals(Offset(300f, 200f), edgeCenterPx(CropEdge.RIGHT, rect, size))
        assertEquals(Offset(200f, 100f), edgeCenterPx(CropEdge.TOP, rect, size))
        assertEquals(Offset(200f, 300f), edgeCenterPx(CropEdge.BOTTOM, rect, size))
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
