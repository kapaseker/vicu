package com.rockbyte.vicu.page

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.rockbyte.vicu.R
import com.rockbyte.vicu.repo.MediaItem
import com.rockbyte.vicu.repo.MediaKind
import com.rockbyte.vicu.ui.component.PrimaryButton
import com.rockbyte.vicu.ui.component.VicuScaffold
import com.rockbyte.vicu.ui.theme.VicuTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.koin.androidx.compose.koinViewModel

/**
 * 自建媒体选择页：替代 SAF 文件夹选择，展示 [MediaRepo] 媒体库中 [kinds] 类型的条目
 * （参考首页 UI：日期分组 + 列表行）；单选即点即回，选中结果经 [MediaPickerViewModel] 回传调用页。
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
            true -> MediaList(state.items, onPick)
            false -> PermissionPrompt(state.permissionsToRequest, permissionLauncher::launch)
            null -> Unit
        }
    }
}

@Composable
private fun MediaList(items: List<MediaItem>, onPick: (MediaItem) -> Unit) {
    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize()) {
            BasicText(
                text = stringResource(R.string.media_empty),
                style = VicuTheme.typography.bodySm.copy(color = VicuTheme.colors.onSurfaceVariant),
                modifier = Modifier.align(Alignment.Center),
            )
        }
        return
    }
    val zoneId by remember { mutableStateOf(ZoneId.systemDefault()) }
    val groups = remember(items, zoneId) { groupMediaByDate(items, zoneId) }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(
            horizontal = VicuTheme.dimensions.screenGutter,
            vertical = VicuTheme.dimensions.screenGutter,
        ),
    ) {
        for (group in groups) {
            item(key = "date-${group.date}") {
                BasicText(
                    text = group.label(LocalDate.now(zoneId)),
                    style = VicuTheme.typography.bodyLg.copy(color = VicuTheme.colors.onSurface),
                    modifier = Modifier.padding(top = VicuTheme.dimensions.spacingUnit),
                )
            }
            items(group.items, key = { it.uri }) { item ->
                MediaRow(item, onPick)
            }
        }
    }
}

/** 媒体行：名称 + 时长（未知时不显示），点击即选中返回。 */
@Composable
private fun MediaRow(item: MediaItem, onPick: (MediaItem) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPick(item) }
            .padding(vertical = VicuTheme.dimensions.spacingUnit),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            text = abbreviateMediaFileName(item.name),
            style = VicuTheme.typography.bodyLg.copy(color = VicuTheme.colors.onSurface),
        )
        if (item.durationMs > 0) {
            BasicText(
                text = formatMediaClock(item.durationMs / 1000),
                style = VicuTheme.typography.bodySm.copy(color = VicuTheme.colors.onSurfaceVariant),
            )
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
