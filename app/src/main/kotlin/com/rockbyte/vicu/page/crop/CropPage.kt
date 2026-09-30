package com.rockbyte.vicu.page.crop

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import kotlin.math.hypot
import kotlinx.coroutines.delay
import com.rockbyte.vicu.R
import com.rockbyte.vicu.page.messageRes
import com.rockbyte.vicu.page.player.PlayerPhase
import com.rockbyte.vicu.page.player.PlayerUiState
import com.rockbyte.vicu.page.player.PlayerViewModel
import com.rockbyte.vicu.player.PlayerEffect
import com.rockbyte.vicu.player.PlayerError
import com.rockbyte.vicu.player.normalized
import com.rockbyte.vicu.repo.SelectedMedia
import com.rockbyte.vicu.ui.component.OutlineButton
import com.rockbyte.vicu.ui.component.PrimaryIconButton
import com.rockbyte.vicu.ui.component.ProgressButton
import com.rockbyte.vicu.ui.component.StatusRow
import com.rockbyte.vicu.ui.component.VideoSeekBar
import com.rockbyte.vicu.ui.component.VideoSurface
import com.rockbyte.vicu.ui.component.VicuScaffold
import com.rockbyte.vicu.ui.theme.VicuTheme
import org.koin.androidx.compose.koinViewModel

/**
 * 裁剪页：复用播放页预览（播放/暂停、进度条与 seek），叠加虚线剪切框（框外 30% 黑遮罩、
 * 四条边中点圆点可拖拽改大小）；「剪切」按框选区域重编码导出，导出中锁定全部操作并拦截返回。
 *
 * 预览始终为完整原画（不套 crop 滤镜），框选区域仅作选区指示；导出时才应用同一 `crop=...` 表达式。
 */
@Composable
fun CropPage(media: SelectedMedia, onBack: () -> Unit, onGoHome: () -> Unit) {
    val playerViewModel = koinViewModel<PlayerViewModel>()
    val cropViewModel = koinViewModel<CropViewModel>()
    LaunchedEffect(media) {
        playerViewModel.bind(media)
        cropViewModel.bind(media)
    }
    DisposableEffect(Unit) {
        onDispose { playerViewModel.release() }
    }
    val playerState by playerViewModel.uiState.collectAsState()
    val cropState by cropViewModel.uiState.collectAsState()

    CropContent(
        playerState = playerState,
        cropState = cropState,
        onTogglePlayPause = playerViewModel::togglePlayPause,
        onSeek = playerViewModel::seekTo,
        onScrubStart = playerViewModel::scrubStart,
        onScrub = playerViewModel::scrubTo,
        onScrubEnd = playerViewModel::scrubEnd,
        onSurfaceAvailable = playerViewModel::setSurface,
        onCut = cropViewModel::cut,
        onBack = onBack,
        onGoHome = onGoHome,
    )
}

