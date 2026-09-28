package com.rockbyte.vicu.page.player

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import com.rockbyte.vicu.R
import com.rockbyte.vicu.repo.EffectSpec
import com.rockbyte.vicu.repo.PlayerError
import com.rockbyte.vicu.repo.SelectedMedia
import com.rockbyte.vicu.repo.normalized
import com.rockbyte.vicu.ui.component.PrimaryIconButton
import com.rockbyte.vicu.ui.component.VicuButton
import com.rockbyte.vicu.ui.component.StatusRow
import com.rockbyte.vicu.ui.component.VicuScaffold
import com.rockbyte.vicu.ui.theme.VicuTheme
import org.koin.androidx.compose.koinViewModel

/**
 * 播放页：原生引擎播放所选视频，支持播放/暂停、进度拖拽与 seek；
 * 效果编辑模式提供 crop 拖拽手柄与 trim 双端滑杆（预览与导出共用效果描述）。
 * 页面离开时释放引擎（引擎由 [PlayerViewModel.release] 终结）。
 */
@Composable
fun PlayerPage(media: SelectedMedia, onBack: () -> Unit) {
    val viewModel = koinViewModel<PlayerViewModel>()
    LaunchedEffect(media) { viewModel.bind(media) }
    DisposableEffect(Unit) {
        onDispose { viewModel.release() }
    }
    val state by viewModel.uiState.collectAsState()

    PlayerContent(
        state = state,
        onTogglePlayPause = viewModel::togglePlayPause,
        onSeek = viewModel::seekTo,
        onCropChange = viewModel::setCrop,
        onTrimChange = viewModel::setTrim,
        onSurfaceAvailable = viewModel::setSurface,
        onBack = onBack,
    )
}

@Composable
private fun PlayerContent(
    state: PlayerUiState,
    onTogglePlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onCropChange: (EffectSpec.Crop?) -> Unit,
    onTrimChange: (EffectSpec.Trim?) -> Unit,
    onSurfaceAvailable: (android.view.Surface?) -> Unit,
    onBack: () -> Unit,
) {
    var editMode by remember { mutableStateOf(false) }
    VicuScaffold(
        title = stringResource(R.string.video_play),
        onBack = onBack,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(VicuTheme.dimensions.screenGutter),
            verticalArrangement = Arrangement.spacedBy(VicuTheme.dimensions.spacingUnit * 2),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(videoAspectRatio(state)),
            ) {
                VideoSurface(
                    modifier = Modifier.matchParentSize(),
                    onSurfaceAvailable = onSurfaceAvailable,
                )
                if (editMode && state.videoWidth > 0 && state.videoHeight > 0) {
                    CropOverlay(
                        videoWidth = state.videoWidth,
                        videoHeight = state.videoHeight,
                        crop = state.crop,
                        onCropChange = onCropChange,
                    )
                }
            }
            PlayerControls(
                state = state,
                editMode = editMode,
                onToggleEditMode = { editMode = !editMode },
                onTogglePlayPause = onTogglePlayPause,
                onSeek = onSeek,
                onCropChange = onCropChange,
                onTrimChange = onTrimChange,
            )
        }
    }
}

/** 预览就绪前按 16:9 占位，就绪后切换真实宽高比。 */
private fun videoAspectRatio(state: PlayerUiState): Float =
    if (state.videoWidth > 0 && state.videoHeight > 0) {
        state.videoWidth.toFloat() / state.videoHeight
    } else {
        16f / 9f
    }

