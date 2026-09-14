package com.rockbyte.vicu.page

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.style.MutableStyleState
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.styleable
import androidx.compose.foundation.style.then
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import com.rockbyte.vicu.R
import com.rockbyte.vicu.repo.SelectedMedia
import com.rockbyte.vicu.repo.VideoConvertError
import com.rockbyte.vicu.repo.VideoConvertFormat
import com.rockbyte.vicu.repo.VideoConvertQuality
import com.rockbyte.vicu.ui.component.ProgressButton
import com.rockbyte.vicu.ui.component.VicuButton
import com.rockbyte.vicu.ui.component.VicuScaffold
import com.rockbyte.vicu.ui.theme.VicuTheme
import org.koin.androidx.compose.koinViewModel

/**
 * 视频转换页：从功能列表带入视频，选择格式（MP4/MKV/WebM/AVI/MOV）与质量（最佳/高/中/低）后转码；
 * 转换过程中锁定全部操作并拦截系统返回。
 */
@Composable
fun VideoConvertPage(media: SelectedMedia, onBack: () -> Unit, onGoHome: () -> Unit) {
    val viewModel = koinViewModel<VideoConvertViewModel>()
    LaunchedEffect(media) { viewModel.bind(media) }
    val state by viewModel.uiState.collectAsState()

    VideoConvertContent(
        state = state,
        onSelectFormat = viewModel::selectFormat,
        onSelectQuality = viewModel::selectQuality,
        onConvert = viewModel::convert,
        onBack = onBack,
        onGoHome = onGoHome,
    )
}

@Composable
private fun VideoConvertContent(
    state: VideoConvertUiState,
    onSelectFormat: (VideoConvertFormat) -> Unit,
    onSelectQuality: (VideoConvertQuality) -> Unit,
    onConvert: () -> Unit,
    onBack: () -> Unit,
    onGoHome: () -> Unit,
) {
    val converting = state.phase is ConvertPhase.Converting
    BackHandler(enabled = converting) { /* 转换中锁定，拦截系统返回 */ }

    VicuScaffold(
        title = stringResource(R.string.video_convert),
        onBack = onBack,
        backEnabled = !converting,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(VicuTheme.dimensions.screenGutter),
            verticalArrangement = Arrangement.spacedBy(VicuTheme.dimensions.spacingUnit * 2),
        ) {
            item {
                RadioOptionGroup(
                    label = stringResource(R.string.export_format_label),
                    options = VideoConvertFormat.entries,
                    selected = state.format,
                    enabled = !converting,
                    optionLabel = { stringResource(it.labelRes) },
                    onSelect = onSelectFormat,
                )
            }
            item {
                RadioOptionGroup(
                    label = stringResource(R.string.export_quality_label),
                    options = VideoConvertQuality.entries,
                    selected = state.quality,
                    enabled = !converting,
                    optionLabel = { stringResource(it.labelRes) },
                    onSelect = onSelectQuality,
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(VicuTheme.dimensions.spacingUnit)) {
                    val phase = state.phase
                    if (phase is ConvertPhase.Converting) {
                        ProgressButton(
                            progress = phase.progress,
                            label = phase.progress?.let { stringResource(R.string.progress_percent_format, it * 100) }
                                ?: stringResource(R.string.converting),
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        VicuButton(
                            modifier = Modifier.weight(1f),
                            enabled = state.videoName.isNotBlank(),
                            onClick = onConvert,
                        ) {
                            Image(
                                painter = painterResource(R.drawable.ic_transfer),
                                contentDescription = null,
                                modifier = Modifier.size(VicuTheme.dimensions.iconMedium),
                                colorFilter = ColorFilter.tint(VicuTheme.colors.onPrimary),
                            )
                            BasicText(
                                text = convertButtonText(state.phase),
                                modifier = Modifier.padding(start = VicuTheme.dimensions.spacingUnit),
                            )
                        }
                    }
                    if (state.phase is ConvertPhase.Complete) {
                        VicuButton(
                            modifier = Modifier.weight(1f),
                            style = VicuTheme.styles.outlineButton,
                            rippleColor = VicuTheme.colors.onSurface,
                            onClick = onGoHome,
                        ) {
                            BasicText(text = stringResource(R.string.back_to_home))
                        }
                    }
                }
            }

            when (val phase = state.phase) {
                is ConvertPhase.Converting -> item {
                    StatusRow(
                        dotColor = VicuTheme.colors.secondary,
                        text = stringResource(R.string.converting),
                        pulsing = true,
                    )
                }
                ConvertPhase.Complete -> item {
                    StatusRow(
                        dotColor = VicuTheme.colors.onSurfaceVariant,
                        text = stringResource(R.string.convert_complete),
                    )
                }
                is ConvertPhase.Failed -> item {
                    StatusRow(
                        dotColor = VicuTheme.colors.error,
                        text = stringResource(
                            R.string.convert_failed,
                            stringResource(phase.error.messageRes),
                        ),
                        textColor = VicuTheme.colors.error,
                    )
                }
                else -> Unit
            }
        }
    }
}

