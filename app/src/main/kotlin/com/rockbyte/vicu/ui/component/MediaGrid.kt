package com.rockbyte.vicu.ui.component

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.rockbyte.vicu.R
import com.rockbyte.vicu.repo.MediaItem
import com.rockbyte.vicu.repo.MediaKind
import com.rockbyte.vicu.ui.theme.VicuTheme
import java.time.LocalDate
import java.time.ZoneId

@Composable
internal fun MediaGrid(
    items: List<MediaItem>,
    loading: Boolean,
    onMediaClick: (MediaItem) -> Unit,
) {
    if (!loading && items.isEmpty()) {
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
    val groups = remember(items, zoneId) { groupMediaByDate(items, zoneId) }
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
        if (item.kind == MediaKind.AUDIO) {
            BasicText(
                text = abbreviateMediaFileName(item.name),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = VicuTheme.typography.caption.copy(color = VicuTheme.colors.onSurface),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(VicuTheme.dimensions.spacingUnit)
                    .background(VicuTheme.colors.surfaceContainerLowest, VicuTheme.shapes.base)
                    .padding(VicuTheme.dimensions.spacingUnit / 2),
            )
        }
        if (item.kind != MediaKind.IMAGE && item.durationMs > 0) {
            BasicText(
                text = formatMediaClock(item.durationMs / 1000),
                style = VicuTheme.typography.caption.copy(color = VicuTheme.colors.onSurface),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(VicuTheme.dimensions.spacingUnit)
                    .background(VicuTheme.colors.surfaceContainerLowest, VicuTheme.shapes.base)
                    .padding(horizontal = VicuTheme.dimensions.spacingUnit / 2,
                        vertical = VicuTheme.dimensions.spacingUnit / 2),
            )
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
