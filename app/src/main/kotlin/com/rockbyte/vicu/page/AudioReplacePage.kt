package com.rockbyte.vicu.page

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.style.MutableStyleState
import androidx.compose.foundation.style.styleable
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.net.toUri
import com.rockbyte.vicu.ui.component.abbreviateMediaFileName
import com.rockbyte.vicu.ui.component.formatMediaClock
import com.rockbyte.vicu.R
import com.rockbyte.vicu.repo.AudioReplaceError
import com.rockbyte.vicu.repo.AudioReplaceMode
import com.rockbyte.vicu.repo.SelectedMedia
import com.rockbyte.vicu.ui.component.OutlineButton
import com.rockbyte.vicu.ui.component.PrimaryButton
import com.rockbyte.vicu.ui.component.ProgressButton
import com.rockbyte.vicu.ui.component.RadioOptionGroup
import com.rockbyte.vicu.ui.component.StatusRow
import com.rockbyte.vicu.ui.component.VicuScaffold
import com.rockbyte.vicu.ui.theme.VicuTheme
import org.koin.androidx.compose.koinViewModel

/**
 * 替换音轨页：从功能列表带入视频，先选背景音乐（自建媒体选择页，见 [MediaPickerPage]），
 * 再选时长适配策略（截断 / 伸缩视频 / 伸缩音频）后替换；替换过程中锁定全部操作并拦截系统返回。
 */
@Composable
fun AudioReplacePage(media: SelectedMedia, onPickMusic: () -> Unit, onBack: () -> Unit, onGoHome: () -> Unit) {
    val viewModel = koinViewModel<AudioReplaceViewModel>()
    LaunchedEffect(media) { viewModel.bind(media) }
    // 消费选择页回传的选中音乐；消费后立即清空，避免下次进入其他功能的 picker 读到脏数据
    val pickerViewModel = koinViewModel<MediaPickerViewModel>()
    val pickedMusic by pickerViewModel.selected.collectAsState()
    LaunchedEffect(pickedMusic) {
        pickedMusic?.let { music ->
            viewModel.selectMusic(music.uri.toUri(), music.name)
            pickerViewModel.consume()
        }
    }
    val state by viewModel.uiState.collectAsState()

    AudioReplaceContent(
        state = state,
        onPickMusic = onPickMusic,
        onSelectMode = viewModel::selectMode,
        onReplace = viewModel::replace,
        onBack = onBack,
        onGoHome = onGoHome,
    )
}

