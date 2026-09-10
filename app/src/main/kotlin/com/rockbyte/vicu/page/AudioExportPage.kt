package com.rockbyte.vicu.page

import android.net.Uri
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.style.MutableStyleState
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.contentPaddingHorizontal
import androidx.compose.foundation.style.styleable
import androidx.compose.foundation.style.then
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.rockbyte.vicu.nav.AudioExportRoute
import com.rockbyte.vicu.repo.AudioExportFormat
import com.rockbyte.vicu.repo.AudioExportQuality
import com.rockbyte.vicu.repo.SourceAudioInfo
import com.rockbyte.vicu.ui.component.VicuButton
import com.rockbyte.vicu.ui.theme.VicuSpacing
import com.rockbyte.vicu.ui.theme.VicuTheme
import org.koin.androidx.compose.koinViewModel

/**
 * 导出音频页：从功能列表带入视频，选择格式（原声/MP3/M4A）与质量（最佳/高/中/低）后导出；
 * 导出过程中锁定全部操作并拦截系统返回。
 */
@Composable
fun AudioExportPage(route: AudioExportRoute) {
    val viewModel = koinViewModel<AudioExportViewModel>()
    val videoUri = remember(route.uri) { Uri.parse(route.uri) }
    LaunchedEffect(videoUri, route.name) { viewModel.bind(videoUri, route.name) }
    val state by viewModel.uiState.collectAsState()

    AudioExportContent(
        state = state,
        onSelectFormat = viewModel::selectFormat,
        onSelectQuality = viewModel::selectQuality,
        onExport = viewModel::export,
    )
}

@Composable
private fun AudioExportContent(
    state: AudioExportUiState,
    onSelectFormat: (AudioExportFormat) -> Unit,
    onSelectQuality: (AudioExportQuality) -> Unit,
    onExport: () -> Unit,
) {
    val exporting = state.phase is ExportPhase.Exporting
    BackHandler(enabled = exporting) { /* 导出中锁定，拦截系统返回 */ }

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
                    .padding(vertical = VicuSpacing.gutter)
                    .verticalScroll(rememberScrollState()),
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

                    BasicText(
                        stringResource(R.string.selected_file, state.videoName),
                        style = VicuTheme.typography.bodySm
                    )

                    state.sourceInfo?.let { info ->
                        BasicText(
                            stringResource(R.string.source_audio_info, sourceInfoText(info)),
                            style = VicuTheme.typography.bodySm.copy(color = VicuTheme.colors.onSurfaceVariant),
                        )
                    }

                    OptionGroupLabel(stringResource(R.string.export_format_label))
                    OptionRow(
                        options = AudioExportFormat.entries,
                        selected = state.format,
                        enabled = !exporting,
                        label = { stringResource(it.labelRes) },
                        onSelect = onSelectFormat,
                    )

                    OptionGroupLabel(stringResource(R.string.export_quality_label))
                    OptionRow(
                        options = AudioExportQuality.entries,
                        selected = state.quality,
                        enabled = !exporting,
                        label = { stringResource(it.labelRes) },
                        onSelect = onSelectQuality,
                    )

                    VicuButton(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !exporting && state.videoName.isNotBlank(),
                        onClick = onExport,
                    ) {
                        BasicText(exportButtonText(state.phase))
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
private fun OptionGroupLabel(text: String) {
    BasicText(
        text,
        style = VicuTheme.typography.caption,
    )
}

/** 等宽选项行：选中项主按钮、未选项次按钮；压缩内边距以容纳 4 档质量。 */
@Composable
private fun <T> OptionRow(
    options: List<T>,
    selected: T,
    enabled: Boolean,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    val compactPadding = Style { contentPaddingHorizontal(8.dp) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(VicuSpacing.unit),
    ) {
        options.forEach { option ->
            VicuButton(
                modifier = Modifier.weight(1f),
                onClick = { onSelect(option) },
                enabled = enabled,
                style = if (option == selected) {
                    compactPadding
                } else {
                    VicuTheme.styles.secondaryButton then compactPadding
                },
            ) {
                BasicText(label(option))
            }
        }
    }
}

private val AudioExportFormat.labelRes: Int
    get() = when (this) {
        AudioExportFormat.ORIGINAL -> R.string.format_original
        AudioExportFormat.MP3 -> R.string.format_mp3
        AudioExportFormat.M4A -> R.string.format_m4a
    }

private val AudioExportQuality.labelRes: Int
    get() = when (this) {
        AudioExportQuality.BEST -> R.string.quality_best
        AudioExportQuality.HIGH -> R.string.quality_high
        AudioExportQuality.MEDIUM -> R.string.quality_medium
        AudioExportQuality.LOW -> R.string.quality_low
    }

@Composable
private fun exportButtonText(phase: ExportPhase): String = stringResource(
    when (phase) {
        is ExportPhase.Complete -> R.string.export_success
        is ExportPhase.Failed -> R.string.export_failed_retry
        ExportPhase.Exporting -> R.string.exporting
        else -> R.string.export_audio
    }
)

private fun sourceInfoText(info: SourceAudioInfo): String {
    val codec = info.codec?.uppercase() ?: "?"
    return if (info.bitrateKbps != null) "$codec · ${info.bitrateKbps} kb/s" else codec
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
                videoName = "sample_video.mp4",
                sourceInfo = SourceAudioInfo("aac", 128),
                phase = ExportPhase.Ready,
            ),
            onSelectFormat = {},
            onSelectQuality = {},
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
                videoName = "sample_video.mp4",
                phase = ExportPhase.Failed("FFmpeg 转码失败"),
            ),
            onSelectFormat = {},
            onSelectQuality = {},
            onExport = {},
        )
    }
}