@Composable
private fun VideoSurface(
    modifier: Modifier = Modifier,
    onSurfaceAvailable: (android.view.Surface?) -> Unit,
) {
    AndroidView(
        factory = { context ->
            SurfaceView(context).apply {
                holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) = Unit

                    override fun surfaceChanged(
                        holder: SurfaceHolder,
                        format: Int,
                        width: Int,
                        height: Int,
                    ) {
                        onSurfaceAvailable(holder.surface)
                    }

                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                        onSurfaceAvailable(null)
                    }
                })
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun PlayerControls(
    state: PlayerUiState,
    editMode: Boolean,
    onToggleEditMode: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onCropChange: (EffectSpec.Crop?) -> Unit,
    onTrimChange: (EffectSpec.Trim?) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(VicuTheme.dimensions.spacingUnit),
    ) {
        PlayerSeekBar(
            positionMs = state.positionMs,
            durationMs = state.durationMs,
            enabled = state.durationMs > 0 && state.phase in seekablePhases,
            onSeek = onSeek,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(VicuTheme.dimensions.spacingUnit),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PrimaryIconButton(
                icon = if (state.playing) R.drawable.ic_pause else R.drawable.ic_play,
                text = playButtonText(state),
                onClick = onTogglePlayPause,
                enabled = state.phase != PlayerPhase.Preparing,
            )
            BasicText(
                text = playerTimeText(state.positionMs, state.durationMs),
                style = VicuTheme.typography.bodySm.copy(color = VicuTheme.colors.onSurfaceVariant),
            )
            Spacer(Modifier.weight(1f))
            VicuButton(
                onClick = onToggleEditMode,
                style = VicuTheme.styles.secondaryButton,
                rippleColor = VicuTheme.colors.onSurface,
                enabled = state.phase != PlayerPhase.Preparing,
            ) {
                BasicText(
                    text = stringResource(
                        if (editMode) R.string.player_effect_done else R.string.player_effect_edit
                    ),
                )
            }
        }
        if (editMode && state.durationMs > 0) {
            EffectPanel(
                state = state,
                onCropChange = onCropChange,
                onTrimChange = onTrimChange,
            )
        }
        when (val phase = state.phase) {
            is PlayerPhase.Failed -> StatusRow(
                dotColor = VicuTheme.colors.error,
                text = stringResource(R.string.player_failed, stringResource(phase.error.messageRes)),
                textColor = VicuTheme.colors.error,
            )
            PlayerPhase.Preparing -> StatusRow(
                dotColor = VicuTheme.colors.secondary,
                text = stringResource(R.string.player_preparing),
                pulsing = true,
            )
            else -> Unit
        }
    }
}

/** 效果面板：trim 双端滑杆 + 区间文本 + crop/trim 重置。 */
@Composable
private fun EffectPanel(
    state: PlayerUiState,
    onCropChange: (EffectSpec.Crop?) -> Unit,
    onTrimChange: (EffectSpec.Trim?) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(VicuTheme.dimensions.spacingUnit),
    ) {
        TrimRangeSlider(
            durationMs = state.durationMs,
            trim = state.trim,
            onTrimChange = onTrimChange,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(VicuTheme.dimensions.spacingUnit),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                text = trimRangeText(state.trim, state.durationMs),
                style = VicuTheme.typography.bodySm.copy(color = VicuTheme.colors.onSurfaceVariant),
            )
            Spacer(Modifier.weight(1f))
            VicuButton(
                onClick = { onCropChange(null) },
                style = VicuTheme.styles.secondaryButton,
                rippleColor = VicuTheme.colors.onSurface,
                enabled = state.crop != null,
            ) {
                BasicText(text = stringResource(R.string.player_crop_reset))
            }
            VicuButton(
                onClick = { onTrimChange(null) },
                style = VicuTheme.styles.secondaryButton,
                rippleColor = VicuTheme.colors.onSurface,
                enabled = state.trim != null,
            ) {
                BasicText(text = stringResource(R.string.player_trim_reset))
            }
        }
    }
}

/** trim 区间文本（未设置时显示全片范围）。 */
private fun trimRangeText(trim: EffectSpec.Trim?, durationMs: Long): String {
    val start = (trim?.startMs ?: 0L) / 1000
    val end = (trim?.endMs ?: durationMs) / 1000
    return "${formatClock(start)} – ${formatClock(end)}"
}

private val seekablePhases = setOf(PlayerPhase.Playing, PlayerPhase.Paused, PlayerPhase.Ended)

/**
 * 自定义进度条（无 Material3）：pill 轨道 + 黑色填充与圆形 thumb。
 * 拖拽期间显示本地预览位置，松手才触发 [onSeek]；点按直接跳转。
 */
@Composable
private fun PlayerSeekBar(
    positionMs: Long,
    durationMs: Long,
    enabled: Boolean,
    onSeek: (Long) -> Unit,
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
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, _ ->
                        dragFraction = (change.position.x / trackWidthPx).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        dragFraction?.let { f -> onSeek((f * durationMs).toLong()) }
                        dragFraction = null
                    },
                    onDragCancel = { dragFraction = null },
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

/** 归一化裁剪框（0..1，相对视频画面）。 */
private data class CropRectF(val left: Float, val top: Float, val right: Float, val bottom: Float)

