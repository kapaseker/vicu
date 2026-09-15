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
import com.rockbyte.vicu.repo.AudioConvertError
import com.rockbyte.vicu.repo.AudioConvertFormat
import com.rockbyte.vicu.repo.AudioConvertQuality
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
 * 音频转换页：从功能列表带入音频，选择格式（MP3/M4A/WAV/FLAC/OGG）与质量（最佳/高/中/低）后转码；
 * 转换过程中锁定全部操作并拦截系统返回；无损格式（WAV/FLAC）不适用码率质量档。
 */
@Composable
fun AudioConvertPage(media: SelectedMedia, onBack: () -> Unit, onGoHome: () -> Unit) {
    val viewModel = koinViewModel<AudioConvertViewModel>()
    LaunchedEffect(media) { viewModel.bind(media) }
    val state by viewModel.uiState.collectAsState()

    AudioConvertContent(
        state = state,
        onSelectFormat = viewModel::selectFormat,
        onSelectQuality = viewModel::selectQuality,
        onConvert = viewModel::convert,
        onBack = onBack,
        onGoHome = onGoHome,
    )
}

@Composable
private fun AudioConvertContent(
    state: AudioConvertUiState,
    onSelectFormat: (AudioConvertFormat) -> Unit,
    onSelectQuality: (AudioConvertQuality) -> Unit,
    onConvert: () -> Unit,
    onBack: () -> Unit,
    onGoHome: () -> Unit,
) {
    val converting = state.phase is AudioConvertPhase.Converting
    BackHandler(enabled = converting) { /* 转换中锁定，拦截系统返回 */ }

    VicuScaffold(
        title = stringResource(R.string.audio_convert),
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
                    options = AudioConvertFormat.entries,
                    selected = state.format,
                    enabled = !converting,
                    optionLabel = { stringResource(it.labelRes) },
                    onSelect = onSelectFormat,
                )
            }
            item {
                RadioOptionGroup(
                    label = stringResource(R.string.export_quality_label),
                    options = AudioConvertQuality.entries,
                    selected = state.quality,
                    enabled = !converting && !state.format.lossless,
                    optionLabel = { stringResource(it.labelRes) },
                    onSelect = onSelectQuality,
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(VicuTheme.dimensions.spacingUnit)) {
                    val phase = state.phase
                    if (phase is AudioConvertPhase.Converting) {
                        ProgressButton(
                            progress = phase.progress,
                            label = phase.progress?.let { stringResource(R.string.progress_percent_format, it * 100) }
                                ?: stringResource(R.string.converting),
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        PrimaryIconButton(
                            icon = R.drawable.ic_transfer,
                            text = convertButtonText(state.phase),
                            onClick = onConvert,
                            modifier = Modifier.weight(1f),
                            enabled = state.audioName.isNotBlank(),
                        )
                    }
                    if (state.phase is AudioConvertPhase.Complete) {
                        OutlineButton(
                            text = stringResource(R.string.back_to_home),
                            onClick = onGoHome,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            when (val phase = state.phase) {
                is AudioConvertPhase.Converting -> item {
                    StatusRow(
                        dotColor = VicuTheme.colors.secondary,
                        text = stringResource(R.string.converting),
                        pulsing = true,
                    )
                }
                AudioConvertPhase.Complete -> item {
                    StatusRow(
                        dotColor = VicuTheme.colors.onSurfaceVariant,
                        text = stringResource(R.string.audio_convert_complete),
                    )
                }
                is AudioConvertPhase.Failed -> item {
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

private val AudioConvertFormat.labelRes: Int
    get() = when (this) {
        AudioConvertFormat.MP3 -> R.string.format_mp3
        AudioConvertFormat.M4A -> R.string.format_m4a
        AudioConvertFormat.WAV -> R.string.format_wav
        AudioConvertFormat.FLAC -> R.string.format_flac
        AudioConvertFormat.OGG -> R.string.format_ogg
    }

private val AudioConvertQuality.labelRes: Int
    get() = when (this) {
        AudioConvertQuality.BEST_QUALITY -> R.string.quality_finest
        AudioConvertQuality.BALANCED -> R.string.quality_balanced
        AudioConvertQuality.SMALLEST -> R.string.quality_smallest
        AudioConvertQuality.SUITABLE -> R.string.quality_suitable
    }

internal val AudioConvertError.messageRes: Int
    get() = when (this) {
        AudioConvertError.TranscodeFailed -> R.string.audio_convert_error_transcode
        AudioConvertError.OutputCreationFailed -> R.string.audio_convert_error_output_creation
        AudioConvertError.Unknown -> R.string.audio_convert_error_unknown
    }

@Composable
private fun convertButtonText(phase: AudioConvertPhase): String = stringResource(
    when (phase) {
        is AudioConvertPhase.Complete -> R.string.convert_success
        is AudioConvertPhase.Failed -> R.string.convert_failed_retry
        is AudioConvertPhase.Converting -> R.string.converting
        else -> R.string.audio_convert
    }
)

@Preview(showBackground = true)
@Composable
private fun AudioConvertPageReadyPreview() {
    VicuTheme {
        AudioConvertContent(
            state = AudioConvertUiState(
                audioName = "sample_audio.mp3",
                phase = AudioConvertPhase.Ready,
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
private fun AudioConvertPageFailedPreview() {
    VicuTheme {
        AudioConvertContent(
            state = AudioConvertUiState(
                audioName = "sample_audio.mp3",
                phase = AudioConvertPhase.Failed(AudioConvertError.TranscodeFailed),
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
private fun AudioConvertPageConvertingPreview() {
    VicuTheme {
        AudioConvertContent(
            state = AudioConvertUiState(
                audioName = "sample_audio.mp3",
                phase = AudioConvertPhase.Converting(progress = 0.50f),
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
private fun AudioConvertPageCompletePreview() {
    VicuTheme {
        AudioConvertContent(
            state = AudioConvertUiState(
                audioName = "sample_audio.mp3",
                phase = AudioConvertPhase.Complete,
            ),
            onSelectFormat = {},
            onSelectQuality = {},
            onConvert = {},
            onBack = {},
            onGoHome = {},
        )
    }
}
