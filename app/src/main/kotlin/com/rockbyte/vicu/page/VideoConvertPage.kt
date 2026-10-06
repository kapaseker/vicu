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
import com.rockbyte.vicu.repo.SelectedMedia
import com.rockbyte.vicu.repo.VideoConvertError
import com.rockbyte.vicu.repo.VideoConvertFormat
import com.rockbyte.vicu.repo.VideoConvertQuality
import com.rockbyte.vicu.ui.component.OutlineButton
import com.rockbyte.vicu.ui.component.PrimaryButton
import com.rockbyte.vicu.ui.component.ProgressButton
import com.rockbyte.vicu.ui.component.RadioOptionGroup
import com.rockbyte.vicu.ui.component.StatusRow
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
                    label = stringResource(R.string.format),
                    options = VideoConvertFormat.entries,
                    selected = state.format,
                    enabled = !converting,
                    optionLabel = { stringResource(it.labelRes) },
                    onSelect = onSelectFormat,
                )
            }
            item {
                RadioOptionGroup(
                    label = stringResource(R.string.quality),
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
                        PrimaryButton(
                            text = convertButtonText(state.phase),
                            onClick = onConvert,
                            modifier = Modifier.weight(1f),
                            enabled = state.videoName.isNotBlank(),
                        )
                    }
                    if (state.phase is ConvertPhase.Complete) {
                        OutlineButton(
                            text = stringResource(R.string.back_to_home),
                            onClick = onGoHome,
                            modifier = Modifier.weight(1f),
                        )
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
                        text = stringResource(R.string.saved_to_movies_folder),
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

private val VideoConvertFormat.labelRes: Int
    get() = when (this) {
        VideoConvertFormat.MP4 -> R.string.mp4
        VideoConvertFormat.MKV -> R.string.mkv
        VideoConvertFormat.WEBM -> R.string.webm
        VideoConvertFormat.AVI -> R.string.avi
        VideoConvertFormat.MOV -> R.string.mov
    }

private val VideoConvertQuality.labelRes: Int
    get() = when (this) {
        VideoConvertQuality.BEST_QUALITY -> R.string.best_quality
        VideoConvertQuality.BALANCED -> R.string.balanced
        VideoConvertQuality.SMALLEST -> R.string.smallest_size
        VideoConvertQuality.SUITABLE -> R.string.most_suitable
    }

internal val VideoConvertError.messageRes: Int
    get() = when (this) {
        VideoConvertError.TranscodeFailed -> R.string.convert_error_transcode
        VideoConvertError.OutputCreationFailed -> R.string.cannot_create_conversion_file
        VideoConvertError.Unknown -> R.string.unknown_error
    }

@Composable
private fun convertButtonText(phase: ConvertPhase): String = stringResource(
    when (phase) {
        is ConvertPhase.Complete -> R.string.convert_success
        is ConvertPhase.Failed -> R.string.convert_failed_retry
        is ConvertPhase.Converting -> R.string.converting
        else -> R.string.confirm
    }
)

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
