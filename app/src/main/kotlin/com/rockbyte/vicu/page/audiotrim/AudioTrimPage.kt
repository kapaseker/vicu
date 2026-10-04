package com.rockbyte.vicu.page.audiotrim

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.rockbyte.vicu.R
import com.rockbyte.vicu.page.audiotrim.screen.AudioWaveformScreen
import com.rockbyte.vicu.player.PlayerEffect
import com.rockbyte.vicu.repo.*
import com.rockbyte.vicu.ui.component.*
import com.rockbyte.vicu.ui.component.trim.*
import com.rockbyte.vicu.ui.theme.VicuTheme
import org.koin.androidx.compose.koinViewModel

@Composable
fun AudioTrimPage(media: SelectedMedia, onBack: () -> Unit, onGoHome: () -> Unit) {
    val vm = koinViewModel<AudioTrimViewModel>()
    LaunchedEffect(media) { vm.bind(media) }
    DisposableEffect(vm) { onDispose { vm.release() } }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(vm, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) vm.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val state by vm.uiState.collectAsState()
    val preview by vm.previewState.collectAsState()
    val waveform by vm.waveformState.collectAsState()
    AudioTrimContent(state, preview, waveform, vm::retryWaveform, vm::selectRange, vm::seekTo,
        vm::togglePlayPause, vm::confirm, onBack, onGoHome)
}

