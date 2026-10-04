package com.rockbyte.vicu.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.rockbyte.vicu.ui.theme.VicuTheme
import com.rockbyte.vicu.R
import kotlin.math.hypot
import kotlin.math.roundToInt

/** 归一化裁剪框（0..1，相对媒体画面）。 */
internal data class CropRectF(val left: Float, val top: Float, val right: Float, val bottom: Float)

/** 剪切框的四条边。 */
internal enum class CropEdge { LEFT, TOP, RIGHT, BOTTOM }

/** 左右两条边贴画面两侧，需要申请排除系统手势区。 */
internal val CropEdge.isHorizontal: Boolean
    get() = this == CropEdge.LEFT || this == CropEdge.RIGHT

/**
 * 按归一化增量平移指定边：每条边只改自身轴（注意拖拽方向），
 * 结果 clamp 在 [0,1] 且与对边保留至少 [minSize] 间距。
 *
 * 用增量而不是绝对坐标：抓取点未必压准圆点（触区有 24dp 半径），
 * 绝对语义会让这条边在第一次移动时直接跳到手指位置。
 */
internal fun CropRectF.moveEdge(edge: CropEdge, dx: Float, dy: Float, minSize: Float = 0.05f): CropRectF {
    fun clamp(value: Float, min: Float, max: Float) = value.coerceIn(min, max.coerceAtLeast(min))
    return when (edge) {
        CropEdge.LEFT -> copy(left = clamp(left + dx, 0f, right - minSize))
        CropEdge.RIGHT -> copy(right = clamp(right + dx, left + minSize, 1f))
        CropEdge.TOP -> copy(top = clamp(top + dy, 0f, bottom - minSize))
        CropEdge.BOTTOM -> copy(bottom = clamp(bottom + dy, top + minSize, 1f))
    }
}

/**
 * 按归一化增量整体平移裁剪框：宽高不变，左上角 clamp 在 [0, 1-尺寸] 内保证整框不越界。
 * 框铺满整幅画面（尺寸为 1）时平移量恒为 0。
 */
internal fun CropRectF.moveBy(dx: Float, dy: Float): CropRectF {
    val width = right - left
    val height = bottom - top
    val newLeft = (left + dx).coerceIn(0f, (1f - width).coerceAtLeast(0f))
    val newTop = (top + dy).coerceIn(0f, (1f - height).coerceAtLeast(0f))
    return CropRectF(newLeft, newTop, newLeft + width, newTop + height)
}

/** 指定边中点的像素坐标：归一化矩形 [rect] 乘以覆盖层像素尺寸 [size]。 */
internal fun edgeCenterPx(edge: CropEdge, rect: CropRectF, size: IntSize): Offset {
    val w = size.width.toFloat()
    val h = size.height.toFloat()
    val midX = (rect.left + rect.right) / 2f * w
    val midY = (rect.top + rect.bottom) / 2f * h
    return when (edge) {
        CropEdge.LEFT -> Offset(rect.left * w, midY)
        CropEdge.RIGHT -> Offset(rect.right * w, midY)
        CropEdge.TOP -> Offset(midX, rect.top * h)
        CropEdge.BOTTOM -> Offset(midX, rect.bottom * h)
    }
}

/**
 * 返回距 [position] 最近且在 [touchRadiusPx] 内的边，否则 null。
 * 仅用于单击时判断落点是否压在圆点上：拖拽由四个独立手柄节点负责，不靠这里命中。
 */
internal fun hitEdge(
    rect: CropRectF,
    size: IntSize,
    position: Offset,
    touchRadiusPx: Float,
): CropEdge? {
    if (size.width <= 0 || size.height <= 0) return null
    var best: CropEdge? = null
    var bestDistance = touchRadiusPx
    CropEdge.entries.forEach { edge ->
        val center = edgeCenterPx(edge, rect, size)
        val distance = hypot(position.x - center.x, position.y - center.y)
        if (distance < bestDistance) {
            best = edge
            bestDistance = distance
        }
    }
    return best
}

