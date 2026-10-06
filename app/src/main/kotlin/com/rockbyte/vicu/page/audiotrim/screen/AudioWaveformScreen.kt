package com.rockbyte.vicu.page.audiotrim.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.IntOffset
import com.rockbyte.vicu.R
import com.rockbyte.vicu.page.audiotrim.*
import com.rockbyte.vicu.player.PlayerEffect
import com.rockbyte.vicu.repo.AudioWaveform
import com.rockbyte.vicu.ui.component.OutlineButton
import com.rockbyte.vicu.ui.component.StatusRow
import com.rockbyte.vicu.ui.component.trim.TrimRangeBar
import com.rockbyte.vicu.ui.component.trim.formatTrimTime
import com.rockbyte.vicu.ui.component.trim.moveTrimEndpoint
import com.rockbyte.vicu.ui.theme.VicuTheme
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
internal fun AudioWaveformScreen(
    state: AudioWaveformState,
    range: PlayerEffect.Trim,
    durationMs: Long,
    positionMs: Long,
    playing: Boolean,
    enabled: Boolean,
    onChange: (PlayerEffect.Trim, Long) -> Unit,
    onSeek: (Long) -> Unit,
    onRetry: () -> Unit,
) {
    if (state is AudioWaveformState.Ready) {
        WaveformPlayer(state.waveform, range, durationMs, positionMs, playing, enabled, onChange, onSeek)
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(VicuTheme.dimensions.spacingUnit)) {
            if (state == AudioWaveformState.Loading) {
                StatusRow(VicuTheme.colors.secondary, stringResource(R.string.waveform_loading), pulsing = true)
            } else if (state == AudioWaveformState.Failed) {
                StatusRow(VicuTheme.colors.error, stringResource(R.string.waveform_failed), textColor = VicuTheme.colors.error)
                OutlineButton(stringResource(R.string.waveform_retry), onRetry, enabled = enabled)
            }
            TrimRangeBar(range, durationMs, positionMs, enabled, onChange, onSeek)
        }
    }
}

@Composable
private fun WaveformPlayer(
    waveform: AudioWaveform,
    range: PlayerEffect.Trim,
    durationMs: Long,
    positionMs: Long,
    playing: Boolean,
    enabled: Boolean,
    onChange: (PlayerEffect.Trim, Long) -> Unit,
    onSeek: (Long) -> Unit,
) {
    val extentMs = waveformExtentMs(waveform, durationMs)
    var viewport by remember(waveform, extentMs) { mutableStateOf(WaveformViewport.full(extentMs)) }
    LaunchedEffect(positionMs, playing) { viewport = viewport.follow(positionMs, playing, extentMs) }
    Column(verticalArrangement = Arrangement.spacedBy(VicuTheme.dimensions.spacingUnit)) {
        WaveformCanvas(waveform, viewport, { viewport = it }, range, durationMs, extentMs, positionMs, enabled, onChange, onSeek)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            BasicText(formatTrimTime(viewport.startMs.toLong()),
                style = VicuTheme.typography.caption.copy(color = VicuTheme.colors.onSurfaceVariant))
            BasicText(formatTrimTime(viewport.endMs.toLong()),
                style = VicuTheme.typography.caption.copy(color = VicuTheme.colors.onSurfaceVariant))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(VicuTheme.dimensions.spacingUnit)) {
            OutlineButton(stringResource(R.string.show_all), { viewport = WaveformViewport.full(extentMs) },
                Modifier.weight(1f), enabled)
            OutlineButton(stringResource(R.string.locate_playback), { viewport = viewport.locate(positionMs, extentMs) },
                Modifier.weight(1f), enabled)
        }
        BasicText(stringResource(R.string.waveform_gestures),
            style = VicuTheme.typography.caption.copy(color = VicuTheme.colors.onSurfaceVariant))
    }
}

