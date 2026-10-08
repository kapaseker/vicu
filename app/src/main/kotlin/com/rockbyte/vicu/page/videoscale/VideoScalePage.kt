package com.rockbyte.vicu.page.videoscale

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rockbyte.vicu.R
import com.rockbyte.vicu.page.messageRes
import com.rockbyte.vicu.page.player.PlayerPhase
import com.rockbyte.vicu.page.player.PlayerUiState
import com.rockbyte.vicu.page.player.PlayerViewModel
import com.rockbyte.vicu.player.PlayerEffect
import com.rockbyte.vicu.player.PlayerError
import com.rockbyte.vicu.repo.SelectedMedia
import com.rockbyte.vicu.ui.component.OutlineButton
import com.rockbyte.vicu.ui.component.PrimaryButton
import com.rockbyte.vicu.ui.component.ProgressButton
import com.rockbyte.vicu.ui.component.RatioChip
import com.rockbyte.vicu.ui.component.RatioChipRow
import com.rockbyte.vicu.ui.component.ScaleRatioPreset
import com.rockbyte.vicu.ui.component.StatusRow
import com.rockbyte.vicu.ui.component.VideoSeekBar
import com.rockbyte.vicu.ui.component.VideoSurface
import com.rockbyte.vicu.ui.component.VicuScaffold
import com.rockbyte.vicu.ui.component.nextRatioSelection
import com.rockbyte.vicu.ui.component.outputRatio
import com.rockbyte.vicu.ui.component.scaleRatioFlippedSaver
import com.rockbyte.vicu.ui.component.videoPreviewWidth
import com.rockbyte.vicu.ui.theme.VicuTheme
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel

/**
 * 缩放页：复用播放页预览（播放/暂停、进度条与 seek），下方提供比例与高度两行预设；
 * 「确认」按输出尺寸 `scale=W:H` 拉伸重编码导出（比例与源不一致时画面变形），
 * 导出中锁定全部操作并拦截返回。
 *
 * 预览实时套用与导出同一 `scale=W:H` 滤镜链：播放中逐帧生效，暂停/结束态由 PlayerViewModel 补一次
 * seek 刷新静止帧；输出尺寸同时以尺寸行文案确认。
 */
@Composable
fun VideoScalePage(media: SelectedMedia, onBack: () -> Unit, onGoHome: () -> Unit) {
    val playerViewModel = koinViewModel<PlayerViewModel>()
    val scaleViewModel = koinViewModel<VideoScaleViewModel>()
    LaunchedEffect(media) {
        playerViewModel.bind(media)
        scaleViewModel.bind(media)
    }
    DisposableEffect(Unit) {
        onDispose { playerViewModel.release() }
    }
    val playerState by playerViewModel.uiState.collectAsState()
    val scaleState by scaleViewModel.uiState.collectAsState()

    VideoScaleContent(
        playerState = playerState,
        scaleState = scaleState,
        onTogglePlayPause = playerViewModel::togglePlayPause,
        onSeek = playerViewModel::seekTo,
        onScrubStart = playerViewModel::scrubStart,
        onScrub = playerViewModel::scrubTo,
        onScrubEnd = playerViewModel::scrubEnd,
        onSurfaceAvailable = playerViewModel::setSurface,
        onScaleChange = playerViewModel::setScale,
        onScale = scaleViewModel::scale,
        onBack = onBack,
        onGoHome = onGoHome,
    )
}

