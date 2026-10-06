package com.rockbyte.vicu.page

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
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
 * 首页：MediaStore 聚合的媒体库 grid（图片/视频/音频），
 * 每格左上角类型角标区分多媒体类型；点击进入功能列表；未授权时展示权限提示。
 */
@Composable
fun HomePage(onMediaClick: (MediaItem) -> Unit) {
    val viewModel = koinViewModel<HomeViewModel>()
    val state by viewModel.uiState.collectAsState()
    HomePageContent(
        state = state,
        onRefresh = viewModel::refresh,
        onMediaClick = onMediaClick,
    )
}

@Composable
private fun HomePageContent(
    state: MediaLibraryUiState,
    onRefresh: () -> Unit,
    onMediaClick: (MediaItem) -> Unit,
) {
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { onRefresh() }

    HomePageBody(
        state = state,
        onRequestPermission = {
            permissionLauncher.launch(state.permissionsToRequest.toTypedArray())
        },
        onMediaClick = onMediaClick,
    )
}

@Composable
private fun HomePageBody(
    state: MediaLibraryUiState,
    onRequestPermission: () -> Unit,
    onMediaClick: (MediaItem) -> Unit,
) {
    VicuScaffold(title = stringResource(R.string.app_name)) {
        when (state.hasAccess) {
            true -> MediaGrid(state.items, state.loading, onMediaClick)
            false -> PermissionPrompt(onRequest = onRequestPermission)
            null -> Unit
        }
    }
}

@Composable
private fun PermissionPrompt(onRequest: () -> Unit) {
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
                onClick = onRequest,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomePageGridPreview() {
    VicuTheme {
        HomePageContent(
            state = MediaLibraryUiState(
                loading = false,
                hasAccess = true,
                items = listOf(
                    MediaItem(Uri.parse("content://media/external/images/1"), "photo.jpg", MediaKind.IMAGE, Instant.parse("2026-09-16T08:00:00Z").epochSecond),
                    MediaItem(Uri.parse("content://media/external/video/2"), "clip.mp4", MediaKind.VIDEO, Instant.parse("2026-09-15T08:00:00Z").epochSecond, durationMs = 65_000),
                    MediaItem(Uri.parse("content://media/external/audio/3"), "a_long_music_file_name_for_two_lines.mp3", MediaKind.AUDIO, Instant.parse("2026-09-15T07:00:00Z").epochSecond, durationMs = 3_665_000),
                )
            ),
            onRefresh = {},
            onMediaClick = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HomePageEmptyPreview() {
    VicuTheme {
        HomePageContent(
            state = MediaLibraryUiState(loading = false, hasAccess = true),
            onRefresh = {},
            onMediaClick = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HomePagePermissionPreview() {
    VicuTheme {
        HomePageBody(
            state = MediaLibraryUiState(
                loading = false,
                hasAccess = false,
                permissionsToRequest = listOf("android.permission.READ_MEDIA_VIDEO"),
            ),
            onRequestPermission = {},
            onMediaClick = {},
        )
    }
}