@Composable
private fun AudioTrimContent(
    state: AudioTrimUiState,
    preview: AudioPreviewState,
    waveform: AudioWaveformState,
    onRetryWaveform: () -> Unit,
    onRangeChange: (Long, Long) -> Boolean,
    onSeek: (Long) -> Unit,
    onPlayPause: () -> Unit,
    onConfirm: (String, String) -> TrimInputError?,
    onBack: () -> Unit,
    onGoHome: () -> Unit,
) {
    val trimming = state.phase is AudioTrimPhase.Trimming
    BackHandler(enabled = trimming) { }
    var startText by remember(state.audioName) { mutableStateOf("0.000") }
    var endText by remember(state.audioName) { mutableStateOf("0.000") }
    LaunchedEffect(state.durationMs, state.audioName) {
        startText = formatTrimTime(state.range?.startMs ?: 0)
        endText = formatTrimTime(state.range?.endMs ?: 0)
    }
    fun syncInput() {
        if (validateTrimInput(startText, endText, state.durationMs) == null) {
            onRangeChange(parseTrimTime(startText)!!, parseTrimTime(endText)!!)
        }
    }
    val context = LocalContext.current
    val resources = LocalResources.current
    val focusManager = LocalFocusManager.current
    VicuScaffold(title = stringResource(R.string.audio_trim), onBack = onBack, backEnabled = !trimming) {
        Column(
            Modifier.fillMaxSize().navigationBarsPadding().imePadding()
                .verticalScroll(rememberScrollState()).padding(VicuTheme.dimensions.screenGutter),
            verticalArrangement = Arrangement.spacedBy(VicuTheme.dimensions.spacingUnit * 2),
        ) {
            BasicText(stringResource(R.string.audio_trim_file, state.audioName),
                style = VicuTheme.typography.bodyLg.copy(color = VicuTheme.colors.onSurface))
            BasicText(stringResource(R.string.audio_trim_position,
                formatTrimTime(preview.positionMs), formatTrimTime(state.durationMs)), Modifier.fillMaxWidth(),
                style = VicuTheme.typography.bodySm.copy(color = VicuTheme.colors.onSurfaceVariant, textAlign = TextAlign.Center))
            OutlineButton(stringResource(if (preview.playing) R.string.player_pause else R.string.player_play),
                onPlayPause, Modifier.align(Alignment.CenterHorizontally),
                enabled = state.editable && preview.phase == AudioPreviewPhase.Ready)
            AudioWaveformScreen(waveform, state.range ?: PlayerEffect.Trim(0, 1), state.durationMs,
                preview.positionMs, preview.playing, state.editable, onChange = { range, position ->
                    focusManager.clearFocus()
                    startText = formatTrimTime(range.startMs)
                    endText = formatTrimTime(range.endMs)
                    if (onRangeChange(range.startMs, range.endMs)) onSeek(position)
                }, onSeek = onSeek, onRetry = onRetryWaveform)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(VicuTheme.dimensions.spacingUnit * 2)) {
                TrimTimeInput(stringResource(R.string.trim_start), startText, state.editable,
                    TextAlign.Start, Modifier.weight(1f),
                    onChange = { startText = it; syncInput() },
                    onFinish = { if (validateTrimInput(startText, endText, state.durationMs) == null) startText = formatTrimTime(parseTrimTime(startText)!!) })
                TrimTimeInput(stringResource(R.string.trim_end), endText, state.editable,
                    TextAlign.End, Modifier.weight(1f),
                    onChange = { endText = it; syncInput() },
                    onFinish = { if (validateTrimInput(startText, endText, state.durationMs) == null) endText = formatTrimTime(parseTrimTime(endText)!!) })
            }
            BasicText(stringResource(R.string.audio_trim_precision),
                style = VicuTheme.typography.caption.copy(color = VicuTheme.colors.onSurfaceVariant))
            Row(horizontalArrangement = Arrangement.spacedBy(VicuTheme.dimensions.spacingUnit)) {
                val phase = state.phase
                if (phase is AudioTrimPhase.Trimming) {
                    ProgressButton(phase.progress, phase.progress?.let {
                        stringResource(R.string.progress_percent_format, it * 100)
                    } ?: stringResource(R.string.trimming), Modifier.weight(1f))
                } else {
                    PrimaryButton(stringResource(if (phase is AudioTrimPhase.Failed) R.string.trim_retry else R.string.confirm),
                        enabled = state.editable, modifier = Modifier.weight(1f), onClick = {
                            focusManager.clearFocus()
                            onConfirm(startText, endText)?.let { error ->
                                val message = if (error == TrimInputError.OUT_OF_BOUNDS) R.string.audio_trim_error_bounds else error.messageRes
                                Toast.makeText(context, resources.getString(message), Toast.LENGTH_SHORT).show()
                            }
                        })
                }
                if (phase is AudioTrimPhase.Complete) {
                    OutlineButton(stringResource(R.string.back_to_home), onGoHome, Modifier.weight(1f))
                }
            }
            when (val phase = state.phase) {
                AudioTrimPhase.Idle -> Unit
                is AudioTrimPhase.Trimming -> StatusRow(VicuTheme.colors.secondary, stringResource(R.string.trimming), pulsing = true)
                AudioTrimPhase.Complete -> StatusRow(VicuTheme.colors.onSurfaceVariant, stringResource(R.string.audio_convert_complete))
                is AudioTrimPhase.Failed -> StatusRow(VicuTheme.colors.error,
                    stringResource(R.string.trim_failed, stringResource(phase.error.messageRes)), textColor = VicuTheme.colors.error)
            }
            if (state.loading) StatusRow(VicuTheme.colors.secondary, stringResource(R.string.audio_trim_loading), pulsing = true)
            state.sourceError?.let { StatusRow(VicuTheme.colors.error, stringResource(it.messageRes), textColor = VicuTheme.colors.error) }
            when (preview.phase) {
                AudioPreviewPhase.Preparing -> StatusRow(VicuTheme.colors.secondary, stringResource(R.string.player_preparing), pulsing = true)
                AudioPreviewPhase.Failed -> StatusRow(VicuTheme.colors.error,
                    stringResource(R.string.audio_trim_preview_failed), textColor = VicuTheme.colors.error)
                else -> Unit
            }
        }
    }
}

internal val AudioTrimError.messageRes: Int get() = when (this) {
    AudioTrimError.ProbeFailed -> R.string.audio_trim_error_probe
    AudioTrimError.UnsupportedFormat -> R.string.audio_trim_error_format
    AudioTrimError.InvalidRange -> R.string.audio_trim_error_bounds
    AudioTrimError.OutputCreationFailed -> R.string.audio_trim_error_create
    AudioTrimError.TrimFailed -> R.string.audio_trim_error_export
    AudioTrimError.OutputPublicationFailed -> R.string.audio_trim_error_publish
}

@Preview(showBackground = true)
@Composable
private fun AudioTrimReadyPreview() {
    VicuTheme {
        AudioTrimContent(AudioTrimUiState("music.mp3", false, 10000, PlayerEffect.Trim(1000, 7000)),
            AudioPreviewState(AudioPreviewPhase.Ready, 2000), AudioWaveformState.Ready(
                AudioWaveform(FloatArray(1000) { i -> if (i in 400..450) 0f else ((i % 37) / 37f) * 0.8f }, 10.0, 10000.0)),
            {}, { _, _ -> true }, {}, {}, { _, _ -> null }, {}, {})
    }
}