@Composable
private fun VideoScaleContent(
    playerState: PlayerUiState,
    scaleState: VideoScaleUiState,
    onTogglePlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onScrubStart: () -> Unit,
    onScrub: (Long) -> Unit,
    onScrubEnd: (Long) -> Unit,
    onSurfaceAvailable: (android.view.Surface?) -> Unit,
    onScaleChange: (PlayerEffect.Scale?) -> Unit,
    onScale: (Int, Int) -> Unit,
    onBack: () -> Unit,
    onGoHome: () -> Unit,
) {
    val scaling = scaleState.scalePhase is ScalePhase.Scaling
    BackHandler(enabled = scaling) { /* 缩放中锁定，拦截系统返回 */ }

    val ready = playerState.videoWidth > 0 && playerState.videoHeight > 0
    val aspect = videoAspectRatio(playerState)
    var ratioPreset by rememberSaveable { mutableStateOf(ScaleRatioPreset.Original) }
    var flippedPresets by rememberSaveable(stateSaver = scaleRatioFlippedSaver) {
        mutableStateOf(emptySet<ScaleRatioPreset>())
    }
    var heightPreset by rememberSaveable { mutableStateOf(VideoScaleHeightPreset.Original) }
    var controlsToken by remember { mutableIntStateOf(0) }
    var controlsVisible by remember { mutableStateOf(true) }
    val hideDelayMillis = VicuTheme.motion.controlsHideDelayMillis
    // 执行操作后显示播放/暂停按钮，延迟自动消去（进度条与预设 chips 常驻）
    LaunchedEffect(controlsToken) {
        controlsVisible = true
        delay(hideDelayMillis.toLong())
        controlsVisible = false
    }
    // 输出尺寸 = 高度档位 × 所选比例（原图比例跟随源画面）；宽高均向下取偶。
    val output = if (ready) {
        scaleOutputSize(
            playerState.videoWidth, playerState.videoHeight,
            ratioPreset.outputRatio(ratioPreset in flippedPresets, aspect),
            heightPreset.height,
        )
    } else null
    // 预览实时套用与导出同串的 scale；输出尺寸等于源尺寸时清空滤镜链（无需重建）。
    val scaleEffect = if (ready && output != null &&
        (output.first != playerState.videoWidth || output.second != playerState.videoHeight)
    ) {
        PlayerEffect.Scale(output.first, output.second)
    } else {
        null
    }
    LaunchedEffect(scaleEffect) { onScaleChange(scaleEffect) }

    VicuScaffold(
        title = stringResource(R.string.video_scale),
        onBack = onBack,
        backEnabled = !scaling,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val availableWidth = maxWidth - VicuTheme.dimensions.screenGutter * 2
            val availableHeight = maxHeight
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(VicuTheme.dimensions.screenGutter),
                verticalArrangement = Arrangement.spacedBy(VicuTheme.dimensions.spacingUnit * 2),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(videoPreviewWidth(availableWidth, availableHeight, aspect))
                        .aspectRatio(aspect),
                ) {
                    VideoSurface(
                        modifier = Modifier.matchParentSize(),
                        onSurfaceAvailable = onSurfaceAvailable,
                    )
                    if (ready && !scaling) {
                        // 点画面切换播放/暂停并唤起中央按钮；3s 自动消去（与裁剪页一致）
                        Box(
                            modifier = Modifier.matchParentSize()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) {
                                    onTogglePlayPause()
                                    controlsToken++
                                },
                        )
                    }
                    if (ready && controlsVisible && !scaling) {
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
                    enabled = !scaling && playerState.durationMs > 0 && playerState.phase in seekablePhases,
                    onSeek = onSeek,
                    onScrubStart = onScrubStart,
                    onScrub = onScrub,
                    onScrubEnd = onScrubEnd,
                )
                if (output != null) {
                    BasicText(
                        text = stringResource(
                            R.string.video_scale_dimensions,
                            playerState.videoWidth, playerState.videoHeight, output.first, output.second,
                        ),
                        style = VicuTheme.typography.bodySm.copy(color = VicuTheme.colors.onSurfaceVariant),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                RatioChipRow(
                    selected = ratioPreset,
                    flipped = ratioPreset in flippedPresets,
                    enabled = ready && !scaling,
                    onSelect = { clicked ->
                        val (next, nextFlipped) = nextRatioSelection(ratioPreset, flippedPresets, clicked)
                        if (next != ratioPreset || nextFlipped != flippedPresets) {
                            ratioPreset = next
                            flippedPresets = nextFlipped
                        }
                    },
                )
                HeightChipRow(
                    selected = heightPreset,
                    sourceHeight = playerState.videoHeight.takeIf { ready },
                    enabled = ready && !scaling,
                    onSelect = { heightPreset = it },
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(VicuTheme.dimensions.spacingUnit),
                ) {
                    val phase = scaleState.scalePhase
                    if (phase is ScalePhase.Scaling) {
                        ProgressButton(
                            progress = phase.progress,
                            label = phase.progress?.let {
                                stringResource(R.string.progress_percent_format, it * 100)
                            } ?: stringResource(R.string.video_scale_scaling),
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        PrimaryButton(
                            text = stringResource(
                                when (phase) {
                                    ScalePhase.Complete -> R.string.video_scale_success
                                    is ScalePhase.Failed -> R.string.video_scale_failed_retry
                                    else -> R.string.confirm
                                }
                            ),
                            onClick = { output?.let { (w, h) -> onScale(w, h) } },
                            modifier = Modifier.weight(1f),
                            enabled = ready && phase != ScalePhase.Complete,
                        )
                    }
                    if (phase is ScalePhase.Complete) {
                        OutlineButton(
                            text = stringResource(R.string.back_to_home),
                            onClick = onGoHome,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                when (val playerPhase = playerState.phase) {
                    is PlayerPhase.Failed -> StatusRow(
                        dotColor = VicuTheme.colors.error,
                        text = stringResource(R.string.playback_failed, stringResource(playerPhase.error.messageRes)),
                        textColor = VicuTheme.colors.error,
                    )
                    PlayerPhase.Preparing -> StatusRow(
                        dotColor = VicuTheme.colors.secondary,
                        text = stringResource(R.string.preparing),
                        pulsing = true,
                    )
                    else -> Unit
                }
                when (val scalePhase = scaleState.scalePhase) {
                    is ScalePhase.Scaling -> StatusRow(
                        dotColor = VicuTheme.colors.secondary,
                        text = stringResource(R.string.video_scale_scaling),
                        pulsing = true,
                    )
                    ScalePhase.Complete -> StatusRow(
                        dotColor = VicuTheme.colors.onSurfaceVariant,
                        text = stringResource(R.string.saved_to_movies_folder),
                    )
                    is ScalePhase.Failed -> StatusRow(
                        dotColor = VicuTheme.colors.error,
                        text = stringResource(R.string.video_scale_failed, stringResource(scalePhase.error.messageRes)),
                        textColor = VicuTheme.colors.error,
                    )
                    ScalePhase.Idle -> Unit
                }
            }
        }
    }
}

/** 高度档位：null 表示保持源高度。声明序即展示序。 */
internal enum class VideoScaleHeightPreset(@StringRes val labelRes: Int, val height: Int?) {
    Original(R.string.video_scale_height_original, null),
    P1080(R.string.video_scale_height_1080p, 1080),
    P720(R.string.video_scale_height_720p, 720),
    P480(R.string.video_scale_height_480p, 480),
    P360(R.string.video_scale_height_360p, 360),
}

/** 高度档位 chip 行：禁止放大，仅显示不超过源高度的档位（未就绪时仅「原始高度」）。 */
@Composable
private fun HeightChipRow(
    selected: VideoScaleHeightPreset,
    sourceHeight: Int?,
    enabled: Boolean,
    onSelect: (VideoScaleHeightPreset) -> Unit,
) {
    val entries = if (sourceHeight != null) {
        VideoScaleHeightPreset.entries.filter { it.height == null || it.height <= sourceHeight }
    } else {
        listOf(VideoScaleHeightPreset.Original)
    }
    FlowRow(
        modifier = Modifier.fillMaxWidth()
            .alpha(if (enabled) VicuTheme.alpha.full else VicuTheme.alpha.disabled),
        horizontalArrangement = Arrangement.spacedBy(VicuTheme.dimensions.spacingUnit),
        verticalArrangement = Arrangement.spacedBy(VicuTheme.dimensions.spacingUnit),
    ) {
        entries.forEach { preset ->
            RatioChip(
                label = stringResource(preset.labelRes),
                selected = preset == selected,
                enabled = enabled,
                onClick = { onSelect(preset) },
            )
        }
    }
}

/** 预览就绪前按 16:9 占位，就绪后切换真实宽高比（与裁剪页一致）。 */
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
                if (playing) R.string.pause else R.string.play,
            ),
            modifier = Modifier.size(VicuTheme.dimensions.iconMedium),
            colorFilter = ColorFilter.tint(VicuTheme.colors.onSurface),
        )
    }
}

