package com.rockbyte.vicu.page

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.rockbyte.vicu.R
import com.rockbyte.vicu.repo.AudioExportError
import com.rockbyte.vicu.repo.AudioExportFormat
import com.rockbyte.vicu.repo.AudioExportQuality
import com.rockbyte.vicu.repo.SelectedMedia
import com.rockbyte.vicu.ui.component.OutlineButton
import com.rockbyte.vicu.ui.component.PrimaryIconButton
import com.rockbyte.vicu.ui.component.ProgressButton
import com.rockbyte.vicu.ui.component.RadioOptionGroup
import com.rockbyte.vicu.ui.component.StatusRow
import com.rockbyte.vicu.ui.component.VicuScaffold
import com.rockbyte.vicu.ui.theme.VicuTheme
import org.koin.androidx.compose.koinViewModel

/**
 * 导出音频页：从功能列表带入视频，选择格式（原声/MP3/M4A）与质量（最佳/高/中/低）后导出；
 * 导出过程中锁定全部操作并拦截系统返回。
 */
@Composable
fun AudioExportPage(media: SelectedMedia, onBack: () -> Unit, onGoHome: () -> Unit) {
    val viewModel = koinViewModel<AudioExportViewModel>()
    LaunchedEffect(media) { viewModel.bind(media) }
    val state by viewModel.uiState.collectAsState()

    AudioExportContent(
        state = state,
        onSelectFormat = viewModel::selectFormat,
        onSelectQuality = viewModel::selectQuality,
        onExport = viewModel::export,
        onBack = onBack,
        onGoHome = onGoHome,
    )
}

@Composable
private fun AudioExportContent(
    state: AudioExportUiState,
    onSelectFormat: (AudioExportFormat) -> Unit,
    onSelectQuality: (AudioExportQuality) -> Unit,
    onExport: () -> Unit,
    onBack: () -> Unit,
    onGoHome: () -> Unit,
) {
    val exporting = state.phase is ExportPhase.Exporting
    BackHandler(enabled = exporting) { /* 导出中锁定，拦截系统返回 */ }

    VicuScaffold(
        title = stringResource(R.string.export_audio),
        onBack = onBack,
        backEnabled = !exporting,
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
                    options = AudioExportFormat.entries,
                    selected = state.format,
                    enabled = !exporting,
                    optionLabel = { stringResource(it.labelRes) },
                    onSelect = onSelectFormat,
                )
            }
            item {
                RadioOptionGroup(
                    label = stringResource(R.string.export_quality_label),
                    options = AudioExportQuality.entries,
                    selected = state.quality,
                    enabled = !exporting,
                    optionLabel = { stringResource(it.labelRes) },
                    onSelect = onSelectQuality,
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(VicuTheme.dimensions.spacingUnit)) {
                    val phase = state.phase
                    if (phase is ExportPhase.Exporting) {
                        ProgressButton(
                            progress = phase.progress,
                            label = phase.progress?.let { stringResource(R.string.progress_percent_format, it * 100) }
                                ?: stringResource(R.string.exporting),
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        PrimaryIconButton(
                            icon = R.drawable.ic_export,
                            text = exportButtonText(phase),
                            onClick = onExport,
                            modifier = Modifier.weight(1f),
                            enabled = state.videoName.isNotBlank(),
                        )
                    }
                    if (state.phase is ExportPhase.Complete) {
                        OutlineButton(
                            text = stringResource(R.string.back_to_home),
                            onClick = onGoHome,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            when (val phase = state.phase) {
                is ExportPhase.Exporting -> item {
                    StatusRow(
                        dotColor = VicuTheme.colors.secondary,
                        text = stringResource(R.string.exporting),
                        pulsing = true,
                    )
                }
                is ExportPhase.Complete -> item {
                    StatusRow(
                        dotColor = VicuTheme.colors.onSurfaceVariant,
                        text = stringResource(R.string.export_complete),
                    )
                }
                is ExportPhase.Failed -> item {
                    StatusRow(
                        dotColor = VicuTheme.colors.error,
                        text = stringResource(
                            R.string.export_failed,
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
        is ExportPhase.Exporting -> R.string.exporting
        else -> R.string.export_audio
    }
)

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
            onGoHome = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AudioExportPageExportingPreview() {
    VicuTheme {
        AudioExportContent(
            state = AudioExportUiState(
                videoName = "sample_video.mp4",
                phase = ExportPhase.Exporting(progress = 0.42f),
            ),
            onSelectFormat = {},
            onSelectQuality = {},
            onExport = {},
            onBack = {},
            onGoHome = {},
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
            onGoHome = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AudioExportPageCompletePreview() {
    VicuTheme {
        AudioExportContent(
            state = AudioExportUiState(
                videoName = "sample_video.mp4",
                phase = ExportPhase.Complete,
            ),
            onSelectFormat = {},
            onSelectQuality = {},
            onExport = {},
            onBack = {},
            onGoHome = {},
        )
    }
}
