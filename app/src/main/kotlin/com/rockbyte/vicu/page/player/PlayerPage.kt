package com.rockbyte.vicu.page.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.Surface
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.rockbyte.vicu.R
import com.rockbyte.vicu.player.PlayerError
import com.rockbyte.vicu.repo.SelectedMedia
import com.rockbyte.vicu.page.player.screen.PlayerControlsOverlay
import com.rockbyte.vicu.ui.component.StatusRow
import com.rockbyte.vicu.ui.component.VideoSurface
import com.rockbyte.vicu.ui.theme.VicuTheme
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel

/**
 * 播放页：全屏播放所选视频——视频等比 letterbox 居中，控制层（返回/进度/播放暂停/时间）
 * 悬浮于画面上层，播放中 [VicuTheme.motion.controlsHideDelayMillis] 无操作自动隐藏，点按画面切换显隐；
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
        onScrubStart = viewModel::scrubStart,
        onScrub = viewModel::scrubTo,
        onScrubEnd = viewModel::scrubEnd,
        onSurfaceAvailable = viewModel::setSurface,
        onBack = onBack,
    )
}

@Composable
private fun PlayerContent(
    state: PlayerUiState,
    onTogglePlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onScrubStart: () -> Unit,
    onScrub: (Long) -> Unit,
    onScrubEnd: (Long) -> Unit,
    onSurfaceAvailable: (Surface?) -> Unit,
    onBack: () -> Unit,
) {
    var controlsVisible by remember { mutableStateOf(true) }
    var scrubbing by remember { mutableStateOf(false) }
    val controlsHideDelayMillis = VicuTheme.motion.controlsHideDelayMillis.toLong()

    PlayerWindowEffect()

    // 播放中显示控制层超过时限且未在拖拽进度条时自动隐藏；暂停/Ended/Preparing 常显
    LaunchedEffect(controlsVisible, state.playing, state.phase, scrubbing) {
        if (controlsVisible && state.playing && !scrubbing) {
            delay(controlsHideDelayMillis)
            controlsVisible = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(state.phase) {
                detectTapGestures {
                    // 隐藏时点按唤出；可见时仅在播放中隐藏（暂停/Ended 常显）
                    controlsVisible = if (controlsVisible) {
                        state.phase != PlayerPhase.Playing
                    } else {
                        true
                    }
                }
            },
    ) {
        LetterboxedVideo(
            state = state,
            onSurfaceAvailable = onSurfaceAvailable,
            modifier = Modifier.align(Alignment.Center),
        )
        when (val phase = state.phase) {
            is PlayerPhase.Failed -> Box(Modifier.align(Alignment.Center)) {
                StatusRow(
                    dotColor = VicuTheme.colors.error,
                    text = stringResource(
                        R.string.playback_failed, stringResource(phase.error.messageRes)
                    ),
                    textColor = VicuTheme.colors.error,
                )
            }
            PlayerPhase.Preparing -> Box(Modifier.align(Alignment.Center)) {
                StatusRow(
                    dotColor = VicuTheme.colors.secondary,
                    text = stringResource(R.string.preparing),
                    pulsing = true,
                    textColor = Color.White,
                )
            }
            else -> Unit
        }
        if (controlsVisible) {
            PlayerControlsOverlay(
                state = state,
                onTogglePlayPause = onTogglePlayPause,
                onSeek = onSeek,
                onScrubStart = {
                    scrubbing = true
                    onScrubStart()
                },
                onScrub = onScrub,
                onScrubEnd = {
                    scrubbing = false
                    onScrubEnd(it)
                },
                onBack = onBack,
            )
        }
    }
}

/** 视频等比缩放（letterbox）居中：SurfaceView 由引擎拉伸填满，故容器必须匹配视频宽高比。 */
@Composable
private fun LetterboxedVideo(
    state: PlayerUiState,
    onSurfaceAvailable: (Surface?) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        val videoAspect = videoAspectRatio(state)
        val boxAspect = maxWidth / maxHeight
        Box(
            modifier = if (videoAspect > boxAspect) {
                Modifier.fillMaxWidth().aspectRatio(videoAspect)
            } else {
                Modifier.fillMaxHeight().aspectRatio(videoAspect)
            }
        ) {
            VideoSurface(
                modifier = Modifier.matchParentSize(),
                onSurfaceAvailable = onSurfaceAvailable,
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

/**
 * 播放页窗口效果：状态栏常驻不隐藏（隐藏后退出时系统栏会重新播放出现动画），
 * 仅把状态栏图标切浅色以适配黑底播放器；播放期间保持屏幕常亮，离开页面恢复浅色主题的系统栏外观。
 * 宿主非 Activity（如 Preview）时不生效。
 */
@Composable
private fun PlayerWindowEffect() {
    val context = LocalContext.current
    DisposableEffect(context) {
        val window = context.findActivity()?.window
        val controller = window?.insetsController
        setLightSystemBars(controller, light = false)
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            setLightSystemBars(controller, light = true)
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

/** 系统栏图标外观：light = 深色图标（浅色主题默认），否则浅色图标（黑底播放器用）。 */
private fun setLightSystemBars(controller: WindowInsetsController?, light: Boolean) {
    val mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
        WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
    controller?.setSystemBarsAppearance(if (light) mask else 0, mask)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private val PlayerError.messageRes: Int
    get() = when (this) {
        PlayerError.OpenFailed -> R.string.cannot_open_video
        PlayerError.PlaybackFailed -> R.string.playback_error
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
            onScrubStart = {},
            onScrub = {},
            onScrubEnd = {},
            onSurfaceAvailable = {},
            onBack = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PlayerPagePausedPreview() {
    VicuTheme {
        PlayerContent(
            state = PlayerUiState(
                videoName = "sample.mp4",
                playing = false,
                phase = PlayerPhase.Paused,
                videoWidth = 1080,
                videoHeight = 1920,
                durationMs = 65000,
                positionMs = 30000,
            ),
            onTogglePlayPause = {},
            onSeek = {},
            onScrubStart = {},
            onScrub = {},
            onScrubEnd = {},
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
            onScrubStart = {},
            onScrub = {},
            onScrubEnd = {},
            onSurfaceAvailable = {},
            onBack = {},
        )
    }
}