@Composable
private fun AudioReplaceContent(
    state: AudioReplaceUiState,
    onPickMusic: () -> Unit,
    onSelectMode: (AudioReplaceMode) -> Unit,
    onReplace: () -> Unit,
    onBack: () -> Unit,
    onGoHome: () -> Unit,
) {
    val converting = state.phase is ReplacePhase.Converting
    BackHandler(enabled = converting) { /* 替换中锁定，拦截系统返回 */ }

    VicuScaffold(
        title = stringResource(R.string.video_replace_audio),
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
            item { MusicSection(state, onPickMusic, converting) }
            item {
                BasicText(
                    text = stringResource(
                        R.string.replace_audio_duration_format,
                        durationText(state.videoDurationMs),
                        durationText(state.musicDurationMs),
                    ),
                    style = VicuTheme.typography.bodySm.copy(color = VicuTheme.colors.onSurfaceVariant),
                )
            }
            item {
                RadioOptionGroup(
                    label = stringResource(R.string.duration_matching),
                    options = AudioReplaceMode.entries,
                    selected = state.mode,
                    enabled = !converting && state.musicName != null,
                    optionLabel = { stringResource(it.labelRes) },
                    onSelect = onSelectMode,
                    // 伸缩两模式依赖两路时长已知；截断允许时长未知（内部回退 -shortest）
                    optionEnabled = { mode ->
                        mode == AudioReplaceMode.TRUNCATE ||
                            (state.videoDurationMs != null && state.musicDurationMs != null)
                    },
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(VicuTheme.dimensions.spacingUnit)) {
                    val phase = state.phase
                    if (phase is ReplacePhase.Converting) {
                        ProgressButton(
                            progress = phase.progress,
                            label = phase.progress?.let { stringResource(R.string.progress_percent_format, it * 100) }
                                ?: stringResource(R.string.replacing),
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        PrimaryButton(
                            text = replaceButtonText(state.phase),
                            onClick = onReplace,
                            modifier = Modifier.weight(1f),
                            enabled = state.musicName != null,
                        )
                    }
                    if (state.phase is ReplacePhase.Complete) {
                        OutlineButton(
                            text = stringResource(R.string.back_to_home),
                            onClick = onGoHome,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            when (val phase = state.phase) {
                is ReplacePhase.Converting -> item {
                    StatusRow(
                        dotColor = VicuTheme.colors.secondary,
                        text = stringResource(R.string.replacing),
                        pulsing = true,
                    )
                }
                ReplacePhase.Complete -> item {
                    StatusRow(
                        dotColor = VicuTheme.colors.onSurfaceVariant,
                        text = stringResource(R.string.saved_to_movies_folder),
                    )
                }
                is ReplacePhase.Failed -> item {
                    StatusRow(
                        dotColor = VicuTheme.colors.error,
                        text = stringResource(
                            R.string.replace_failed,
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

/** 背景音乐卡片：未选时提供选择按钮，已选时展示名称与时长，并可重新选择。 */
@Composable
private fun MusicSection(
    state: AudioReplaceUiState,
    onPickMusic: () -> Unit,
    converting: Boolean,
) {
    val cardState = remember { MutableStyleState(null) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .styleable(cardState, VicuTheme.styles.card),
        verticalArrangement = Arrangement.spacedBy(VicuTheme.dimensions.spacingUnit),
    ) {
        BasicText(
            text = stringResource(R.string.background_music),
            style = VicuTheme.typography.caption,
        )
        val musicName = state.musicName
        if (musicName == null) {
            PrimaryButton(
                text = stringResource(R.string.select_music),
                onClick = onPickMusic,
                modifier = Modifier.fillMaxWidth(),
                enabled = !converting,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(VicuTheme.dimensions.spacingUnit / 2)) {
                BasicText(
                    text = abbreviateMediaFileName(musicName),
                    style = VicuTheme.typography.bodyLg.copy(color = VicuTheme.colors.onSurface),
                )
                BasicText(
                    text = durationText(state.musicDurationMs),
                    style = VicuTheme.typography.bodySm.copy(color = VicuTheme.colors.onSurfaceVariant),
                )
            }
            OutlineButton(
                text = stringResource(R.string.select_again),
                onClick = onPickMusic,
                enabled = !converting,
            )
        }
    }
}

@Composable
private fun durationText(durationMs: Long?): String =
    durationMs?.takeIf { it > 0 }?.let { formatMediaClock(it / 1000) }
        ?: stringResource(R.string.unknown_duration)

private val AudioReplaceMode.labelRes: Int
    get() = when (this) {
        AudioReplaceMode.TRUNCATE -> R.string.replace_audio_mode_truncate
        AudioReplaceMode.STRETCH_VIDEO -> R.string.replace_audio_mode_stretch_video
        AudioReplaceMode.STRETCH_AUDIO -> R.string.replace_audio_mode_stretch_audio
    }

internal val AudioReplaceError.messageRes: Int
    get() = when (this) {
        AudioReplaceError.TranscodeFailed -> R.string.replace_audio_error_transcode
        AudioReplaceError.OutputCreationFailed -> R.string.replace_audio_error_output_creation
        AudioReplaceError.InvalidMedia -> R.string.replace_audio_error_invalid
        AudioReplaceError.Unknown -> R.string.unknown_error
    }

@Composable
private fun replaceButtonText(phase: ReplacePhase): String = stringResource(
    when (phase) {
        is ReplacePhase.Complete -> R.string.replace_success
        is ReplacePhase.Failed -> R.string.replace_failed_retry
        is ReplacePhase.Converting -> R.string.replacing
        else -> R.string.confirm
    }
)

@Preview(showBackground = true)
@Composable
private fun AudioReplacePageNoMusicPreview() {
    VicuTheme {
        AudioReplaceContent(
            state = AudioReplaceUiState(
                videoName = "sample_video.mp4",
                videoDurationMs = 90_000,
                phase = ReplacePhase.Ready,
            ),
            onPickMusic = {},
            onSelectMode = {},
            onReplace = {},
            onBack = {},
            onGoHome = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AudioReplacePageMusicSelectedPreview() {
    VicuTheme {
        AudioReplaceContent(
            state = AudioReplaceUiState(
                videoName = "sample_video.mp4",
                videoDurationMs = 90_000,
                musicName = "background_music.mp3",
                musicDurationMs = 200_000,
                mode = AudioReplaceMode.STRETCH_AUDIO,
                phase = ReplacePhase.Ready,
            ),
            onPickMusic = {},
            onSelectMode = {},
            onReplace = {},
            onBack = {},
            onGoHome = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AudioReplacePageConvertingPreview() {
    VicuTheme {
        AudioReplaceContent(
            state = AudioReplaceUiState(
                videoName = "sample_video.mp4",
                videoDurationMs = 90_000,
                musicName = "background_music.mp3",
                musicDurationMs = 200_000,
                phase = ReplacePhase.Converting(progress = 0.50f),
            ),
            onPickMusic = {},
            onSelectMode = {},
            onReplace = {},
            onBack = {},
            onGoHome = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AudioReplacePageCompletePreview() {
    VicuTheme {
        AudioReplaceContent(
            state = AudioReplaceUiState(
                videoName = "sample_video.mp4",
                videoDurationMs = 90_000,
                musicName = "background_music.mp3",
                musicDurationMs = 200_000,
                phase = ReplacePhase.Complete,
            ),
            onPickMusic = {},
            onSelectMode = {},
            onReplace = {},
            onBack = {},
            onGoHome = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AudioReplacePageFailedPreview() {
    VicuTheme {
        AudioReplaceContent(
            state = AudioReplaceUiState(
                videoName = "sample_video.mp4",
                videoDurationMs = 90_000,
                musicName = "background_music.mp3",
                musicDurationMs = 200_000,
                phase = ReplacePhase.Failed(AudioReplaceError.TranscodeFailed),
            ),
            onPickMusic = {},
            onSelectMode = {},
            onReplace = {},
            onBack = {},
            onGoHome = {},
        )
    }
}
