package com.rockbyte.vicu.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.rockbyte.vicu.R
import com.rockbyte.vicu.ui.theme.VicuTheme
import kotlin.math.roundToInt

/** 缩放框的四个角。 */
internal enum class ScaleCorner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

/**
 * 等比缩放：拖 [corner] 时对角固定，两轴同时改为同一边长。
 * 增量沿「锚点 → 角点」的对角线投影成边长变化（等价于角点绕对角点做圆周运动）：
 * 任意方向都有即时、成比例的响应。此前取 min(两轴提议) 的做法有死区——
 * 等比框宽高恒相等，单轴向外拖时另一轴提议不变，min 永远选中不动的轴，框完全不动。
 * 结果 clamp 到 [minSize, 1] 与对角可达范围内。
 */
internal fun CropRectF.moveCornerUniform(
    corner: ScaleCorner, dx: Float, dy: Float, minSize: Float = 0.05f,
): CropRectF {
    val projection = when (corner) {
        ScaleCorner.BOTTOM_RIGHT -> (dx + dy) / 2f
        ScaleCorner.TOP_LEFT -> -(dx + dy) / 2f
        ScaleCorner.TOP_RIGHT -> (dx - dy) / 2f
        ScaleCorner.BOTTOM_LEFT -> (dy - dx) / 2f
    }
    val sizeMax = when (corner) {
        ScaleCorner.TOP_LEFT -> minOf(right, bottom)
        ScaleCorner.TOP_RIGHT -> minOf(1f - left, bottom)
        ScaleCorner.BOTTOM_LEFT -> minOf(right, 1f - top)
        ScaleCorner.BOTTOM_RIGHT -> minOf(1f - left, 1f - top)
    }
    val side = (right - left + projection).coerceIn(minSize, sizeMax.coerceAtLeast(minSize))
    return when (corner) {
        ScaleCorner.TOP_LEFT -> copy(left = right - side, top = bottom - side)
        ScaleCorner.TOP_RIGHT -> copy(right = left + side, top = bottom - side)
        ScaleCorner.BOTTOM_LEFT -> copy(left = right - side, bottom = top + side)
        ScaleCorner.BOTTOM_RIGHT -> copy(right = left + side, bottom = top + side)
    }
}

/**
 * 切到等比模式：取宽高占比较小者作为新边长（不放大已缩小的部分），左上角锚定。
 */
internal fun CropRectF.snapToUniform(minSize: Float = 0.05f): CropRectF {
    val side = minOf(right - left, bottom - top).coerceIn(minSize, 1f)
    return CropRectF(left, top, left + side, top + side)
}

/** 输出像素尺寸：框宽高占比 × 原图，逐轴 round 后夹在 [1, 原图]。 */
internal data class ScaleOutputSize(val width: Int, val height: Int)

internal fun scaleOutputSize(rect: CropRectF, imageWidth: Int, imageHeight: Int): ScaleOutputSize =
    ScaleOutputSize(
        ((rect.right - rect.left).coerceIn(0f, 1f) * imageWidth).roundToInt().coerceIn(1, imageWidth),
        ((rect.bottom - rect.top).coerceIn(0f, 1f) * imageHeight).roundToInt().coerceIn(1, imageHeight),
    )

/** 角点中心像素坐标：归一化矩形 [rect] 乘以覆盖层像素尺寸 [size]。 */
internal fun cornerCenterPx(corner: ScaleCorner, rect: CropRectF, size: IntSize): Offset {
    val w = size.width.toFloat()
    val h = size.height.toFloat()
    val x = (if (corner == ScaleCorner.TOP_LEFT || corner == ScaleCorner.BOTTOM_LEFT) rect.left else rect.right) * w
    val y = (if (corner == ScaleCorner.TOP_LEFT || corner == ScaleCorner.TOP_RIGHT) rect.top else rect.bottom) * h
    return Offset(x, y)
}

/**
 * 缩放框覆盖层：Canvas 画虚线边框 + 框外 30% 黑遮罩，输出像素 = 框宽高占比 × 原图。
 * 等比模式四个圆点画在四角，拖任意角点对角固定等比缩放；
 * 非等比模式圆点画在四条边中点，交互与 CropMarquee 一致（各边只动单轴，可拉伸变形）。
 * 手势结构照搬 CropMarquee：手柄是独立节点（先平移区后手柄的节点序问题此处不存在，
 * 没有框内平移区），增量语义防跳变，触区 coerce 在覆盖层内防越界。
 */