@Composable
private fun <T> RadioOptionGroup(
    label: String,
    options: List<T>,
    selected: T,
    enabled: Boolean,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    val cardState = remember { MutableStyleState(null) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .styleable(cardState, VicuTheme.styles.card),
        verticalArrangement = Arrangement.spacedBy(VicuTheme.dimensions.spacingUnit),
    ) {
        BasicText(label, style = VicuTheme.typography.caption)
        Column(
            modifier = Modifier.selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(VicuTheme.dimensions.spacingUnit / 2),
        ) {
            options.forEach { option ->
                RadioOption(
                    label = optionLabel(option),
                    selected = option == selected,
                    enabled = enabled,
                    onClick = { onSelect(option) },
                )
            }
        }
    }
}

@Composable
private fun RadioOption(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val indicatorColor = if (selected) VicuTheme.colors.primary else VicuTheme.colors.outline
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(VicuTheme.shapes.base)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                interactionSource = interactionSource,
                onClick = onClick,
            )
            .padding(
                horizontal = VicuTheme.dimensions.spacingUnit,
                vertical = VicuTheme.dimensions.radioItemVerticalPadding,
            )
            .alpha(if (enabled) VicuTheme.alpha.full else VicuTheme.alpha.disabled),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VicuTheme.dimensions.spacingUnit),
    ) {
        Box(
            modifier = Modifier
                .size(VicuTheme.dimensions.radioOuterSize)
                .border(VicuTheme.dimensions.radioBorderWidth, indicatorColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(
                    Modifier
                        .size(VicuTheme.dimensions.radioInnerSize)
                        .clip(CircleShape)
                        .background(VicuTheme.colors.primary)
                )
            }
        }
        BasicText(label, style = VicuTheme.typography.bodyLg)
    }
}

private val VideoConvertFormat.labelRes: Int
    get() = when (this) {
        VideoConvertFormat.MP4 -> R.string.format_mp4
        VideoConvertFormat.MKV -> R.string.format_mkv
        VideoConvertFormat.WEBM -> R.string.format_webm
        VideoConvertFormat.AVI -> R.string.format_avi
        VideoConvertFormat.MOV -> R.string.format_mov
    }

private val VideoConvertQuality.labelRes: Int
    get() = when (this) {
        VideoConvertQuality.BEST_QUALITY -> R.string.quality_finest
        VideoConvertQuality.BALANCED -> R.string.quality_balanced
        VideoConvertQuality.SMALLEST -> R.string.quality_smallest
        VideoConvertQuality.SUITABLE -> R.string.quality_suitable
    }

internal val VideoConvertError.messageRes: Int
    get() = when (this) {
        VideoConvertError.TranscodeFailed -> R.string.convert_error_transcode
        VideoConvertError.OutputCreationFailed -> R.string.convert_error_output_creation
        VideoConvertError.Unknown -> R.string.convert_error_unknown
    }

@Composable
private fun convertButtonText(phase: ConvertPhase): String = stringResource(
    when (phase) {
        is ConvertPhase.Complete -> R.string.convert_success
        is ConvertPhase.Failed -> R.string.convert_failed_retry
        is ConvertPhase.Converting -> R.string.converting
        else -> R.string.video_convert
    }
)

@Composable
private fun StatusRow(
    dotColor: Color,
    text: String,
    pulsing: Boolean = false,
    textColor: Color = VicuTheme.colors.onBackground,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VicuTheme.dimensions.spacingUnit),
    ) {
        val dotAlpha = if (pulsing) {
            val transition = rememberInfiniteTransition(label = "statusPulse")
            transition.animateFloat(
                initialValue = VicuTheme.alpha.statusPulseMinimum,
                targetValue = VicuTheme.alpha.full,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = VicuTheme.motion.statusPulseDurationMillis,
                    ),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dotAlpha",
            ).value
        } else {
            VicuTheme.alpha.full
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

@Preview(showBackground = true)
@Composable
private fun VideoConvertPageReadyPreview() {
    VicuTheme {
        VideoConvertContent(
            state = VideoConvertUiState(
                videoName = "sample_video.mp4",
                phase = ConvertPhase.Ready,
            ),
            onSelectFormat = {},
            onSelectQuality = {},
            onConvert = {},
            onBack = {},
            onGoHome = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun VideoConvertPageFailedPreview() {
    VicuTheme {
        VideoConvertContent(
            state = VideoConvertUiState(
                videoName = "sample_video.mp4",
                phase = ConvertPhase.Failed(VideoConvertError.TranscodeFailed),
            ),
            onSelectFormat = {},
            onSelectQuality = {},
            onConvert = {},
            onBack = {},
            onGoHome = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun VideoConvertPageConvertingPreview() {
    VicuTheme {
        VideoConvertContent(
            state = VideoConvertUiState(
                videoName = "sample_video.mp4",
                phase = ConvertPhase.Converting(progress = 0.50f),
            ),
            onSelectFormat = {},
            onSelectQuality = {},
            onConvert = {},
            onBack = {},
            onGoHome = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun VideoConvertPageCompletePreview() {
    VicuTheme {
        VideoConvertContent(
            state = VideoConvertUiState(
                videoName = "sample_video.mp4",
                phase = ConvertPhase.Complete,
            ),
            onSelectFormat = {},
            onSelectQuality = {},
            onConvert = {},
            onBack = {},
            onGoHome = {},
        )
    }
}
