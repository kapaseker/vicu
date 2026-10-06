package com.rockbyte.vicu.page.crop

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
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
import com.rockbyte.vicu.ui.component.CropMarquee
import com.rockbyte.vicu.ui.component.CropRectF
import com.rockbyte.vicu.ui.component.OutlineButton
import com.rockbyte.vicu.ui.component.PrimaryButton
import com.rockbyte.vicu.ui.component.ProgressButton
import com.rockbyte.vicu.ui.component.StatusRow
import com.rockbyte.vicu.ui.component.VideoSeekBar
import com.rockbyte.vicu.ui.component.VideoSurface
import com.rockbyte.vicu.ui.component.videoPreviewWidth
import com.rockbyte.vicu.ui.component.VicuScaffold
import com.rockbyte.vicu.ui.theme.VicuTheme
import org.koin.androidx.compose.koinViewModel

/**
 * 裁剪页：复用播放页预览（播放/暂停、进度条与 seek），叠加虚线剪切框（框外 30% 黑遮罩、
 * 四条边中点圆点可拖拽改大小）；「确认」按框选区域重编码导出，导出中锁定全部操作并拦截返回。
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
        title = stringResource(R.string.crop),
        onBack = onBack,
        backEnabled = !cutting,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val availableWidth = maxWidth - VicuTheme.dimensions.screenGutter * 2
            val availableHeight = maxHeight
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(VicuTheme.dimensions.screenGutter),
                verticalArrangement = Arrangement.spacedBy(VicuTheme.dimensions.spacingUnit * 2),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(videoPreviewWidth(availableWidth, availableHeight, videoAspectRatio(playerState)))
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
                        PrimaryButton(
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
                        text = stringResource(R.string.playback_failed, stringResource(phase.error.messageRes)),
                        textColor = VicuTheme.colors.error,
                    )
                    PlayerPhase.Preparing -> StatusRow(
                        dotColor = VicuTheme.colors.secondary,
                        text = stringResource(R.string.preparing),
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
                        text = stringResource(R.string.saved_to_movies_folder),
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
                if (playing) R.string.pause else R.string.play,
            ),
            modifier = Modifier.size(VicuTheme.dimensions.iconMedium),
            colorFilter = ColorFilter.tint(VicuTheme.colors.onSurface),
        )
    }
}

/** 视频像素裁剪仍单独按 YUV420 偶数化，图片不走此路径。 */
internal fun CropRectF.toCrop(videoWidth: Int, videoHeight: Int): PlayerEffect.Crop = PlayerEffect.Crop(
    left = (left * videoWidth).toInt(),
    top = (top * videoHeight).toInt(),
    width = ((right - left) * videoWidth).toInt(),
    height = ((bottom - top) * videoHeight).toInt(),
).normalized()

@Composable
private fun cutButtonText(phase: CutPhase): String = stringResource(
    when (phase) {
        is CutPhase.Complete -> R.string.crop_success
        is CutPhase.Failed -> R.string.crop_failed_retry
        is CutPhase.Cutting -> R.string.cropping
        CutPhase.Idle -> R.string.confirm
    }
)

private val seekablePhases = setOf(PlayerPhase.Playing, PlayerPhase.Paused, PlayerPhase.Ended)

private val PlayerError.messageRes: Int
    get() = when (this) {
        PlayerError.OpenFailed -> R.string.cannot_open_video
        PlayerError.PlaybackFailed -> R.string.playback_error
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