/** crop 拖拽覆盖层：边框 + 四角手柄；拖拽松手换算视频像素坐标回调。 */
@Composable
private fun CropOverlay(
    videoWidth: Int,
    videoHeight: Int,
    crop: EffectSpec.Crop?,
    onCropChange: (EffectSpec.Crop?) -> Unit,
) {
    var rect by remember(crop) {
        mutableStateOf(
            crop?.let {
                CropRectF(
                    it.left.toFloat() / videoWidth,
                    it.top.toFloat() / videoHeight,
                    (it.left + it.width).toFloat() / videoWidth,
                    (it.top + it.height).toFloat() / videoHeight,
                )
            } ?: CropRectF(0f, 0f, 1f, 1f)
        )
    }
    var dragCorner by remember { mutableIntStateOf(-1) }
    var overlaySize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val thumbSize = VicuTheme.dimensions.playerProgressThumbSize
    // 最小可拖尺寸（对应 2 视频像素），防止框缩成点
    val minW = (2f / videoWidth).coerceAtLeast(0.02f)
    val minH = (2f / videoHeight).coerceAtLeast(0.02f)

    fun hitCorner(x: Float, y: Float): Int {
        val radius = with(density) { 24.dp.toPx() }
        val corners = arrayOf(
            rect.left to rect.top,
            rect.right to rect.top,
            rect.left to rect.bottom,
            rect.right to rect.bottom,
        )
        var best = -1
        var bestDistance = radius
        corners.forEachIndexed { index, (cx, cy) ->
            val px = cx * overlaySize.width
            val py = cy * overlaySize.height
            val distance = hypot(x - px, y - py)
            if (distance < bestDistance) {
                best = index
                bestDistance = distance
            }
        }
        return best
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { overlaySize = it }
            .pointerInput(videoWidth, videoHeight) {
                detectDragGestures(
                    onDragStart = { offset -> dragCorner = hitCorner(offset.x, offset.y) },
                    onDrag = { change, _ ->
                        val corner = dragCorner
                        if (corner < 0) return@detectDragGestures
                        change.consume()
                        val x = (change.position.x / overlaySize.width).coerceIn(0f, 1f)
                        val y = (change.position.y / overlaySize.height).coerceIn(0f, 1f)
                        rect = when (corner) {
                            0 -> rect.copy(
                                left = x.coerceAtMost(rect.right - minW),
                                top = y.coerceAtMost(rect.bottom - minH),
                            )
                            1 -> rect.copy(
                                right = x.coerceAtLeast(rect.left + minW),
                                top = y.coerceAtMost(rect.bottom - minH),
                            )
                            2 -> rect.copy(
                                left = x.coerceAtMost(rect.right - minW),
                                bottom = y.coerceAtLeast(rect.top + minH),
                            )
                            else -> rect.copy(
                                right = x.coerceAtLeast(rect.left + minW),
                                bottom = y.coerceAtLeast(rect.top + minH),
                            )
                        }
                    },
                    onDragEnd = {
                        if (dragCorner >= 0) {
                            onCropChange(
                                EffectSpec.Crop(
                                    left = (rect.left * videoWidth).toInt(),
                                    top = (rect.top * videoHeight).toInt(),
                                    width = ((rect.right - rect.left) * videoWidth).toInt(),
                                    height = ((rect.bottom - rect.top) * videoHeight).toInt(),
                                ).normalized()
                            )
                        }
                        dragCorner = -1
                    },
                    onDragCancel = { dragCorner = -1 },
                )
            },
    ) {
        // 裁剪框边框
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        (rect.left * overlaySize.width).roundToInt(),
                        (rect.top * overlaySize.height).roundToInt(),
                    )
                }
                .size(
                    with(density) {
                        ((rect.right - rect.left) * overlaySize.width).toDp()
                    },
                    with(density) {
                        ((rect.bottom - rect.top) * overlaySize.height).toDp()
                    },
                )
                .border(2.dp, VicuTheme.colors.onSurface)
        )
        // 四角手柄（触控命中半径 24dp，见 hitCorner）
        val corners = listOf(
            rect.left to rect.top,
            rect.right to rect.top,
            rect.left to rect.bottom,
            rect.right to rect.bottom,
        )
        corners.forEach { (cx, cy) ->
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (cx * overlaySize.width).roundToInt() - (thumbSize.toPx() / 2).roundToInt(),
                            (cy * overlaySize.height).roundToInt() - (thumbSize.toPx() / 2).roundToInt(),
                        )
                    }
                    .size(thumbSize)
                    .clip(CircleShape)
                    .background(VicuTheme.colors.primary)
            )
        }
    }
}

