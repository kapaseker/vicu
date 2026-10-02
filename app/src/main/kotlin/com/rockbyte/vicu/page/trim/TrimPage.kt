package com.rockbyte.vicu.page.trim

import android.view.Surface
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.rockbyte.vicu.R
import com.rockbyte.vicu.page.player.PlayerPhase
import com.rockbyte.vicu.page.player.PlayerUiState
import com.rockbyte.vicu.page.player.PlayerViewModel
import com.rockbyte.vicu.player.PlayerEffect
import com.rockbyte.vicu.repo.SelectedMedia
import com.rockbyte.vicu.ui.component.*
import com.rockbyte.vicu.ui.theme.VicuTheme
import org.koin.androidx.compose.koinViewModel

@Composable
fun TrimPage(media: SelectedMedia, onBack: () -> Unit, onGoHome: () -> Unit) {
    val player = koinViewModel<PlayerViewModel>()
    val trim = koinViewModel<TrimViewModel>()
    LaunchedEffect(media) { player.bind(media); trim.bind(media) }
    DisposableEffect(player) { onDispose { player.release() } }
    val playerState by player.uiState.collectAsState()
    val trimState by trim.uiState.collectAsState()
    LaunchedEffect(playerState.durationMs) {
        trim.setDuration(playerState.durationMs)
        trim.uiState.value.range?.let(player::setTrim)
    }
    TrimContent(
        playerState, trimState,
        onRangeChange = { range ->
            if (trim.selectRange(range.startMs, range.endMs)) {
                player.pause()
                player.setTrim(range)
            }
        },
        onPreview = player::previewAt,
        onSeek = { position -> player.seekTo(position) },
        onPlayPause = player::togglePlayPause,
        onConfirm = { start, end ->
            val error = trim.confirm(start, end)
            if (error == null) {
                player.pause()
                trim.uiState.value.range?.let(player::setTrim)
            }
            error
        },
        onSurfaceAvailable = player::setSurface,
        onBack = onBack,
        onGoHome = onGoHome,
    )
}

@Composable
private fun TrimContent(
    playerState: PlayerUiState,
    trimState: TrimUiState,
    onRangeChange: (PlayerEffect.Trim) -> Unit,
    onPreview: (Long) -> Unit,
    onSeek: (Long) -> Unit,
    onPlayPause: () -> Unit,
    onConfirm: (String, String) -> TrimInputError?,
    onSurfaceAvailable: (Surface?) -> Unit,
    onBack: () -> Unit,
    onGoHome: () -> Unit,
) {
    val trimming = trimState.phase is TrimPhase.Trimming
    val ready = playerState.videoWidth > 0 && playerState.videoHeight > 0 &&
        playerState.phase in setOf(PlayerPhase.Playing, PlayerPhase.Paused, PlayerPhase.Ended) && trimState.durationMs > 0
    val editable = ready && !trimming
    BackHandler(enabled = trimming) { }
    var startText by remember { mutableStateOf("0.000") }
    var endText by remember { mutableStateOf("0.000") }
    LaunchedEffect(trimState.durationMs) {
        startText = formatTrimTime(trimState.range?.startMs ?: 0)
        endText = formatTrimTime(trimState.range?.endMs ?: 0)
    }
    fun syncInput() {
        if (validateTrimInput(startText, endText, trimState.durationMs) == null) {
            onRangeChange(PlayerEffect.Trim(parseTrimTime(startText)!!, parseTrimTime(endText)!!))
        }
    }
    val context = LocalContext.current
    val resources = LocalResources.current
    val focusManager = LocalFocusManager.current
    VicuScaffold(title = stringResource(R.string.video_trim), onBack = onBack, backEnabled = !trimming) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val availableWidth = maxWidth - VicuTheme.dimensions.screenGutter * 2
            val availableHeight = maxHeight
            Column(
                Modifier.fillMaxSize().navigationBarsPadding().imePadding()
                    .verticalScroll(rememberScrollState()).padding(VicuTheme.dimensions.screenGutter),
                verticalArrangement = Arrangement.spacedBy(VicuTheme.dimensions.spacingUnit * 2),
            ) {
                val aspectRatio = if (playerState.videoWidth > 0 && playerState.videoHeight > 0)
                    playerState.videoWidth.toFloat() / playerState.videoHeight else 16f / 9f
                Box(Modifier.align(Alignment.CenterHorizontally)
                    .width(videoPreviewWidth(availableWidth, availableHeight, aspectRatio)).aspectRatio(aspectRatio)) {
                    VideoSurface(Modifier.matchParentSize(), onSurfaceAvailable)
                    if (ready) {
                        Box(
                            Modifier.align(Alignment.Center).size(VicuTheme.dimensions.navigationTouchSize)
                                .clip(CircleShape)
                                .background(VicuTheme.colors.surfaceContainerLowest.copy(alpha = VicuTheme.alpha.glassOverlay))
                                .clickable(enabled = editable, onClick = onPlayPause),
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(
                                painterResource(if (playerState.playing) R.drawable.ic_pause else R.drawable.ic_play),
                                stringResource(if (playerState.playing) R.string.player_pause else R.string.player_play),
                                Modifier.size(VicuTheme.dimensions.iconMedium),
                                colorFilter = ColorFilter.tint(VicuTheme.colors.onSurface),
                            )
                        }
                    }
                }
                TrimRangeBar(
                    range = trimState.range ?: PlayerEffect.Trim(0, 1),
                    durationMs = trimState.durationMs,
                    positionMs = playerState.positionMs,
                    enabled = editable,
                    onChange = { range, position ->
                        focusManager.clearFocus()
                        startText = formatTrimTime(range.startMs)
                        endText = formatTrimTime(range.endMs)
                        onRangeChange(range)
                        onPreview(position)
                    },
                    onSeek = onSeek,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(VicuTheme.dimensions.spacingUnit * 2)) {
                    TrimTimeInput(
                        stringResource(R.string.trim_start), startText, editable, TextAlign.Start, Modifier.weight(1f),
                        onChange = { startText = it; syncInput() },
                        onFinish = { if (validateTrimInput(startText, endText, trimState.durationMs) == null) startText = formatTrimTime(parseTrimTime(startText)!!) },
                    )
                    TrimTimeInput(
                        stringResource(R.string.trim_end), endText, editable, TextAlign.End, Modifier.weight(1f),
                        onChange = { endText = it; syncInput() },
                        onFinish = { if (validateTrimInput(startText, endText, trimState.durationMs) == null) endText = formatTrimTime(parseTrimTime(endText)!!) },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(VicuTheme.dimensions.spacingUnit)) {
                    val phase = trimState.phase
                    if (phase is TrimPhase.Trimming) {
                        ProgressButton(phase.progress,
                            phase.progress?.let { stringResource(R.string.progress_percent_format, it * 100) }
                                ?: stringResource(R.string.trimming), Modifier.weight(1f))
                    } else {
                        PrimaryButton(
                            text = stringResource(if (phase is TrimPhase.Failed) R.string.trim_retry else R.string.confirm),
                            enabled = editable,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                focusManager.clearFocus()
                                onConfirm(startText, endText)?.let { error ->
                                    Toast.makeText(context, resources.getString(error.messageRes), Toast.LENGTH_SHORT).show()
                                }
                            },
                        )
                    }
                    if (phase is TrimPhase.Complete) {
                        OutlineButton(stringResource(R.string.back_to_home), onGoHome, Modifier.weight(1f))
                    }
                }
                when (val phase = trimState.phase) {
                    is TrimPhase.Trimming -> StatusRow(VicuTheme.colors.secondary, stringResource(R.string.trimming), pulsing = true)
                    TrimPhase.Complete -> StatusRow(VicuTheme.colors.onSurfaceVariant, stringResource(R.string.trim_complete))
                    is TrimPhase.Failed -> StatusRow(VicuTheme.colors.error,
                        stringResource(R.string.trim_failed, stringResource(phase.error.messageRes)), textColor = VicuTheme.colors.error)
                    TrimPhase.Idle -> Unit
                }
                when (val phase = playerState.phase) {
                    PlayerPhase.Preparing -> StatusRow(VicuTheme.colors.secondary, stringResource(R.string.player_preparing), pulsing = true)
                    is PlayerPhase.Failed -> StatusRow(VicuTheme.colors.error,
                        stringResource(R.string.player_failed, stringResource(phase.error.messageRes)), textColor = VicuTheme.colors.error)
                    else -> Unit
                }
            }
        }
    }
}

