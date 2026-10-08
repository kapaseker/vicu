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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
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
 * 以图片中心保比例缩放；两轴拖动投影到角点方向，对边同步移动。
 * [ratio] 为归一化框宽高比（框宽占比 / 框高占比）；取 1f 时框为正方形，等价于保持原图比例。
 */
internal fun CropRectF.moveCornerUniform(
    corner: ScaleCorner, dx: Float, dy: Float, minSize: Float = 0.05f, ratio: Float = 1f,
): CropRectF {
    val delta = when (corner) {
        ScaleCorner.BOTTOM_RIGHT -> dx + dy
        ScaleCorner.TOP_LEFT -> -(dx + dy)
        ScaleCorner.TOP_RIGHT -> dx - dy
        ScaleCorner.BOTTOM_LEFT -> dy - dx
    }
    return fittedCenteredRect(right - left + delta, ratio, minSize)
}

/** 非等比缩放只改变拖动轴，对边镜像移动。 */
internal fun CropRectF.moveScaleEdge(
    edge: CropEdge, dx: Float, dy: Float, minSize: Float = 0.05f,
): CropRectF {
    val width = right - left
    val height = bottom - top
    return when (edge) {
        CropEdge.LEFT -> centeredScaleRect((width - 2f * dx).coerceIn(minSize, 1f), height)
        CropEdge.RIGHT -> centeredScaleRect((width + 2f * dx).coerceIn(minSize, 1f), height)
        CropEdge.TOP -> centeredScaleRect(width, (height - 2f * dy).coerceIn(minSize, 1f))
        CropEdge.BOTTOM -> centeredScaleRect(width, (height + 2f * dy).coerceIn(minSize, 1f))
    }
}

private fun centeredScaleRect(width: Float, height: Float): CropRectF =
    CropRectF((1f - width) / 2f, (1f - height) / 2f, (1f + width) / 2f, (1f + height) / 2f)

/**
 * 以归一化宽 [width] 为驱动尺寸、按归一化宽高比 [ratio] 求居中矩形：驱动宽夹在 [minSize, 1]，
 * 另一轴按比例推导；推导轴撑出图片（> 1）时改由该轴反推回宽。结果恒居中且不超出图片。
 * ponytail: 单轮修正；归一化比例极端（超出约 20 倍）时推导轴会小于 minSize，届时需改为按面积求解。
 */
internal fun fittedCenteredRect(width: Float, ratio: Float, minSize: Float = 0.05f): CropRectF {
    val k = ratio.coerceAtLeast(1e-4f)
    var w = width.coerceIn(minSize, 1f)
    var h = w / k
    if (h > 1f) { h = 1f; w = h * k }
    if (h < minSize) { h = minSize; w = h * k }
    if (w > 1f) { w = 1f; h = w / k }
    return centeredScaleRect(w, h)
}

/** 该归一化宽高比在当前图片内能放下的最大居中矩形。 */
internal fun maxCenteredRect(ratio: Float, minSize: Float = 0.05f): CropRectF =
    fittedCenteredRect(1f, ratio, minSize)

/** 输出宽高比 → 归一化框宽高比（框以图片宽高为量纲，故需除以图片宽高比）。 */
internal fun normalizedRectRatio(outputRatio: Float, imageAspect: Float): Float =
    outputRatio / imageAspect

/** 切回等比模式时按 [ratio] 收敛：保持较小边，图片居中。 */
internal fun CropRectF.snapToUniform(minSize: Float = 0.05f, ratio: Float = 1f): CropRectF {
    val width = minOf(right - left, (bottom - top) * ratio)
    return fittedCenteredRect(width, ratio, minSize)
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

/** 缩放边框与手柄：等比模式拖四角（保持 [ratio] 归一化宽高比），非等比模式拖边中点；均以图片中心缩放。 */
@Composable
internal fun ScaleMarquee(
    rect: CropRectF,
    uniform: Boolean,
    ratio: Float,
    enabled: Boolean,
    onRectChange: (CropRectF) -> Unit,
    modifier: Modifier = Modifier,
) {
    var overlaySize by remember { mutableStateOf(IntSize.Zero) }
    val borderColor = VicuTheme.colors.onSurface
    val handleColor = VicuTheme.colors.primary
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
                                latestOnRectChange(latestRect.moveCornerUniform(corner, dx / w, dy / h, ratio = ratio))
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
                                latestOnRectChange(latestRect.moveScaleEdge(edge, dx / w, dy / h))
                            }
                        },
                    )
                }
            }
        }
    }
}

/**
 * 等比模式的角点手柄：透明触区 Box，接收 2D 增量（横竖都吃，由 moveCornerUniform 投影计算缩放比例）。
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
