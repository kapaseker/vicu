package com.rockbyte.vicu.page

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.style.MutableStyleState
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.styleable
import androidx.compose.foundation.style.then
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rockbyte.vicu.R
import com.rockbyte.vicu.ui.component.VicuButton
import com.rockbyte.vicu.ui.theme.VicuSpacing
import com.rockbyte.vicu.ui.theme.VicuTheme
import org.koin.androidx.compose.koinViewModel

/**
 * 视频转 MP3 导出页：选择视频，提取音频流并导出为 192 kb/s MP3。
 * 暂无导航入口（用户决策），经 nav/MainApp 的 AudioExportRoute 注册，接线后即达。
 */
@Composable
fun AudioExportPage() {
    val viewModel = koinViewModel<AudioExportViewModel>()
    val uiState by viewModel.uiState.collectAsState()
    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> uri?.let(viewModel::selectVideo) }
    )

    AudioExportContent(
        state = uiState,
        onChooseVideo = { videoPicker.launch(arrayOf("video/*")) },
        onExport = viewModel::exportSelectedVideo,
    )
}

@Composable
private fun AudioExportContent(
    state: AudioExportUiState,
    onChooseVideo: () -> Unit,
    onExport: () -> Unit,
) {
    val exporting = state.phase is ExportPhase.Exporting
    val canExport = state.selectedFileName != null && !exporting

    val screenState = remember { MutableStyleState(null) }
    Box(
        Modifier
            .fillMaxSize()
            .styleable(screenState, VicuTheme.styles.screen)
    ) {
        OrbBackground()
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            val headerState = remember { MutableStyleState(null) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .styleable(headerState, VicuTheme.styles.header),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(stringResource(R.string.app_title), style = VicuTheme.typography.h3)
            }
            Column(
                modifier = Modifier
                    .padding(horizontal = VicuSpacing.gutter)
                    .padding(vertical = VicuSpacing.gutter),
                verticalArrangement = Arrangement.spacedBy(VicuSpacing.unit * 2),
            ) {
                val cardState = remember { MutableStyleState(null) }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .styleable(cardState, VicuTheme.styles.card),
                    verticalArrangement = Arrangement.spacedBy(VicuSpacing.unit * 2),
                ) {
                    BasicText(
                        stringResource(R.string.screen_description),
                        style = VicuTheme.typography.bodyLg
                    )

                    VicuButton(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !exporting,
                        onClick = onChooseVideo,
                    ) {
                        BasicText(stringResource(R.string.choose_video))
                    }

                    state.selectedFileName?.let {
                        BasicText(
                            stringResource(R.string.selected_file, it),
                            style = VicuTheme.typography.bodySm
                        )
                    }

                    VicuButton(
                        modifier = Modifier.fillMaxWidth(),
                        style = VicuTheme.styles.secondaryButton,
                        enabled = canExport,
                        onClick = onExport,
                    ) {
                        BasicText(stringResource(R.string.export_mp3))
                    }

                    when (val phase = state.phase) {
                        ExportPhase.Exporting -> StatusRow(
                            dotColor = VicuTheme.colors.secondary,
                            text = stringResource(R.string.exporting),
                            pulsing = true,
                        )
                        is ExportPhase.Complete -> StatusRow(
                            dotColor = VicuTheme.colors.onSurfaceVariant,
                            text = stringResource(R.string.export_complete),
                        )
                        is ExportPhase.Failed -> StatusRow(
                            dotColor = VicuTheme.colors.error,
                            text = stringResource(R.string.export_failed, phase.reason),
                            textColor = VicuTheme.colors.error,
                        )
                        else -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusRow(
    dotColor: Color,
    text: String,
    pulsing: Boolean = false,
    textColor: Color = VicuTheme.colors.onBackground,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VicuSpacing.unit),
    ) {
        val dotAlpha = if (pulsing) {
            val transition = rememberInfiniteTransition(label = "statusPulse")
            transition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 600),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dotAlpha",
            ).value
        } else {
            1f
        }
        val dotState = remember { MutableStyleState(null) }
        Box(
            Modifier
                .alpha(dotAlpha)
                .styleable(dotState, VicuTheme.styles.statusDot then Style { background(dotColor) })
        )
        BasicText(text, style = VicuTheme.typography.bodySm.copy(color = textColor))
    }
}

@Composable
private fun OrbBackground() {
    // ponytail: DESIGN.md 的模糊渐变 orb 以纯 radialGradient 近似——Modifier.blur 需 API 31+
    // （minSdk 29）；升级路径：按 SDK_INT 分级加 blur 或改 RenderEffect。
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .size(360.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            VicuTheme.colors.surfaceTint.copy(alpha = 0.10f),
                            Color.Transparent,
                        ),
                    )
                )
        )
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .size(240.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            VicuTheme.colors.outlineVariant.copy(alpha = 0.15f),
                            Color.Transparent,
                        ),
                    )
                )
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AudioExportPageReadyPreview() {
    VicuTheme {
        AudioExportContent(
            state = AudioExportUiState(
                selectedFileName = "sample_video.mp4",
                phase = ExportPhase.Ready,
            ),
            onChooseVideo = {},
            onExport = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AudioExportPageFailedPreview() {
    VicuTheme {
        AudioExportContent(
            state = AudioExportUiState(
                selectedFileName = "sample_video.mp4",
                phase = ExportPhase.Failed("FFmpeg 转码失败"),
            ),
            onChooseVideo = {},
            onExport = {},
        )
    }
}
