@file:OptIn(ExperimentalLayoutApi::class)

package com.rockbyte.vicu.page.player.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.rockbyte.vicu.R
import com.rockbyte.vicu.page.player.PlayerPhase
import com.rockbyte.vicu.page.player.PlayerUiState
import com.rockbyte.vicu.page.player.playerTimeText
import com.rockbyte.vicu.ui.component.BackButton
import com.rockbyte.vicu.ui.component.VideoSeekBar
import com.rockbyte.vicu.ui.theme.VicuTheme
import com.rockbyte.vicu.ui.theme.vicuRipple

/**
 * 全屏播放悬浮控制层：左上角返回、底部 scrim + seekbar + 播放/暂停与时间。
 * 显隐由页面控制；seekbar/按钮自行消费点击，不透传画面点按手势。
 */
@Composable
internal fun PlayerControlsOverlay(
    state: PlayerUiState,
    onTogglePlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onScrubStart: () -> Unit,
    onScrub: (Long) -> Unit,
    onScrubEnd: (Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        // 顶部返回：对齐标准标题栏几何（64dp 行高 + 4dp 行边距），白色 tint。
        // 用 statusBarsIgnoringVisibility 保留状态栏真实高度：即使播放页隐藏了状态栏，
        // 返回按钮的屏幕位置也与其他页面（状态栏可见时）完全一致，进出页面不产生视觉跳动。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
                .height(VicuTheme.dimensions.topAppBarHeight)
                .padding(horizontal = VicuTheme.dimensions.topAppBarHorizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackButton(onClick = onBack, tint = Color.White)
        }
        BottomControls(
            state = state,
            onTogglePlayPause = onTogglePlayPause,
            onSeek = onSeek,
            onScrubStart = onScrubStart,
            onScrub = onScrub,
            onScrubEnd = onScrubEnd,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/** 底部控制条：渐变 scrim + 满宽 seekbar + 播放/暂停图标按钮与时间文本。 */
@Composable
private fun BottomControls(
    state: PlayerUiState,
    onTogglePlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onScrubStart: () -> Unit,
    onScrub: (Long) -> Unit,
    onScrubEnd: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val unit = VicuTheme.dimensions.spacingUnit
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)),
                )
            )
            .padding(horizontal = unit, vertical = unit),
        verticalArrangement = Arrangement.spacedBy(unit),
    ) {
        VideoSeekBar(
            positionMs = state.positionMs,
            durationMs = state.durationMs,
            enabled = state.durationMs > 0 && state.phase in seekablePhases,
            onSeek = onSeek,
            onScrubStart = onScrubStart,
            onScrub = onScrub,
            onScrubEnd = onScrubEnd,
            trackColor = Color.White.copy(alpha = 0.3f),
            fillColor = Color.White,
            thumbColor = Color.White,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(unit),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlayPauseButton(
                playing = state.playing,
                enabled = state.phase != PlayerPhase.Preparing,
                onClick = onTogglePlayPause,
            )
            BasicText(
                text = playerTimeText(state.positionMs, state.durationMs),
                style = VicuTheme.typography.bodySm.copy(color = Color.White),
            )
        }
    }
}

private val seekablePhases = setOf(PlayerPhase.Playing, PlayerPhase.Paused, PlayerPhase.Ended)

/** 圆形半透明底播放/暂停图标按钮。 */
@Composable
private fun PlayPauseButton(
    playing: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(VicuTheme.dimensions.navigationTouchSize)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.15f))
            .clickable(
                interactionSource = interactionSource,
                enabled = enabled,
                indication = vicuRipple(Color.White),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play),
            contentDescription = stringResource(
                if (playing) R.string.player_pause else R.string.player_play
            ),
            modifier = Modifier.size(VicuTheme.dimensions.iconMedium),
            colorFilter = ColorFilter.tint(Color.White),
        )
    }
}