/** trim 双端滑杆：拖任一端调整区间（最小 500ms），松手才回调。 */
@Composable
private fun TrimRangeSlider(
    durationMs: Long,
    trim: EffectSpec.Trim?,
    onTrimChange: (EffectSpec.Trim?) -> Unit,
) {
    var startFraction by remember(trim) {
        mutableFloatStateOf(trim?.let { it.startMs.toFloat() / durationMs } ?: 0f)
    }
    var endFraction by remember(trim) {
        mutableFloatStateOf(trim?.let { it.endMs.toFloat() / durationMs } ?: 1f)
    }
    var dragging by remember { mutableIntStateOf(0) } // -1 = 起点端，1 = 终点端，0 = 未拖拽
    var trackWidthPx by remember { mutableFloatStateOf(0f) }
    val minGap = (500f / durationMs).coerceIn(0.01f, 0.5f)
    val thumbSize = VicuTheme.dimensions.playerProgressThumbSize

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(VicuTheme.dimensions.playerProgressTouchHeight)
            .onSizeChanged { trackWidthPx = it.width.toFloat() }
            .pointerInput(durationMs) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val hitRadius = 24.dp.toPx()
                        dragging = when {
                            abs(offset.x - startFraction * trackWidthPx) < hitRadius -> -1
                            abs(offset.x - endFraction * trackWidthPx) < hitRadius -> 1
                            else -> 0
                        }
                    },
                    onDrag = { change, _ ->
                        if (dragging == 0) return@detectDragGestures
                        change.consume()
                        val fraction = (change.position.x / trackWidthPx).coerceIn(0f, 1f)
                        if (dragging < 0) {
                            startFraction = fraction.coerceAtMost(endFraction - minGap)
                        } else {
                            endFraction = fraction.coerceAtLeast(startFraction + minGap)
                        }
                    },
                    onDragEnd = {
                        if (dragging != 0) {
                            onTrimChange(
                                EffectSpec.Trim(
                                    startMs = (startFraction * durationMs).toLong(),
                                    endMs = (endFraction * durationMs).toLong(),
                                )
                            )
                        }
                        dragging = 0
                    },
                    onDragCancel = { dragging = 0 },
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
        )
        // 区间高亮（primary）
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset { IntOffset((startFraction * trackWidthPx).roundToInt(), 0) }
                .size(
                    with(LocalDensity.current) { ((endFraction - startFraction) * trackWidthPx).toDp() },
                    VicuTheme.dimensions.playerProgressTrackHeight,
                )
                .clip(VicuTheme.shapes.full)
                .background(VicuTheme.colors.primary),
        )
        listOf(startFraction, endFraction).forEach { fraction ->
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset {
                        IntOffset(
                            (fraction * trackWidthPx).roundToInt() -
                                    (thumbSize.toPx() / 2).roundToInt(),
                            0,
                        )
                    }
                    .size(thumbSize)
                    .clip(CircleShape)
                    .background(VicuTheme.colors.primary)
            )
        }
    }
}

@Composable
private fun playButtonText(state: PlayerUiState): String = stringResource(
    when (state.phase) {
        PlayerPhase.Playing -> R.string.player_pause
        PlayerPhase.Paused -> R.string.player_play
        PlayerPhase.Ended -> R.string.player_replay
        else -> R.string.player_play
    }
)

private val PlayerError.messageRes: Int
    get() = when (this) {
        PlayerError.OpenFailed -> R.string.player_error_open
        PlayerError.PlaybackFailed -> R.string.player_error_playback
    }

/** 位置 / 总时长（超过 1 小时按 h:mm:ss，否则 m:ss）。 */
internal fun playerTimeText(positionMs: Long, durationMs: Long): String =
    "${formatClock(positionMs / 1000)} / ${formatClock(durationMs / 1000)}"

private fun formatClock(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = totalSeconds % 3600 / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(java.util.Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(java.util.Locale.US, "%d:%02d", minutes, seconds)
    }
}

@Preview(showBackground = true)
@Composable
private fun PlayerPagePlayingPreview() {
    VicuTheme {
        PlayerContent(
            state = PlayerUiState(
                videoName = "sample.mp4",
                playing = true,
                phase = PlayerPhase.Playing,
                videoWidth = 1920,
                videoHeight = 1080,
                durationMs = 65000,
                positionMs = 12000,
            ),
            onTogglePlayPause = {},
            onSeek = {},
            onCropChange = {},
            onTrimChange = {},
            onSurfaceAvailable = {},
            onBack = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PlayerPageEffectsPreview() {
    VicuTheme {
        PlayerContent(
            state = PlayerUiState(
                videoName = "sample.mp4",
                playing = false,
                phase = PlayerPhase.Paused,
                videoWidth = 1920,
                videoHeight = 1080,
                durationMs = 65000,
                positionMs = 30000,
                crop = EffectSpec.Crop(left = 160, top = 90, width = 1600, height = 900),
                trim = EffectSpec.Trim(startMs = 10000, endMs = 50000),
            ),
            onTogglePlayPause = {},
            onSeek = {},
            onCropChange = {},
            onTrimChange = {},
            onSurfaceAvailable = {},
            onBack = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PlayerPageFailedPreview() {
    VicuTheme {
        PlayerContent(
            state = PlayerUiState(
                videoName = "sample.mp4",
                phase = PlayerPhase.Failed(PlayerError.OpenFailed),
            ),
            onTogglePlayPause = {},
            onSeek = {},
            onCropChange = {},
            onTrimChange = {},
            onSurfaceAvailable = {},
            onBack = {},
        )
    }
}