@Composable
private fun CropContent(
    playerState: PlayerUiState,
    cropState: CropUiState,
    onTogglePlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onScrubStart: () -> Unit,
    onScrub: (Long) -> Unit,
    onScrubEnd: (Long) -> Unit,
    onSurfaceAvailable: (android.view.Surface?) -> Unit,
    onCut: (PlayerEffect.Crop) -> Unit,
    onBack: () -> Unit,
    onGoHome: () -> Unit,
) {
    val cutting = cropState.cutPhase is CutPhase.Cutting
    BackHandler(enabled = cutting) { /* 剪切中锁定，拦截系统返回 */ }

    val ready = playerState.videoWidth > 0 && playerState.videoHeight > 0
    var cropRect by remember(playerState.videoWidth, playerState.videoHeight) {
        mutableStateOf(CropRectF(0f, 0f, 1f, 1f))
    }
    var controlsToken by remember { mutableIntStateOf(0) }
    var controlsVisible by remember { mutableStateOf(true) }
    val hideDelayMillis = VicuTheme.motion.controlsHideDelayMillis
    // 执行操作后显示播放/暂停按钮，延迟自动消去（进度条与剪切按钮常驻）
    LaunchedEffect(controlsToken) {
        controlsVisible = true
        delay(hideDelayMillis.toLong())
        controlsVisible = false
    }

    VicuScaffold(
        title = stringResource(R.string.video_crop),
        onBack = onBack,
        backEnabled = !cutting,
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
                    .aspectRatio(videoAspectRatio(playerState)),
            ) {
                VideoSurface(
                    modifier = Modifier.matchParentSize(),
                    onSurfaceAvailable = onSurfaceAvailable,
                )
                if (ready) {
                    CropMarquee(
                        modifier = Modifier.matchParentSize(),
                        rect = cropRect,
                        enabled = !cutting,
                        onRectChange = { cropRect = it },
                        onTap = {
                            onTogglePlayPause()
                            controlsToken++
                        },
                    )
                }
                if (ready && controlsVisible && !cutting) {
                    PlayPauseButton(
                        playing = playerState.playing,
                        onClick = {
                            onTogglePlayPause()
                            controlsToken++
                        },
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
            VideoSeekBar(
                positionMs = playerState.positionMs,
                durationMs = playerState.durationMs,
                enabled = !cutting && playerState.durationMs > 0 && playerState.phase in seekablePhases,
                onSeek = onSeek,
                onScrubStart = onScrubStart,
                onScrub = onScrub,
                onScrubEnd = onScrubEnd,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(VicuTheme.dimensions.spacingUnit),
            ) {
                val phase = cropState.cutPhase
                if (phase is CutPhase.Cutting) {
                    ProgressButton(
                        progress = phase.progress,
                        label = phase.progress?.let { stringResource(R.string.progress_percent_format, it * 100) }
                            ?: stringResource(R.string.cropping),
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    PrimaryIconButton(
                        icon = R.drawable.ic_cut,
                        text = cutButtonText(phase),
                        onClick = {
                            onCut(cropRect.toCrop(playerState.videoWidth, playerState.videoHeight))
                        },
                        modifier = Modifier.weight(1f),
                        enabled = ready,
                    )
                }
                if (phase is CutPhase.Complete) {
                    OutlineButton(
                        text = stringResource(R.string.back_to_home),
                        onClick = onGoHome,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            when (val phase = playerState.phase) {
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
            when (val phase = cropState.cutPhase) {
                is CutPhase.Cutting -> StatusRow(
                    dotColor = VicuTheme.colors.secondary,
                    text = stringResource(R.string.cropping),
                    pulsing = true,
                )
                CutPhase.Complete -> StatusRow(
                    dotColor = VicuTheme.colors.onSurfaceVariant,
                    text = stringResource(R.string.crop_complete),
                )
                is CutPhase.Failed -> StatusRow(
                    dotColor = VicuTheme.colors.error,
                    text = stringResource(R.string.crop_failed, stringResource(phase.error.messageRes)),
                    textColor = VicuTheme.colors.error,
                )
                CutPhase.Idle -> Unit
            }
        }
    }
}

/** 预览就绪前按 16:9 占位，就绪后切换真实宽高比（与播放页一致）。 */
private fun videoAspectRatio(state: PlayerUiState): Float =
    if (state.videoWidth > 0 && state.videoHeight > 0) {
        state.videoWidth.toFloat() / state.videoHeight
    } else {
        16f / 9f
    }

/** 画面中央的播放/暂停覆盖按钮：操作后显示，3s 自动消去。 */
@Composable
private fun PlayPauseButton(
    playing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(VicuTheme.dimensions.navigationTouchSize)
            .clip(CircleShape)
            .background(
                VicuTheme.colors.surfaceContainerLowest.copy(alpha = VicuTheme.alpha.glassOverlay),
            )
            .clickable(interactionSource = interactionSource, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play),
            contentDescription = stringResource(
                if (playing) R.string.player_pause else R.string.player_play,
            ),
            modifier = Modifier.size(VicuTheme.dimensions.iconMedium),
            colorFilter = ColorFilter.tint(VicuTheme.colors.onSurface),
        )
    }
}

/** 归一化裁剪框（0..1，相对视频画面）。 */
internal data class CropRectF(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    /** 换算为视频像素裁剪描述（经 YUV420 偶数化）。 */
    fun toCrop(videoWidth: Int, videoHeight: Int): PlayerEffect.Crop = PlayerEffect.Crop(
        left = (left * videoWidth).toInt(),
        top = (top * videoHeight).toInt(),
        width = ((right - left) * videoWidth).toInt(),
        height = ((bottom - top) * videoHeight).toInt(),
    ).normalized()
}

/** 剪切框的四条边。 */
internal enum class CropEdge { LEFT, TOP, RIGHT, BOTTOM }

/**
 * 把指定边拖到归一化坐标 [x]/[y]：每条边只改自身轴（注意拖拽方向），
 * 结果 clamp 在 [0,1] 且与对边保留至少 [minSize] 间距。
 */
internal fun CropRectF.dragEdge(edge: CropEdge, x: Float, y: Float, minSize: Float = 0.05f): CropRectF {
    fun clamp(value: Float, min: Float, max: Float) = value.coerceIn(min, max.coerceAtLeast(min))
    return when (edge) {
        CropEdge.LEFT -> copy(left = clamp(x, 0f, right - minSize))
        CropEdge.RIGHT -> copy(right = clamp(x, left + minSize, 1f))
        CropEdge.TOP -> copy(top = clamp(y, 0f, bottom - minSize))
        CropEdge.BOTTOM -> copy(bottom = clamp(y, top + minSize, 1f))
    }
}

/**
 * 命中检测：返回距 [position] 最近且在 [touchRadiusPx] 内的边（用于拖拽改大小），否则 null。
 * 归一化矩形 [rect] 乘以覆盖层像素 [size] 得到四条边中点。
 */
internal fun hitEdge(
    rect: CropRectF,
    size: IntSize,
    position: Offset,
    touchRadiusPx: Float,
): CropEdge? {
    if (size.width <= 0 || size.height <= 0) return null
    val w = size.width.toFloat()
    val h = size.height.toFloat()
    val midX = (rect.left + rect.right) / 2f * w
    val midY = (rect.top + rect.bottom) / 2f * h
    val centers = listOf(
        CropEdge.TOP to Offset(midX, rect.top * h),
        CropEdge.BOTTOM to Offset(midX, rect.bottom * h),
        CropEdge.LEFT to Offset(rect.left * w, midY),
        CropEdge.RIGHT to Offset(rect.right * w, midY),
    )
    var best: CropEdge? = null
    var bestDistance = touchRadiusPx
    centers.forEach { (edge, center) ->
        val distance = hypot(position.x - center.x, position.y - center.y)
        if (distance < bestDistance) {
            best = edge
            bestDistance = distance
        }
    }
    return best
}

/**
 * 剪切框覆盖层：虚线边框 + 框外 30% 黑遮罩 + 四条边中点拖拽圆点；单击画面切换播放状态。
 * 拖拽只改对应边（上/下改 y，左/右改 x），拖拽中持续回调 [onRectChange]。
 *
 * 点击与拖拽必须在同一个 pointerInput 内自行区分：若叠加 detectTapGestures 与 detectDragGestures
 * 两个检测器，前者会消费 down，后者永远收不到事件，圆点就拖不动了。
 */
@Composable
private fun CropMarquee(
    rect: CropRectF,
    enabled: Boolean,
    onRectChange: (CropRectF) -> Unit,
    onTap: () -> Unit,
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
    // 命中半径为指针事件内的 dp 换算，需显式取 Density（该处不在 DrawScope/PointerInputScope 内）
    val touchRadiusPx = with(density) { VicuTheme.dimensions.cropHandleTouchRadius.toPx() }
    // 手势块只在 key 变化时重启，内部一律读最新值，避免捕获过期的 rect/回调
    val latestRect by rememberUpdatedState(rect)
    val latestSize by rememberUpdatedState(overlaySize)
    val latestOnRectChange by rememberUpdatedState(onRectChange)
    val latestOnTap by rememberUpdatedState(onTap)

    Box(
        modifier = modifier
            .onSizeChanged { overlaySize = it }
            .pointerInput(enabled, density) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    // requireUnconsumed：上层播放/暂停按钮已消费的 down 不再重复处理，
                    // 否则按钮与覆盖层各切一次播放状态，视觉上等于没切换。
                    val down = awaitFirstDown(requireUnconsumed = true)
                    val edge = hitEdge(latestRect, latestSize, down.position, touchRadiusPx)
                    val slop = viewConfiguration.touchSlop
                    // 拖拽期间以本地 working 递推：手势块不会因 rect 变化重启，不能读重组后的 rect
                    var working = latestRect
                    var isTap = true
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            change.consume()
                            if (isTap && edge == null) latestOnTap()
                            break
                        }
                        if ((change.position - down.position).getDistance() > slop) isTap = false
                        val size = latestSize
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        if (edge != null && w > 0f && h > 0f) {
                            val x = (change.position.x / w).coerceIn(0f, 1f)
                            val y = (change.position.y / h).coerceIn(0f, 1f)
                            working = working.dragEdge(edge, x, y)
                            latestOnRectChange(working)
                        }
                        change.consume()
                    }
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
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
            // 四条边中点圆点（拖拽命中半径见 hitEdge）
            val radius = handleSize.toPx() / 2f
            listOf(
                Offset((left + right) / 2f, top),
                Offset((left + right) / 2f, bottom),
                Offset(left, (top + bottom) / 2f),
                Offset(right, (top + bottom) / 2f),
            ).forEach { center -> drawCircle(color = handleColor, radius = radius, center = center) }
        }
    }
}

@Composable
private fun cutButtonText(phase: CutPhase): String = stringResource(
    when (phase) {
        is CutPhase.Complete -> R.string.crop_success
        is CutPhase.Failed -> R.string.crop_failed_retry
        is CutPhase.Cutting -> R.string.cropping
        CutPhase.Idle -> R.string.crop_cut
    }
)

private val seekablePhases = setOf(PlayerPhase.Playing, PlayerPhase.Paused, PlayerPhase.Ended)

private val PlayerError.messageRes: Int
    get() = when (this) {
        PlayerError.OpenFailed -> R.string.player_error_open
        PlayerError.PlaybackFailed -> R.string.player_error_playback
    }

@Preview(showBackground = true)
@Composable
private fun CropPageReadyPreview() {
    VicuTheme {
        CropContent(
            playerState = PlayerUiState(
                videoName = "sample.mp4",
                playing = true,
                phase = PlayerPhase.Playing,
                videoWidth = 1920,
                videoHeight = 1080,
                durationMs = 65000,
                positionMs = 12000,
            ),
            cropState = CropUiState(videoName = "sample.mp4"),
            onTogglePlayPause = {},
            onSeek = {},
            onScrubStart = {},
            onScrub = {},
            onScrubEnd = {},
            onSurfaceAvailable = {},
            onCut = {},
            onBack = {},
            onGoHome = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CropPageCuttingPreview() {
    VicuTheme {
        CropContent(
            playerState = PlayerUiState(
                videoName = "sample.mp4",
                phase = PlayerPhase.Paused,
                videoWidth = 1920,
                videoHeight = 1080,
                durationMs = 65000,
                positionMs = 30000,
            ),
            cropState = CropUiState(
                videoName = "sample.mp4",
                cutPhase = CutPhase.Cutting(progress = 0.5f),
            ),
            onTogglePlayPause = {},
            onSeek = {},
            onScrubStart = {},
            onScrub = {},
            onScrubEnd = {},
            onSurfaceAvailable = {},
            onCut = {},
            onBack = {},
            onGoHome = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CropPageCompletePreview() {
    VicuTheme {
        CropContent(
            playerState = PlayerUiState(
                videoName = "sample.mp4",
                phase = PlayerPhase.Paused,
                videoWidth = 1920,
                videoHeight = 1080,
                durationMs = 65000,
                positionMs = 42000,
            ),
            cropState = CropUiState(
                videoName = "sample.mp4",
                cutPhase = CutPhase.Complete,
            ),
            onTogglePlayPause = {},
            onSeek = {},
            onScrubStart = {},
            onScrub = {},
            onScrubEnd = {},
            onSurfaceAvailable = {},
            onCut = {},
            onBack = {},
            onGoHome = {},
        )
    }
}
