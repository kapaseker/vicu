package com.rockbyte.vicu.page

import android.net.Uri
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.style.MutableStyleState
import androidx.compose.foundation.style.Style
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import com.rockbyte.vicu.R
import com.rockbyte.vicu.nav.AudioExportRoute
import com.rockbyte.vicu.repo.AudioExportError
import com.rockbyte.vicu.repo.AudioExportFormat
import com.rockbyte.vicu.repo.AudioExportQuality
import com.rockbyte.vicu.ui.component.VicuButton
import com.rockbyte.vicu.ui.component.VicuScaffold
import com.rockbyte.vicu.ui.theme.VicuTheme
import com.rockbyte.vicu.ui.theme.vicuRipple
import org.koin.androidx.compose.koinViewModel

/**
 * 导出音频页：从功能列表带入视频，选择格式（原声/MP3/M4A）与质量（最佳/高/中/低）后导出；
 * 导出过程中锁定全部操作并拦截系统返回。
 */
@Composable
fun AudioExportPage(route: AudioExportRoute, onBack: () -> Unit) {
    val viewModel = koinViewModel<AudioExportViewModel>()
    val videoUri = remember(route.uri) { Uri.parse(route.uri) }
    LaunchedEffect(videoUri, route.name) { viewModel.bind(videoUri, route.name) }
    val state by viewModel.uiState.collectAsState()

    AudioExportContent(
        state = state,
        onSelectFormat = viewModel::selectFormat,
        onSelectQuality = viewModel::selectQuality,
        onExport = viewModel::export,
        onBack = onBack,
    )
}

@Composable
private fun AudioExportContent(
    state: AudioExportUiState,
    onSelectFormat: (AudioExportFormat) -> Unit,
    onSelectQuality: (AudioExportQuality) -> Unit,
    onExport: () -> Unit,
    onBack: () -> Unit,
) {
    val exporting = state.phase is ExportPhase.Exporting
    BackHandler(enabled = exporting) { /* 导出中锁定，拦截系统返回 */ }

    VicuScaffold(
        title = stringResource(R.string.export_audio),
        onBack = onBack,
        backEnabled = !exporting,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(VicuTheme.dimensions.screenGutter)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(VicuTheme.dimensions.spacingUnit * 2),
        ) {
            RadioOptionGroup(
                label = stringResource(R.string.export_format_label),
                options = AudioExportFormat.entries,
                selected = state.format,
                enabled = !exporting,
                optionLabel = { stringResource(it.labelRes) },
                onSelect = onSelectFormat,
            )
            RadioOptionGroup(
                label = stringResource(R.string.export_quality_label),
                options = AudioExportQuality.entries,
                selected = state.quality,
                enabled = !exporting,
                optionLabel = { stringResource(it.labelRes) },
                onSelect = onSelectQuality,
            )
            VicuButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = !exporting && state.videoName.isNotBlank(),
                onClick = onExport,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_export),
                    contentDescription = null,
                    modifier = Modifier.size(VicuTheme.dimensions.iconMedium),
                    colorFilter = ColorFilter.tint(VicuTheme.colors.onPrimary),
                )
                BasicText(
                    text = exportButtonText(state.phase),
                    modifier = Modifier.padding(start = VicuTheme.dimensions.spacingUnit),
                )
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
                    text = stringResource(
                        R.string.export_failed,
                        stringResource(phase.error.messageRes),
                    ),
                    textColor = VicuTheme.colors.error,
                )
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
                indication = vicuRipple(),
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

internal val AudioExportError.messageRes: Int
    get() = when (this) {
        AudioExportError.TranscodeFailed -> R.string.export_error_transcode
        AudioExportError.OutputCreationFailed -> R.string.export_error_output_creation
        AudioExportError.Unknown -> R.string.export_error_unknown
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
private fun AudioExportPageReadyPreview() {
    VicuTheme {
        AudioExportContent(
            state = AudioExportUiState(
                videoName = "sample_video.mp4",
                phase = ExportPhase.Ready,
            ),
            onSelectFormat = {},
            onSelectQuality = {},
            onExport = {},
            onBack = {},
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
                phase = ExportPhase.Failed(AudioExportError.TranscodeFailed),
            ),
            onSelectFormat = {},
            onSelectQuality = {},
            onExport = {},
            onBack = {},
        )
    }
}