private val seekablePhases = setOf(PlayerPhase.Playing, PlayerPhase.Paused, PlayerPhase.Ended)

private val PlayerError.messageRes: Int
    get() = when (this) {
        PlayerError.OpenFailed -> R.string.cannot_open_video
        PlayerError.PlaybackFailed -> R.string.playback_error
    }

@Preview(showBackground = true)
@Composable
private fun VideoScalePageReadyPreview() {
    VicuTheme {
        VideoScaleContent(
            playerState = PlayerUiState(
                videoName = "sample.mp4",
                playing = true,
                phase = PlayerPhase.Playing,
                videoWidth = 1920,
                videoHeight = 1080,
                durationMs = 65000,
                positionMs = 12000,
            ),
            scaleState = VideoScaleUiState(),
            onTogglePlayPause = {},
            onSeek = {},
            onScrubStart = {},
            onScrub = {},
            onScrubEnd = {},
            onSurfaceAvailable = {},
            onScaleChange = {},
            onScale = { _, _ -> },
            onBack = {},
            onGoHome = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun VideoScalePageScalingPreview() {
    VicuTheme {
        VideoScaleContent(
            playerState = PlayerUiState(
                videoName = "sample.mp4",
                phase = PlayerPhase.Paused,
                videoWidth = 1920,
                videoHeight = 1080,
                durationMs = 65000,
                positionMs = 30000,
            ),
            scaleState = VideoScaleUiState(scalePhase = ScalePhase.Scaling(progress = 0.5f)),
            onTogglePlayPause = {},
            onSeek = {},
            onScrubStart = {},
            onScrub = {},
            onScrubEnd = {},
            onSurfaceAvailable = {},
            onScaleChange = {},
            onScale = { _, _ -> },
            onBack = {},
            onGoHome = {},
        )
    }
}