@Composable
private fun TrimTimeInput(
    label: String, value: String, enabled: Boolean, alignment: TextAlign, modifier: Modifier,
    onChange: (String) -> Unit, onFinish: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(VicuTheme.dimensions.spacingUnit)) {
        BasicText(label, Modifier.fillMaxWidth(), style = VicuTheme.typography.caption.copy(
            color = VicuTheme.colors.onSurfaceVariant, textAlign = alignment))
        BasicTextField(
            value, onChange, enabled = enabled, singleLine = true,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label }.onFocusChanged {
                if (focused && !it.isFocused) onFinish()
                focused = it.isFocused
            }.background(VicuTheme.colors.surfaceContainerLow, VicuTheme.shapes.base)
                .border(VicuTheme.dimensions.cardBorderWidth,
                    if (focused) VicuTheme.colors.primary else VicuTheme.colors.outlineVariant, VicuTheme.shapes.base)
                .padding(VicuTheme.dimensions.spacingUnit),
            textStyle = VicuTheme.typography.bodyLg.copy(color = VicuTheme.colors.onSurface, textAlign = alignment),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onFinish(); focusManager.clearFocus() }),
        )
    }
}

private val TrimInputError.messageRes: Int get() = when (this) {
    TrimInputError.EMPTY -> R.string.trim_error_empty
    TrimInputError.FORMAT -> R.string.trim_error_format
    TrimInputError.OUT_OF_BOUNDS -> R.string.trim_error_bounds
    TrimInputError.ORDER -> R.string.trim_error_order
}
private val com.rockbyte.vicu.repo.VideoConvertError.messageRes: Int get() = when (this) {
    com.rockbyte.vicu.repo.VideoConvertError.TranscodeFailed -> R.string.convert_error_transcode
    com.rockbyte.vicu.repo.VideoConvertError.OutputCreationFailed -> R.string.convert_error_output_creation
    com.rockbyte.vicu.repo.VideoConvertError.Unknown -> R.string.convert_error_unknown
}
private val com.rockbyte.vicu.player.PlayerError.messageRes: Int get() = when (this) {
    com.rockbyte.vicu.player.PlayerError.OpenFailed -> R.string.player_error_open
    com.rockbyte.vicu.player.PlayerError.PlaybackFailed -> R.string.player_error_playback
}

@Preview(showBackground = true)
@Composable
private fun TrimPageReadyPreview() {
    VicuTheme {
        TrimContent(PlayerUiState(videoWidth = 1920, videoHeight = 1080, durationMs = 10000, phase = PlayerPhase.Paused),
            TrimUiState(10000, PlayerEffect.Trim(1000, 7000)), {}, {}, {}, {}, { _, _ -> null }, {}, {}, {})
    }
}