/**
 * 剪切框覆盖层：Canvas 画虚线边框 + 框外 30% 黑遮罩 + 四条边中点圆点，单击画面切换播放状态；
 * 四个圆点是各自独立的手势节点（透明触区），统一接收 2D 增量；各边几何上只应用自身轴。
 * 框内还有一块平移触区：按住框内拖动整体挪动选区（不改变宽高）。
 *
 * 不给单覆盖层叠 tap/drag 两个检测器：detectTapGestures 会消费 down，drag 永远收不到事件。
 * 也不用「单节点 + 最近边命中」：命中靠猜，框接近最小尺寸时四条边中点会挤进同一个触圈。
 * 平移区与边手柄的重叠交给节点序：边手柄后声明、命中优先且先 consume，抓圆点必然是缩放。
 */
@Composable
internal fun CropMarquee(
    rect: CropRectF,
    enabled: Boolean,
    onRectChange: (CropRectF) -> Unit,
    modifier: Modifier = Modifier,
    onTap: (() -> Unit)? = null,
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
    // 命中半径（px）：Canvas 只负责视觉，手势由手柄节点承担；这里只用于单击时排除落在圆点上的按下
    val touchRadiusPx = with(density) { VicuTheme.dimensions.cropHandleTouchRadius.toPx() }
    // 手势节点只在 key 变化时重启，回调里一律读最新值，避免捕获过期的 rect/尺寸
    val latestRect by rememberUpdatedState(rect)
    val latestSize by rememberUpdatedState(overlaySize)
    val latestOnRectChange by rememberUpdatedState(onRectChange)
    val latestOnTap by rememberUpdatedState(onTap)

    Box(modifier = modifier.onSizeChanged { overlaySize = it }) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(enabled, onTap != null) {
                    if (!enabled || latestOnTap == null) return@pointerInput
                    detectTapGestures { position ->
                        // 落在圆点上的按下交给手柄节点，不算「单击画面」
                        if (hitEdge(latestRect, latestSize, position, touchRadiusPx) == null) {
                            latestOnTap?.invoke()
                        }
                    }
                },
        ) {
            val left = rect.left * size.width
            val top = rect.top * size.height
            val right = rect.right * size.width
            val bottom = rect.bottom * size.height
            // 框外 30% 黑遮罩：整屏半透明黑 + 差集挖空框内
            val hole = Path().apply { addRect(Rect(left, top, right, bottom)) }
            clipPath(hole, ClipOp.Difference) {
                drawRect(color = dimColor, topLeft = Offset.Zero, size = size)
            }
            // 虚线边框
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
            // 四条边中点圆点（视觉；手势由下面的 CropHandle 触区承担）
            val radius = handleSize.toPx() / 2f
            listOf(
                Offset((left + right) / 2f, top),
                Offset((left + right) / 2f, bottom),
                Offset(left, (top + bottom) / 2f),
                Offset(right, (top + bottom) / 2f),
            ).forEach { center -> drawCircle(color = handleColor, radius = radius, center = center) }
        }

        if (enabled && overlaySize.width > 0 && overlaySize.height > 0) {
            // 框内整体平移：先于四个边手柄声明，保证抓在圆点上时由手柄（后声明者命中优先）处理缩放
            CropMoveArea(
                offsetPx = IntOffset(
                    (rect.left * overlaySize.width).roundToInt(),
                    (rect.top * overlaySize.height).roundToInt(),
                ),
                sizePx = IntSize(
                    ((rect.right - rect.left) * overlaySize.width).roundToInt(),
                    ((rect.bottom - rect.top) * overlaySize.height).roundToInt(),
                ),
                enabled = enabled,
                onDrag = { dx, dy ->
                    val size = latestSize
                    val w = size.width.toFloat()
                    val h = size.height.toFloat()
                    if (w > 0f && h > 0f) {
                        latestOnRectChange(latestRect.moveBy(dx / w, dy / h))
                    }
                },
            )
            // 触区以圆点为中心原样放置，不夹回覆盖层：圆点压在画面边缘时（初始选区就是整幅画面）
            // 圆点外侧的触区照样有效。Compose 允许子节点越出父 bounds 命中，页面上唯一的裁剪者
            // verticalScroll 只沿交叉轴裁且边界是整屏宽，越界部分落在页边距内、照样可命中。
            CropEdge.entries.forEach { edge ->
                val center = edgeCenterPx(edge, rect, overlaySize)
                val description = stringResource(when (edge) {
                    CropEdge.LEFT -> R.string.crop_edge_left
                    CropEdge.TOP -> R.string.crop_edge_top
                    CropEdge.RIGHT -> R.string.crop_edge_right
                    CropEdge.BOTTOM -> R.string.crop_edge_bottom
                })
                val edgePosition = when (edge) {
                    CropEdge.LEFT -> rect.left
                    CropEdge.TOP -> rect.top
                    CropEdge.RIGHT -> rect.right
                    CropEdge.BOTTOM -> rect.bottom
                }
                CropHandle(
                    edge = edge,
                    offsetPx = IntOffset(
                        (center.x - touchRadiusPx).roundToInt(),
                        (center.y - touchRadiusPx).roundToInt(),
                    ),
                    boxSize = touchBoxSize,
                    enabled = enabled,
                    modifier = Modifier.semantics {
                        contentDescription = description
                        progressBarRangeInfo = ProgressBarRangeInfo(edgePosition, 0f..1f)
                        setProgress { target ->
                            if (!target.isFinite()) return@setProgress false
                            val current = latestRect
                            val moved = when (edge) {
                                CropEdge.LEFT -> current.moveEdge(edge, target - current.left, 0f)
                                CropEdge.RIGHT -> current.moveEdge(edge, target - current.right, 0f)
                                CropEdge.TOP -> current.moveEdge(edge, 0f, target - current.top)
                                CropEdge.BOTTOM -> current.moveEdge(edge, 0f, target - current.bottom)
                            }
                            latestOnRectChange(moved)
                            true
                        }
                    },
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

/**
 * 框内平移触区：覆盖裁剪框内部，接收 2D 增量整体移动选区（不改变宽高）。
 * 用增量（dragAmount）而不是绝对坐标，与四条边手柄一致；平移量由 moveBy 钳制在画面内。
 */
@Composable
private fun CropMoveArea(
    offsetPx: IntOffset,
    sizePx: IntSize,
    enabled: Boolean,
    onDrag: (dx: Float, dy: Float) -> Unit,
) {
    val latestOnDrag by rememberUpdatedState(onDrag)
    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .offset { offsetPx }
            .size(
                width = with(density) { sizePx.width.toDp() },
                height = with(density) { sizePx.height.toDp() },
            )
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectDragGestures { _, dragAmount -> latestOnDrag(dragAmount.x, dragAmount.y) }
            },
    )
}

/**
 * 单条边的拖拽手柄：透明触区 Box，接收 2D 增量。
 * 用增量（dragAmount）而不是绝对坐标，抓取点不压准圆点也不会让这条边跳变；
 * 检测器不区分轴向——斜向拖拽立即跨过 touch slop 开始响应，
 * 垂直于边方向的分量由 moveEdge 的几何钳制自然忽略。
 */
@Composable
internal fun CropHandle(
    edge: CropEdge,
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
            .then(if (enabled && edge.isHorizontal) Modifier.systemGestureExclusion() else Modifier)
            .pointerInput(edge, enabled) {
                if (!enabled) return@pointerInput
                detectDragGestures { _, dragAmount -> latestOnDrag(dragAmount.x, dragAmount.y) }
            },
    )
}
