package com.rockbyte.vicu.ui.component.trim

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.IntOffset
import com.rockbyte.vicu.R
import com.rockbyte.vicu.player.PlayerEffect
import com.rockbyte.vicu.ui.theme.VicuTheme
import kotlin.math.abs
import kotlin.math.roundToInt

/** 双端区间选择，点击区间内跳转；端点语义允许辅助功能调节毫秒时间。 */
@Composable
internal fun TrimRangeBar(
    range: PlayerEffect.Trim,
    durationMs: Long,
    positionMs: Long,
    enabled: Boolean,
    onChange: (PlayerEffect.Trim, Long) -> Unit,
    onSeek: (Long) -> Unit,
) {
    val currentRange by rememberUpdatedState(range)
    val change by rememberUpdatedState(onChange)
    val seek by rememberUpdatedState(onSeek)
    var width by remember { mutableIntStateOf(0) }
    val touchSize = VicuTheme.dimensions.navigationTouchSize
    val radius = with(androidx.compose.ui.platform.LocalDensity.current) { (touchSize / 2).toPx() }
    val primary = VicuTheme.colors.primary
    val outline = VicuTheme.colors.outlineVariant
    val cursorColor = VicuTheme.colors.onPrimary
    val thumbRadius = with(androidx.compose.ui.platform.LocalDensity.current) { VicuTheme.dimensions.playerProgressThumbSize.toPx() / 2 }
    val trackHeight = with(androidx.compose.ui.platform.LocalDensity.current) { VicuTheme.dimensions.playerProgressTrackHeight.toPx() }
    val cursorWidth = with(androidx.compose.ui.platform.LocalDensity.current) { VicuTheme.dimensions.cropBorderWidth.toPx() }
    fun xAt(time: Long) = radius + (width - radius * 2).coerceAtLeast(1f) * (time.toDouble() / durationMs.coerceAtLeast(1)).toFloat()
    fun timeAt(x: Float) = (((x - radius) / (width - radius * 2).coerceAtLeast(1f)).coerceIn(0f, 1f).toDouble() * durationMs).toLong()
    Box(
        Modifier.fillMaxWidth().height(touchSize).onSizeChanged { width = it.width }
            .alpha(if (enabled) VicuTheme.alpha.full else VicuTheme.alpha.disabled)
            .then(if (enabled) Modifier.systemGestureExclusion() else Modifier)
            .pointerInput(enabled, durationMs, width) {
                if (!enabled || durationMs <= 0) return@pointerInput
                detectTapGestures { offset ->
                    val time = timeAt(offset.x)
                    if (time in currentRange.startMs..currentRange.endMs) seek(time)
                }
            }
            .pointerInput(enabled, durationMs, width) {
                if (!enabled || durationMs <= 0) return@pointerInput
                var startHandle = true
                fun move(x: Float) {
                    val old = currentRange
                    val updated = moveTrimEndpoint(old, startHandle, timeAt(x), durationMs)
                    change(updated, if (startHandle) updated.startMs else updated.endMs)
                }
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        startHandle = abs(offset.x - xAt(currentRange.startMs)) <= abs(offset.x - xAt(currentRange.endMs))
                        move(offset.x)
                    },
                    onHorizontalDrag = { event, _ -> event.consume(); move(event.position.x) },
                )
            },
    ) {
        Canvas(Modifier.matchParentSize()) {
            val y = size.height / 2
            val startX = xAt(range.startMs)
            val endX = xAt(range.endMs)
            drawLine(outline, Offset(radius, y), Offset(size.width - radius, y), trackHeight, StrokeCap.Round)
            drawLine(primary, Offset(startX, y), Offset(endX, y), trackHeight, StrokeCap.Round)
            val cursorX = xAt(positionMs.coerceIn(range.startMs, range.endMs))
            drawLine(primary, Offset(cursorX, y - thumbRadius), Offset(cursorX, y + thumbRadius), cursorWidth)
            drawLine(cursorColor, Offset(cursorX, y - trackHeight / 2), Offset(cursorX, y + trackHeight / 2), cursorWidth)
            drawCircle(primary, thumbRadius, Offset(startX, y))
            drawCircle(primary, thumbRadius, Offset(endX, y))
        }
        for (start in listOf(true, false)) {
            val time = if (start) range.startMs else range.endMs
            val label = stringResource(if (start) R.string.trim_start else R.string.trim_end)
            Box(Modifier.align(Alignment.CenterStart)
                .offset { IntOffset((xAt(time) - radius).roundToInt(), 0) }.size(touchSize)
                .semantics {
                    contentDescription = label
                    stateDescription = formatTrimTime(time)
                    if (!enabled) disabled()
                    progressBarRangeInfo = ProgressBarRangeInfo(time.toFloat(),
                        if (start) 0f..(range.endMs - 1).toFloat() else (range.startMs + 1).toFloat()..durationMs.coerceAtLeast(1).toFloat())
                    setProgress { value ->
                        if (!enabled) false else {
                            val updated = moveTrimEndpoint(currentRange, start, value.toLong(), durationMs)
                            change(updated, if (start) updated.startMs else updated.endMs)
                            true
                        }
                    }
                }.focusable(enabled))
        }
    }
}

internal fun moveTrimEndpoint(range: PlayerEffect.Trim, start: Boolean, timeMs: Long, durationMs: Long): PlayerEffect.Trim =
    if (start) range.copy(startMs = timeMs.coerceIn(0, range.endMs - 1))
    else range.copy(endMs = timeMs.coerceIn(range.startMs + 1, durationMs))
