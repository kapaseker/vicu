package com.rockbyte.vicu.page

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.style.MutableStyleState
import androidx.compose.foundation.style.styleable
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.rockbyte.vicu.R
import com.rockbyte.vicu.repo.MediaItem
import com.rockbyte.vicu.repo.MediaKind
import com.rockbyte.vicu.ui.component.PrimaryButton
import com.rockbyte.vicu.ui.component.VicuScaffold
import com.rockbyte.vicu.ui.component.iconRes
import com.rockbyte.vicu.ui.theme.VicuTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
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
            true -> MediaGrid(state, onMediaClick)
            false -> PermissionPrompt(onRequest = onRequestPermission)
            null -> Unit
        }
    }
}

@Composable
private fun MediaGrid(
    state: MediaLibraryUiState,
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
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var zoneId by remember { mutableStateOf(ZoneId.systemDefault()) }
    var today by remember { mutableStateOf(LocalDate.now(zoneId)) }
    DisposableEffect(context, lifecycleOwner) {
        fun refreshDate() {
            zoneId = ZoneId.systemDefault()
            today = LocalDate.now(zoneId)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) = refreshDate()
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshDate()
        }
        context.registerReceiver(receiver, filter)
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            context.unregisterReceiver(receiver)
        }
    }
    val groups = remember(state.items, zoneId) { groupMediaByDate(state.items, zoneId) }
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
        for (group in groups) {
            item(
                key = "date-${group.date}",
                span = { GridItemSpan(maxLineSpan) },
            ) {
                BasicText(
                    text = group.label(today),
                    style = VicuTheme.typography.bodyLg.copy(color = VicuTheme.colors.onSurface),
                    modifier = Modifier.padding(top = VicuTheme.dimensions.spacingUnit),
                )
            }
            items(group.items, key = { it.uri }) { item ->
                MediaTile(item, onMediaClick)
            }
        }
    }
}

internal data class MediaDateGroup(val date: LocalDate, val items: List<MediaItem>)

internal fun groupMediaByDate(
    items: List<MediaItem>,
    zoneId: ZoneId,
): List<MediaDateGroup> = items
    .groupBy { Instant.ofEpochSecond(it.dateAdded).atZone(zoneId).toLocalDate() }
    .map { (date, dateItems) -> MediaDateGroup(date, dateItems) }

internal sealed interface MediaDateLabel {
    data object Today : MediaDateLabel
    data object Yesterday : MediaDateLabel
    data class MonthDay(val month: Int, val day: Int) : MediaDateLabel
    data class YearMonthDay(val year: Int, val month: Int, val day: Int) : MediaDateLabel
}

internal fun mediaDateLabel(date: LocalDate, today: LocalDate): MediaDateLabel = when {
    date == today -> MediaDateLabel.Today
    date == today.minusDays(1) -> MediaDateLabel.Yesterday
    date.year == today.year -> MediaDateLabel.MonthDay(date.monthValue, date.dayOfMonth)
    else -> MediaDateLabel.YearMonthDay(date.year, date.monthValue, date.dayOfMonth)
}

@Composable
private fun MediaDateGroup.label(today: LocalDate): String = when (val label = mediaDateLabel(date, today)) {
    MediaDateLabel.Today -> stringResource(R.string.media_date_today)
    MediaDateLabel.Yesterday -> stringResource(R.string.media_date_yesterday)
    is MediaDateLabel.MonthDay -> stringResource(R.string.media_date_month_day, label.month, label.day)
    is MediaDateLabel.YearMonthDay -> stringResource(
        R.string.media_date_year_month_day,
        label.year,
        label.month,
        label.day,
    )
}

@Composable
private fun MediaTile(
    item: MediaItem,
    onMediaClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tileState = remember { MutableStyleState(null) }
    val interactionSource = remember { MutableInteractionSource() }
    var imageState by remember { mutableStateOf<AsyncImagePainter.State?>(null) }
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
        // 图片/视频用 Coil 加载缩略图（视频经 VideoFrameDecoder 解码真实第一帧）
        if (item.kind != MediaKind.AUDIO) {
            AsyncImage(
                model = item.uri,
                contentDescription = item.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onState = { imageState = it },
            )
        }
        if (imageState is AsyncImagePainter.State.Success) {
            // badge 只在缩略图加载成功时显示，避免与居中的默认类型图标重复
            TypeBadge(item.kind, Modifier
                .align(Alignment.TopStart)
                .padding(VicuTheme.dimensions.spacingUnit))
        } else {
            // 音频/加载中/加载失败 → 居中默认类型图标
            KindIcon(item)
        }
    }
}

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
                    MediaItem(Uri.parse("content://media/external/video/2"), "clip.mp4", MediaKind.VIDEO, Instant.parse("2026-09-15T08:00:00Z").epochSecond),
                    MediaItem(Uri.parse("content://media/external/audio/3"), "song.mp3", MediaKind.AUDIO, Instant.parse("2026-09-15T07:00:00Z").epochSecond),
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
