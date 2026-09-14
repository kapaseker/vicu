package com.rockbyte.vicu.page

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.style.MutableStyleState
import androidx.compose.foundation.style.styleable
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.rockbyte.vicu.R
import com.rockbyte.vicu.repo.MediaItem
import com.rockbyte.vicu.repo.MediaKind
import com.rockbyte.vicu.ui.component.VicuButton
import com.rockbyte.vicu.ui.component.VicuScaffold
import com.rockbyte.vicu.ui.component.iconRes
import com.rockbyte.vicu.ui.theme.VicuTheme
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
        onLoadThumbnail = viewModel::loadThumbnail,
        onMediaClick = onMediaClick,
    )
}

@Composable
private fun HomePageContent(
    state: MediaLibraryUiState,
    onRefresh: () -> Unit,
    onLoadThumbnail: suspend (Uri, Int, Int) -> Bitmap?,
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
        onLoadThumbnail = onLoadThumbnail,
        onMediaClick = onMediaClick,
    )
}

@Composable
private fun HomePageBody(
    state: MediaLibraryUiState,
    onRequestPermission: () -> Unit,
    onLoadThumbnail: suspend (Uri, Int, Int) -> Bitmap?,
    onMediaClick: (MediaItem) -> Unit,
) {
    VicuScaffold(title = stringResource(R.string.app_name)) {
        when (state.hasAccess) {
            true -> MediaGrid(state, onLoadThumbnail, onMediaClick)
            false -> PermissionPrompt(onRequest = onRequestPermission)
            null -> Unit
        }
    }
}

@Composable
private fun MediaGrid(
    state: MediaLibraryUiState,
    onLoadThumbnail: suspend (Uri, Int, Int) -> Bitmap?,
    onMediaClick: (MediaItem) -> Unit,
) {
    if (!state.loading && state.items.isEmpty()) {
        Box(Modifier.fillMaxSize()) {
            BasicText(
                text = stringResource(R.string.media_empty),
                style = VicuTheme.typography.bodySm.copy(color = VicuTheme.colors.onSurfaceVariant),
                modifier = Modifier.align(Alignment.Center),
            )
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = VicuTheme.dimensions.homeMediaGridMinSize),
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(
            horizontal = VicuTheme.dimensions.screenGutter,
            vertical = VicuTheme.dimensions.screenGutter,
        ),
        horizontalArrangement = Arrangement.spacedBy(VicuTheme.dimensions.spacingUnit),
        verticalArrangement = Arrangement.spacedBy(VicuTheme.dimensions.spacingUnit),
    ) {
        items(state.items, key = { it.uri }) { item ->
            MediaTile(item, onLoadThumbnail, onMediaClick)
        }
    }
}

@Composable
private fun MediaTile(
    item: MediaItem,
    onLoadThumbnail: suspend (Uri, Int, Int) -> Bitmap?,
    onMediaClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tileState = remember { MutableStyleState(null) }
    val interactionSource = remember { MutableInteractionSource() }
    val thumbnail by produceState<Bitmap?>(initialValue = null, item.uri) {
        value = when (item.kind) {
            MediaKind.IMAGE, MediaKind.VIDEO ->
                onLoadThumbnail(item.uri, THUMBNAIL_SIZE_PX, THUMBNAIL_SIZE_PX)
            MediaKind.AUDIO -> null
        }
    }
    Box(
        modifier
            .aspectRatio(1f)
            .clip(VicuTheme.shapes.xl)
            .styleable(tileState, VicuTheme.styles.mediaTile)
            .clickable(
                interactionSource = interactionSource,
                onClick = { onMediaClick(item) },
            )
    ) {
        val bitmap = thumbnail
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = item.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            // badge 只在缩略图加载成功时显示，避免与居中的默认类型图标重复
            TypeBadge(item.kind, Modifier
                .align(Alignment.TopStart)
                .padding(VicuTheme.dimensions.spacingUnit))
        } else {
            KindIcon(item)
        }
    }
}

private const val THUMBNAIL_SIZE_PX = 256

@Composable
private fun BoxScope.KindIcon(item: MediaItem) {
    Image(
        painter = painterResource(item.kind.iconRes),
        contentDescription = item.name,
        modifier = Modifier
            .align(Alignment.Center)
            .size(VicuTheme.dimensions.iconLarge),
        colorFilter = ColorFilter.tint(VicuTheme.colors.onSurfaceVariant),
    )
}

@Composable
private fun TypeBadge(kind: MediaKind, modifier: Modifier = Modifier) {
    val badgeState = remember { MutableStyleState(null) }
    Box(
        modifier = modifier.styleable(badgeState, VicuTheme.styles.typeBadge),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(kind.iconRes),
            contentDescription = null,
            modifier = Modifier.size(VicuTheme.dimensions.iconSmall),
            colorFilter = ColorFilter.tint(VicuTheme.colors.onSurface),
        )
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
            VicuButton(onClick = onRequest) {
                BasicText(stringResource(R.string.grant_media_permission))
            }
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
                    MediaItem(Uri.parse("content://media/external/images/1"), "photo.jpg", MediaKind.IMAGE, 3),
                    MediaItem(Uri.parse("content://media/external/video/2"), "clip.mp4", MediaKind.VIDEO, 2),
                    MediaItem(Uri.parse("content://media/external/audio/3"), "song.mp3", MediaKind.AUDIO, 1),
                )
            ),
            onRefresh = {},
            onLoadThumbnail = { _, _, _ -> null },
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
            onLoadThumbnail = { _, _, _ -> null },
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
            onLoadThumbnail = { _, _, _ -> null },
            onMediaClick = {},
        )
    }
}