@Composable
private fun WaveformCanvas(
    waveform: AudioWaveform,
    viewport: WaveformViewport,
    onViewport: (WaveformViewport) -> Unit,
    range: PlayerEffect.Trim,
    durationMs: Long,
    extentMs: Long,
    positionMs: Long,
    enabled: Boolean,
    onChange: (PlayerEffect.Trim, Long) -> Unit,
    onSeek: (Long) -> Unit,
) {
    val currentViewport by rememberUpdatedState(viewport)
    val currentRange by rememberUpdatedState(range)
    val changeViewport by rememberUpdatedState(onViewport)
    val changeRange by rememberUpdatedState(onChange)
    val seek by rememberUpdatedState(onSeek)
    var width by remember { mutableIntStateOf(0) }
    val dimensions = VicuTheme.dimensions
    val colors = VicuTheme.colors
    val selectionAlpha = VicuTheme.alpha.cropDim
    val touchSize = dimensions.navigationTouchSize
    val density = LocalDensity.current
    val radius = with(density) { (touchSize / 2).toPx() }
    val stroke = with(density) { dimensions.cropBorderWidth.toPx() }
    val thumbRadius = with(density) { dimensions.playerProgressThumbSize.toPx() / 2 }
    val barWidth = with(density) { dimensions.playerProgressTrackHeight.toPx() }
    val trackWidth = (width - radius * 2).coerceAtLeast(1f)
    fun xAt(time: Long) = radius + (currentViewport.fractionAt(time) * trackWidth).toFloat()
    fun fractionAt(x: Float) = ((x - radius) / trackWidth).toDouble()
    val bars = remember(waveform, viewport, width, barWidth) {
        waveformBars(waveform, viewport, (trackWidth / (barWidth * 2)).toInt().coerceAtLeast(1))
    }
    val description = stringResource(R.string.waveform_description)
    val windowDescription = stringResource(R.string.waveform_window,
        formatTrimTime(viewport.startMs.toLong()), formatTrimTime(viewport.endMs.toLong()))
    val zoomIn = stringResource(R.string.waveform_zoom_in)
    val zoomOut = stringResource(R.string.waveform_zoom_out)
    val earlier = stringResource(R.string.view_previous_segment)
    val later = stringResource(R.string.view_next_segment)
    Box(Modifier.fillMaxWidth().height(dimensions.spacingUnit * 20)
        .onSizeChanged { width = it.width }
        .clip(VicuTheme.shapes.xl).background(colors.surfaceContainerLow)
        .alpha(if (enabled) VicuTheme.alpha.full else VicuTheme.alpha.disabled)
        .then(if (enabled) Modifier.systemGestureExclusion() else Modifier)
        .semantics {
            contentDescription = description
            stateDescription = windowDescription
            if (!enabled) disabled()
            customActions = listOf(
                CustomAccessibilityAction(zoomIn) { if (!enabled) false else { onViewport(viewport.zoom(2.0, 0.5, extentMs)); true } },
                CustomAccessibilityAction(zoomOut) { if (!enabled) false else { onViewport(viewport.zoom(0.5, 0.5, extentMs)); true } },
                CustomAccessibilityAction(earlier) { if (!enabled) false else { onViewport(viewport.pan(0.5, extentMs)); true } },
                CustomAccessibilityAction(later) { if (!enabled) false else { onViewport(viewport.pan(-0.5, extentMs)); true } },
            )
        }.focusable(enabled)
        .pointerInput(enabled, durationMs, extentMs, width) {
            if (!enabled || durationMs <= 0) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                var last = down.position
                var dragging = false
                var transformed = false
                var handle: Boolean? = null
                do {
                    val event = awaitPointerEvent()
                    val pressed = event.changes.filter { it.pressed }
                    if (pressed.size >= 2) {
                        transformed = true
                        val center = event.calculateCentroid(useCurrent = false)
                        val zoom = event.calculateZoom()
                        val pan = event.calculatePan()
                        changeViewport(currentViewport.zoom(zoom.toDouble(), fractionAt(center.x), extentMs)
                            .pan(pan.x / trackWidth.toDouble(), extentMs))
                        event.changes.forEach { it.consume() }
                    } else {
                        val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!dragging && !transformed) {
                            if (pointer.isConsumed) break
                            val distance = pointer.position - down.position
                            if (abs(distance.y) > viewConfiguration.touchSlop && abs(distance.y) > abs(distance.x)) break
                            if (abs(distance.x) > viewConfiguration.touchSlop) {
                                dragging = true
                                handle = waveformHandleAt(down.position.x - radius,
                                    xAt(currentRange.startMs) - radius, xAt(currentRange.endMs) - radius,
                                    radius, distance.x, trackWidth)
                            }
                        }
                        if (dragging && !transformed) {
                            val startHandle = handle
                            if (startHandle == null) changeViewport(currentViewport.pan(
                                (pointer.position.x - last.x) / trackWidth.toDouble(), extentMs))
                            else {
                                val time = currentViewport.timeAt(fractionAt(pointer.position.x)).toLong()
                                val updated = moveTrimEndpoint(currentRange, startHandle, time, durationMs)
                                changeRange(updated, if (startHandle) updated.startMs else updated.endMs)
                            }
                            pointer.consume()
                        } else if (!pointer.pressed && !transformed) {
                            val time = currentViewport.timeAt(fractionAt(pointer.position.x)).toLong()
                            if (time in currentRange.startMs..currentRange.endMs) { seek(time); pointer.consume() }
                        }
                        last = pointer.position
                    }
                } while (event.changes.any { it.pressed })
            }
        }) {
        Canvas(Modifier.matchParentSize()) {
            val y = size.height / 2
            val waveHeight = (size.height - radius * 2) / 2
            val startX = xAt(range.startMs)
            val endX = xAt(range.endMs)
            val left = startX.coerceIn(radius, size.width - radius)
            val right = endX.coerceIn(radius, size.width - radius)
            drawRect(colors.secondaryContainer.copy(alpha = selectionAlpha), Offset(left, radius), Size(max(0f, right - left), waveHeight * 2))
            drawLine(colors.outlineVariant, Offset(radius, y), Offset(size.width - radius, y), stroke)
            bars.forEachIndexed { i, peak ->
                val x = radius + (i + 0.5f) * trackWidth / bars.size
                val color = if (x in startX..endX) colors.primary else colors.outline
                drawLine(color, Offset(x, y - peak * waveHeight), Offset(x, y + peak * waveHeight), barWidth, StrokeCap.Round)
            }
            for (x in listOf(startX, endX)) {
                if (x in radius..(size.width - radius)) {
                    drawLine(colors.primary, Offset(x, radius), Offset(x, size.height - radius), stroke * 2)
                    drawCircle(colors.primary, thumbRadius, Offset(x, radius))
                }
            }
            val cursorX = xAt(positionMs)
            if (cursorX in radius..(size.width - radius)) {
                drawLine(colors.onSurfaceVariant, Offset(cursorX, radius / 2), Offset(cursorX, size.height - radius / 2), stroke)
                drawCircle(colors.onSurfaceVariant, thumbRadius, Offset(cursorX, size.height - radius / 2))
            }
        }
        for (start in listOf(true, false)) {
            val time = if (start) range.startMs else range.endMs
            val x = xAt(time)
            if (x !in radius..(width - radius)) continue
            val label = stringResource(if (start) R.string.start_time_seconds else R.string.end_time_seconds)
            // Distinct vertical regions keep both virtual controls accessible when endpoints overlap.
            Box(Modifier.align(if (start) Alignment.TopStart else Alignment.BottomStart)
                .offset { IntOffset((x - radius).roundToInt(), 0) }.width(touchSize).height(dimensions.spacingUnit * 10)
                .semantics {
                    contentDescription = label
                    stateDescription = formatTrimTime(time)
                    if (!enabled) disabled()
                    progressBarRangeInfo = ProgressBarRangeInfo(time.toFloat(),
                        if (start) 0f..(range.endMs - 1).toFloat() else (range.startMs + 1).toFloat()..durationMs.toFloat())
                    setProgress { value ->
                        if (!enabled) false else {
                            val updated = moveTrimEndpoint(currentRange, start, value.toLong(), durationMs)
                            changeRange(updated, if (start) updated.startMs else updated.endMs)
                            true
                        }
                    }
                }.focusable(enabled))
        }
    }
}
