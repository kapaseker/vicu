package com.rockbyte.vicu.page

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.rockbyte.vicu.R
import com.rockbyte.vicu.repo.MediaItem
import com.rockbyte.vicu.repo.MediaKind
import com.rockbyte.vicu.ui.component.MediaGrid
import com.rockbyte.vicu.ui.component.PrimaryButton
import com.rockbyte.vicu.ui.component.VicuScaffold
import com.rockbyte.vicu.ui.theme.VicuTheme
import java.time.Instant
import org.koin.androidx.compose.koinViewModel

/**
 * 自建媒体选择页：替代 SAF 文件夹选择，展示 [MediaRepo] 媒体库中 [kinds] 类型的条目
 * （参考首页 UI：日期分组 + Grid Card）；单选即点即回，选中结果经 [MediaPickerViewModel] 回传调用页。
 */
@Composable
fun MediaPickerPage(kinds: Set<MediaKind>, onDone: () -> Unit, onBack: () -> Unit) {
    val viewModel = koinViewModel<MediaPickerViewModel>()
    LaunchedEffect(kinds) { viewModel.bind(kinds) }
    val state by viewModel.uiState.collectAsState()

    MediaPickerContent(
        state = state,
        onGrant = viewModel::refresh,
        onPick = { item ->
            viewModel.confirm(item)
            onDone()
        },
        onBack = onBack,
    )
}

@Composable
private fun MediaPickerContent(
    state: MediaPickerUiState,
    onGrant: () -> Unit,
    onPick: (MediaItem) -> Unit,
    onBack: () -> Unit,
) {
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { onGrant() }

    VicuScaffold(title = stringResource(R.string.select_music), onBack = onBack) {
        when (state.hasAccess) {
            true -> MediaGrid(state.items, state.loading, onPick)
            false -> PermissionPrompt(state.permissionsToRequest, permissionLauncher::launch)
            null -> Unit
        }
    }
}

/** 未授权提示：与首页一致的居中说明 + 授权按钮。 */
@Composable
private fun PermissionPrompt(
    permissionsToRequest: List<String>,
    launch: (Array<String>) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = VicuTheme.dimensions.screenGutter),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(VicuTheme.dimensions.spacingUnit * 2),
        ) {
            BasicText(
                text = stringResource(R.string.media_permission_rationale),
                style = VicuTheme.typography.bodyLg.copy(color = VicuTheme.colors.onSurfaceVariant),
            )
            PrimaryButton(
                text = stringResource(R.string.grant_media_permission),
                onClick = { launch(permissionsToRequest.toTypedArray()) },
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MediaPickerListPreview() {
    VicuTheme {
        MediaPickerContent(
            state = MediaPickerUiState(
                loading = false,
                hasAccess = true,
                items = listOf(
                    MediaItem(
                        Uri.parse("content://media/external/audio/1"),
                        "background_music.mp3",
                        MediaKind.AUDIO,
                        Instant.parse("2026-10-06T02:00:00Z").epochSecond,
                        durationMs = 200_000,
                    ),
                    MediaItem(
                        Uri.parse("content://media/external/audio/2"),
                        "intro_theme.flac",
                        MediaKind.AUDIO,
                        Instant.parse("2026-10-05T02:00:00Z").epochSecond,
                        durationMs = 65_000,
                    ),
                ),
            ),
            onGrant = {},
            onPick = {},
            onBack = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MediaPickerEmptyPreview() {
    VicuTheme {
        MediaPickerContent(
            state = MediaPickerUiState(loading = false, hasAccess = true),
            onGrant = {},
            onPick = {},
            onBack = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MediaPickerPermissionPreview() {
    VicuTheme {
        MediaPickerContent(
            state = MediaPickerUiState(
                loading = false,
                hasAccess = false,
                permissionsToRequest = listOf("android.permission.READ_MEDIA_AUDIO"),
            ),
            onGrant = {},
            onPick = {},
            onBack = {},
        )
    }
}
