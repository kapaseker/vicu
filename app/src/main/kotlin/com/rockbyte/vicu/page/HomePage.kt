package com.rockbyte.vicu.page

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rockbyte.vicu.R
import com.rockbyte.vicu.repo.MediaItem
import com.rockbyte.vicu.repo.MediaKind
import com.rockbyte.vicu.ui.component.VicuButton
import com.rockbyte.vicu.ui.component.VicuScaffold
import com.rockbyte.vicu.ui.theme.VicuSpacing
import com.rockbyte.vicu.ui.theme.VicuTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel

/** 按系统版本返回访问媒体库所需的运行时权限。 */
internal fun mediaPermissionsForSdk(sdkInt: Int): List<String> = if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
    listOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_AUDIO,
    )
} else {
    listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

private val MediaKind.iconRes: Int
    get() = when (this) {
        MediaKind.IMAGE -> R.drawable.ic_pic
        MediaKind.VIDEO -> R.drawable.ic_video
        MediaKind.AUDIO -> R.drawable.ic_music
    }

/**
 * 首页：MediaStore 聚合的媒体库 grid（图片/视频/音频），
 * 每格左上角类型角标区分多媒体类型；未授权时展示权限提示。
 */
@Composable
fun HomePage() {
    val viewModel = koinViewModel<HomeViewModel>()
    val state by viewModel.uiState.collectAsState()
    HomePageContent(state = state, onRefresh = viewModel::refresh)
}

@Composable
private fun HomePageContent(
    state: MediaLibraryUiState,
    onRefresh: () -> Unit,
) {
    val context = LocalContext.current
    val requiredPermissions = remember { mediaPermissionsForSdk(Build.VERSION.SDK_INT) }
    var hasPermission by remember {
        mutableStateOf(
            requiredPermissions.any {
                context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
            })
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result -> hasPermission = result.values.any { it } }

    LaunchedEffect(hasPermission) {
        if (hasPermission) onRefresh()
    }

    HomePageBody(
        state = state,
        hasPermission = hasPermission,
        onRequestPermission = { permissionLauncher.launch(requiredPermissions.toTypedArray()) },
    )
}

@Composable
private fun HomePageBody(
    state: MediaLibraryUiState,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
) {
    VicuScaffold(title = stringResource(R.string.app_name)) {
        if (hasPermission) {
            MediaGrid(state)
        } else {
            PermissionPrompt(onRequest = onRequestPermission)
        }
    }
}

@Composable
private fun MediaGrid(state: MediaLibraryUiState) {
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
        columns = GridCells.Fixed(3),
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(
            horizontal = VicuSpacing.gutter,
            vertical = VicuSpacing.gutter,
        ),
        horizontalArrangement = Arrangement.spacedBy(VicuSpacing.unit),
        verticalArrangement = Arrangement.spacedBy(VicuSpacing.unit),
    ) {
        items(state.items, key = { it.uri }) { item ->
            MediaTile(item)
        }
    }
}

@Composable
private fun MediaTile(item: MediaItem, modifier: Modifier = Modifier) {
    val tileState = remember { MutableStyleState(null) }
    Box(
        modifier
            .aspectRatio(1f)
            .clip(VicuTheme.shapes.xl)
            .styleable(tileState, VicuTheme.styles.mediaTile)
    ) {
        when (item.kind) {
            MediaKind.IMAGE, MediaKind.VIDEO -> MediaThumbnail(item)
            MediaKind.AUDIO -> KindIcon(item)
        }
        TypeBadge(item.kind, Modifier
            .align(Alignment.TopStart)
            .padding(VicuSpacing.unit))
    }
}

@Composable
private fun BoxScope.MediaThumbnail(item: MediaItem) {
    val context = LocalContext.current
    val thumbnail by produceState<ImageBitmap?>(initialValue = null, item.uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.loadThumbnail(item.uri, Size(256, 256), null).asImageBitmap()
            }.getOrNull()
        }
    }
    val bitmap = thumbnail
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = item.name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    } else {
        KindIcon(item)
    }
}

@Composable
private fun BoxScope.KindIcon(item: MediaItem) {
    Image(
        painter = painterResource(item.kind.iconRes),
        contentDescription = item.name,
        modifier = Modifier
            .align(Alignment.Center)
            .size(32.dp),
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
            modifier = Modifier.size(16.dp),
            colorFilter = ColorFilter.tint(VicuTheme.colors.onSurfaceVariant),
        )
    }
}

@Composable
private fun PermissionPrompt(onRequest: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = VicuSpacing.gutter),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(VicuSpacing.unit * 2),
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
                items = listOf(
                    MediaItem(Uri.parse("content://media/external/images/1"), "photo.jpg", MediaKind.IMAGE, 3),
                    MediaItem(Uri.parse("content://media/external/video/2"), "clip.mp4", MediaKind.VIDEO, 2),
                    MediaItem(Uri.parse("content://media/external/audio/3"), "song.mp3", MediaKind.AUDIO, 1),
                )
            ),
            onRefresh = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HomePageEmptyPreview() {
    VicuTheme {
        HomePageContent(
            state = MediaLibraryUiState(),
            onRefresh = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HomePagePermissionPreview() {
    VicuTheme {
        HomePageBody(
            state = MediaLibraryUiState(),
            hasPermission = false,
            onRequestPermission = {},
        )
    }
}
