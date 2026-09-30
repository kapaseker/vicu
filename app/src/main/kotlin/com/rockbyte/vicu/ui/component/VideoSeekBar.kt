package com.rockbyte.vicu.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import com.rockbyte.vicu.ui.theme.VicuTheme

/**
 * 自定义进度条（无 Material3）：pill 轨道 + 黑色填充与圆形 thumb。
 * 拖拽期间显示本地预览位置并逐帧刷新画面（音频静音），松手才跳转并恢复播放；点按直接跳转。
 * 播放页与裁剪页共用。
 */
@Composable
fun VideoSeekBar(
    positionMs: Long,
    durationMs: Long,
    enabled: Boolean,
    onSeek: (Long) -> Unit,
    onScrubStart: () -> Unit,
    onScrub: (Long) -> Unit,
    onScrubEnd: (Long) -> Unit,
) {
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    var trackWidthPx by remember { mutableFloatStateOf(0f) }
    val fraction = dragFraction
        ?: if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val thumbSize = VicuTheme.dimensions.playerProgressThumbSize
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(VicuTheme.dimensions.playerProgressTouchHeight)
            .onSizeChanged { trackWidthPx = it.width.toFloat() }
            .alpha(if (enabled) VicuTheme.alpha.full else VicuTheme.alpha.disabled)
            .pointerInput(enabled, durationMs) {
                if (!enabled || durationMs <= 0) return@pointerInput
                detectTapGestures { offset ->
                    val tapped = (offset.x / trackWidthPx).coerceIn(0f, 1f)
                    onSeek((tapped * durationMs).toLong())
                }
            }
            .pointerInput(enabled, durationMs) {
                if (!enabled || durationMs <= 0) return@pointerInput
                fun fractionAt(x: Float) = (x / trackWidthPx).coerceIn(0f, 1f)
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        val f = fractionAt(offset.x)
                        dragFraction = f
                        onScrubStart()
                        onScrub((f * durationMs).toLong())
                    },
                    onHorizontalDrag = { change, _ ->
                        val f = fractionAt(change.position.x)
                        dragFraction = f
                        onScrub((f * durationMs).toLong())
                    },
                    onDragEnd = {
                        dragFraction?.let { f -> onScrubEnd((f * durationMs).toLong()) }
                        dragFraction = null
                    },
                    onDragCancel = {
                        dragFraction?.let { f -> onScrubEnd((f * durationMs).toLong()) }
                        dragFraction = null
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(VicuTheme.dimensions.playerProgressTrackHeight)
                .clip(VicuTheme.shapes.full)
                .background(VicuTheme.colors.outlineVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .clip(VicuTheme.shapes.full)
                    .background(VicuTheme.colors.primary),
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset {
                    IntOffset(
                        x = (trackWidthPx * fraction).roundToInt() - thumbSize.roundToPx() / 2,
                        y = 0,
                    )
                }
                .size(thumbSize)
                .clip(CircleShape)
                .background(VicuTheme.colors.primary),
        )
    }
}