@Composable
internal fun ScaleMarquee(
    rect: CropRectF,
    uniform: Boolean,
    enabled: Boolean,
    onRectChange: (CropRectF) -> Unit,
    modifier: Modifier = Modifier,
) {
    var overlaySize by remember { mutableStateOf(IntSize.Zero) }
    val borderColor = VicuTheme.colors.onSurface
    val handleColor = VicuTheme.colors.primary
    val dimColor = Color.Black.copy(alpha = VicuTheme.alpha.cropDim)
    val handleSize = VicuTheme.dimensions.cropHandleSize
    val borderWidth = VicuTheme.dimensions.cropBorderWidth
    val dashLength = VicuTheme.dimensions.cropDashLength
    val dashGap = VicuTheme.dimensions.cropDashGap
    val density = LocalDensity.current
    val touchBoxSize = VicuTheme.dimensions.cropHandleTouchRadius * 2
    val touchRadiusPx = with(density) { VicuTheme.dimensions.cropHandleTouchRadius.toPx() }
    // 手势节点只在 key 变化时重启，回调里一律读最新值，避免捕获过期的 rect/尺寸
    val latestRect by rememberUpdatedState(rect)
    val latestSize by rememberUpdatedState(overlaySize)
    val latestOnRectChange by rememberUpdatedState(onRectChange)

    Box(modifier = modifier.onSizeChanged { overlaySize = it }) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val left = rect.left * size.width
            val top = rect.top * size.height
            val right = rect.right * size.width
            val bottom = rect.bottom * size.height
            // 框外 30% 黑遮罩：整屏半透明黑 + 差集挖空框内
            val hole = Path().apply { addRect(Rect(left, top, right, bottom)) }
            clipPath(hole, ClipOp.Difference) {
                drawRect(color = dimColor, topLeft = Offset.Zero, size = size)
            }
            drawRect(
                color = borderColor,
                topLeft = Offset(left, top),
                size = Size(right - left, bottom - top),
                style = Stroke(
                    width = borderWidth.toPx(),
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(dashLength.toPx(), dashGap.toPx()),
                    ),
                ),
            )
            // 圆点：等比画四角、非等比画四条边中点（视觉；手势由下方触区承担）
            val radius = handleSize.toPx() / 2f
            val centers = if (uniform) {
                listOf(Offset(left, top), Offset(right, top), Offset(left, bottom), Offset(right, bottom))
            } else {
                listOf(
                    Offset((left + right) / 2f, top),
                    Offset((left + right) / 2f, bottom),
                    Offset(left, (top + bottom) / 2f),
                    Offset(right, (top + bottom) / 2f),
                )
            }
            centers.forEach { center -> drawCircle(color = handleColor, radius = radius, center = center) }
        }

        if (enabled && overlaySize.width > 0 && overlaySize.height > 0) {
            // 触区以圆点为中心原样放置，不夹回覆盖层：圆点压在画面边缘时（初始选区就是整幅画面）
            // 圆点外侧的触区照样有效。Compose 允许子节点越出父 bounds 命中，页面上唯一的裁剪者
            // verticalScroll 只沿交叉轴裁且边界是整屏宽，越界部分落在页边距内、照样可命中。
            if (uniform) {
                ScaleCorner.entries.forEach { corner ->
                    val center = cornerCenterPx(corner, rect, overlaySize)
                    val description = stringResource(when (corner) {
                        ScaleCorner.TOP_LEFT -> R.string.scale_corner_top_left
                        ScaleCorner.TOP_RIGHT -> R.string.scale_corner_top_right
                        ScaleCorner.BOTTOM_LEFT -> R.string.scale_corner_bottom_left
                        ScaleCorner.BOTTOM_RIGHT -> R.string.scale_corner_bottom_right
                    })
                    ScaleCornerHandle(
                        corner = corner,
                        offsetPx = IntOffset(
                            (center.x - touchRadiusPx).roundToInt(),
                            (center.y - touchRadiusPx).roundToInt(),
                        ),
                        boxSize = touchBoxSize,
                        enabled = enabled,
                        modifier = Modifier.semantics { contentDescription = description },
                        onDrag = { dx, dy ->
                            val size = latestSize
                            val w = size.width.toFloat()
                            val h = size.height.toFloat()
                            if (w > 0f && h > 0f) {
                                latestOnRectChange(latestRect.moveCornerUniform(corner, dx / w, dy / h))
                            }
                        },
                    )
                }
            } else {
                CropEdge.entries.forEach { edge ->
                    val center = edgeCenterPx(edge, rect, overlaySize)
                    val description = stringResource(when (edge) {
                        CropEdge.LEFT -> R.string.scale_edge_left
                        CropEdge.TOP -> R.string.scale_edge_top
                        CropEdge.RIGHT -> R.string.scale_edge_right
                        CropEdge.BOTTOM -> R.string.scale_edge_bottom
                    })
                    CropHandle(
                        edge = edge,
                        offsetPx = IntOffset(
                            (center.x - touchRadiusPx).roundToInt(),
                            (center.y - touchRadiusPx).roundToInt(),
                        ),
                        boxSize = touchBoxSize,
                        enabled = enabled,
                        modifier = Modifier.semantics { contentDescription = description },
                        onDrag = { dx, dy ->
                            val size = latestSize
                            val w = size.width.toFloat()
                            val h = size.height.toFloat()
                            if (w > 0f && h > 0f) {
                                latestOnRectChange(latestRect.moveEdge(edge, dx / w, dy / h))
                            }
                        },
                    )
                }
            }
        }
    }
}

/**
 * 等比模式的角点手柄：透明触区 Box，接收 2D 增量（横竖都吃，由 moveCornerUniform 取小轴定边长）。
 * 角点既有横向也有纵向增量，同样排除系统手势区。
 */
@Composable
private fun ScaleCornerHandle(
    corner: ScaleCorner,
    offsetPx: IntOffset,
    boxSize: Dp,
    enabled: Boolean,
    modifier: Modifier,
    onDrag: (dx: Float, dy: Float) -> Unit,
) {
    val latestOnDrag by rememberUpdatedState(onDrag)
    Box(
        modifier = Modifier
            .offset { offsetPx }
            .size(boxSize)
            .then(modifier)
            .then(if (enabled) Modifier.systemGestureExclusion() else Modifier)
            .pointerInput(corner, enabled) {
                if (!enabled) return@pointerInput
                detectDragGestures { _, dragAmount -> latestOnDrag(dragAmount.x, dragAmount.y) }
            },
    )
}
